/*
Copyright 2021 c-fraser

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package io.github.cfraser.dfx.rsocket

import io.github.cfraser.dfx.Worker
import io.github.cfraser.dfx.util.Resource
import io.github.cfraser.dfx.util.collectDependencies
import io.github.cfraser.dfx.util.deserialize
import io.github.cfraser.dfx.util.serialize
import io.github.cfraser.dfx.util.useSystemResource
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.rsocket.Closeable
import io.rsocket.ConnectionSetupPayload
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.SocketAcceptor
import io.rsocket.core.RSocketClient
import io.rsocket.core.RSocketConnector
import io.rsocket.core.RSocketServer
import io.rsocket.metadata.AuthMetadataCodec
import io.rsocket.metadata.RoutingMetadata
import io.rsocket.metadata.TaggingMetadataCodec
import io.rsocket.metadata.WellKnownAuthType
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.ByteBufPayload
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeBytes
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono

/**
 * [RSocketWorker] is a [Worker] implementation that uses [RSocket](https://rsocket.io/) as the
 * transport mechanism for dispatching *distributed* transforms.
 *
 * @property transportInitializer is a function that initializes a [ServerTransport]
 */
class RSocketWorker
internal constructor(private val transportInitializer: () -> ServerTransport<out Closeable>) :
    Worker {

  init {
    // Ignore unnecessary client disconnection exceptions
    Hooks.onErrorDropped { ex ->
      when {
        ex is CancellationException || ex.cause is CancellationException ->
            LOGGER.trace("Cancellation occurred", ex)
        else -> LOGGER.error(ex.message, ex)
      }
    }
  }

  /**
   * Construct a [RSocketWorker] with the default [transportInitializer] which creates a
   * [TcpServerTransport] that binds to a local address on the given port.
   */
  constructor(port: Int) : this({ TcpServerTransport.create(port) })

  /** Store the [Job] containing the running [RSocketServer]. */
  @Volatile private var rSocketServer: Job? = null

  @Synchronized
  override fun start() {
    if (rSocketServer != null) return
    rSocketServer =
        GlobalScope.launch(Dispatchers.IO) {
          // Socket connection acceptor that defines server semantics
          val acceptor = SocketAcceptor { setupPayload, _ ->
            mono {
              LOGGER.debug("Authenticating and deserializing setup payload")
              val setupPayloadData = setupPayload.apply { authenticate() }.sliceData().deserialize()
              val uuid =
                  checkNotNull(setupPayloadData as? UUID) {
                    "Failed to deserialize connection identifier data"
                  }
              LOGGER.debug("Authenticating connection {}", uuid)
              // RSocket handling the incoming requests
              RequestHandler(uuid)
            }
          }
          // Server transport used by RSocketServer
          val serverTransport = transportInitializer()
          // Create the RSocketServer and bind to the server transport
          val disposable = RSocketServer.create(acceptor).bindNow(serverTransport)
          try {
            LOGGER.debug("{} started", RSocketWorker::class.simpleName)
            awaitCancellation()
          } finally {
            if (!disposable.isDisposed) disposable.runCatching { dispose() }
          }
        }
  }

  @Synchronized
  override fun stop() {
    (rSocketServer ?: return).runCatching { runBlocking(Dispatchers.IO) { cancelAndJoin() } }
    LOGGER.debug("{} stopped", RSocketWorker::class.simpleName)
  }

  /**
   * [RSocketWorker.RequestHandler] is a [RSocket] implementation that handles incoming requests to
   * the [RSocketServer] running within [RSocketWorker].
   *
   * @property resourcePath the [Path] where *resources* are persisted to
   * @property threads the number of threads the [dispatcher] may dispatch coroutines to
   */
  private class RequestHandler(private val resourcePath: Path, val threads: Int = 1) : RSocket {

    /**
     * Construct a [RSocketWorker.RequestHandler] which uses the unique temporary directory created
     * from the [uuid] as the [resourcePath].
     */
    constructor(uuid: UUID) : this(Files.createTempDirectory("dfx-$uuid"))

    /**
     * Lazily initialize a [URLClassLoader] that loads resources from the [resourcePath] and
     * [ClassLoader.getSystemClassLoader].
     */
    private val classLoader by lazy {
      URLClassLoader(arrayOf(resourcePath.toUri().toURL()), ClassLoader.getSystemClassLoader())
    }

    /**
     * Lazily initialize a [ExecutorCoroutineDispatcher] with the number of *daemon* [threads] and
     * the context [classLoader].
     */
    private val dispatcher by lazy {
      val scheduledExecutorService =
          Executors.newScheduledThreadPool(threads) { runnable ->
            thread(
                start = false,
                isDaemon = true,
                contextClassLoader = classLoader,
                block = runnable::run)
          }
      scheduledExecutorService.asCoroutineDispatcher()
    }

    /** Store an atomic reference to the *distributed* transform. */
    private val aTransform = atomic<((Any) -> Flow<Any>)?>(null)

    override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> {
      return flux(dispatcher) {
        payloads.asFlow().collect { payload ->
          when (val route = payload.route()) {
            INITIALIZE_RESOURCE_ROUTE -> {
              val payloadData = payload.sliceData().deserialize()
              val resource =
                  checkNotNull(payloadData as? Resource) { "Failed to deserialize resource data" }
              LOGGER.debug("Writing {} to {}", resource.path, resourcePath)
              resource
                  .runCatching {
                    withContext(Dispatchers.IO) {
                      resourcePath
                          .resolve(path)
                          .apply { parent.createDirectories() }
                          .createFile()
                          .writeBytes(data)
                    }
                  }
                  .onFailure { throwable ->
                    LOGGER.warn("Failed to write {} to {}", resource.path, resourcePath, throwable)
                  }
            }
            INITIALIZE_TRANSFORM_ROUTE -> {
              val payloadData = payload.sliceData().deserialize(classLoader)
              val transform =
                  checkNotNull(@Suppress("UNCHECKED_CAST") (payloadData as? (Any) -> Flow<Any>)) {
                    "Failed to deserialize transform data"
                  }
              check(aTransform.compareAndSet(expect = null, update = transform)) {
                "Transform initialization has already occurred"
              }
              LOGGER.debug("Distributed transform using {} initialized", resourcePath)
            }
            else -> error("Received unexpected route $route")
          }
        }
      }
    }

    override fun requestStream(payload: Payload): Flux<Payload> {
      val transform = checkNotNull(aTransform.value) { "Transform initialization has not occurred" }
      return flux(dispatcher) {
        check(payload.route() == TRANSFORM_VALUE_ROUTE) { "Received unexpected route" }
        val value = payload.sliceData().deserialize(classLoader)
        LOGGER.debug("Transforming value {}", value)
        transform(value)
            .map { transformed -> transformed.serialize() }
            .map { serialized -> newPayload(serialized) }
            .collect { payload -> send(payload) }
        LOGGER.debug("Transformed value {}", value)
      }
    }

    override fun onClose(): Mono<Void> {
      return mono(Dispatchers.IO) {
        dispatcher.close()
        classLoader.close()
        resourcePath.toFile().deleteRecursively()
      }
          .flatMap {
            LOGGER.debug("Closed connection and deleted resources {}", resourcePath)
            Mono.empty()
          }
    }
  }

  /**
   * [RSocketWorker.Connection] is a [Worker.Connection] implementation for executing *distributed*
   * transforms on a *remote* [RSocketWorker].
   *
   * @param transportInitializer is a function that initializes a [ClientTransport]
   * @param transform the *distributed* transform function
   */
  class Connection
  internal constructor(transportInitializer: () -> ClientTransport, transform: (Any) -> Flow<Any>) :
      Worker.Connection {

    /**
     * Construct a [RSocketWorker.Connection] with the default [transportInitializer] which creates
     * a [TcpClientTransport] connecting to the given [InetSocketAddress].
     */
    constructor(
        address: InetSocketAddress,
        transform: (Any) -> Flow<Any>
    ) : this({ TcpClientTransport.create(address) }, transform)

    /** The [RSocketClient] to use to interact with the remote [RSocketWorker]. */
    private val rSocketClient by lazy {
      val initialization =
          flowOf("${transform::class.java.name.replace('.', '/')}.class")
              .flatMapConcat { transformClass ->
                LOGGER.debug("Collecting dependencies for distributed transform {}", transformClass)
                collectDependencies(transformClass)
                    .also { dependencies ->
                      LOGGER.debug(
                          "Collected dependencies for {}: \t\n{}",
                          transformClass,
                          dependencies.joinToString("\t\n"))
                    }
                    .asFlow()
              }
              .mapNotNull { dependency ->
                useSystemResource(dependency) { inputStream ->
                  ByteArrayOutputStream()
                      .use { outputStream ->
                        inputStream.transferTo(outputStream)
                        outputStream.toByteArray()
                      }
                      .takeUnless { bytes -> bytes.isEmpty() }
                      ?.let { data -> Resource(dependency, data) }
                      ?.run { newPayload(serialize(), INITIALIZE_RESOURCE_ROUTE) }
                }
              }
              .flowOn(Dispatchers.IO)
              .let { payloads ->
                flow {
                  emitAll(payloads)
                  emit(newPayload(transform.serialize(), INITIALIZE_TRANSFORM_ROUTE))
                }
              }
      runBlocking(Dispatchers.IO) {
        val setupPayload = mono { newSetupPayload(UUID.randomUUID()) }
        // Connector specifying rSocket connection semantics
        val connector = RSocketConnector.create().setupPayload(setupPayload)
        // Client transport used by RSocketClient
        val clientTransport = transportInitializer()
        RSocketClient.from(connector.connect(clientTransport)).apply {
          requestChannel(initialization.asPublisher()).collect {}
          LOGGER.debug("Initialized client connection to remote worker")
        }
      }
    }

    override suspend fun transform(value: Any): Flow<Any> {
      val serialized = value.serialize()
      val payload = mono { newPayload(serialized, TRANSFORM_VALUE_ROUTE) }
      return rSocketClient
          .requestStream(payload)
          .asFlow()
          .map { _payload -> _payload.sliceData() }
          .flowOn(Dispatchers.Default)
          .map { data -> data.deserialize() }
          .flowOn(Dispatchers.IO)
    }

    override fun close() {
      if (!rSocketClient.isDisposed) rSocketClient.runCatching { dispose() }
    }
  }

  private companion object {

    val LOGGER = LoggerFactory.getLogger(RSocketWorker::class.java)!!

    /** The authorized bearer token used to authenticate connections. */
    val TOKEN = "dfx".toCharArray()

    /** The route to initialize a resource for a *remote* transformation. */
    const val INITIALIZE_RESOURCE_ROUTE = "initialize-resource-context"

    /** The route to initialize a *remote* transformation. */
    const val INITIALIZE_TRANSFORM_ROUTE = "initialize-transform"

    /** The route to transform a value via a *remote* transformation. */
    const val TRANSFORM_VALUE_ROUTE = "transform-value"

    /**
     * Authenticate the [ConnectionSetupPayload].
     *
     * Specifically ensure the [ConnectionSetupPayload.getMetadata] contains the
     * [WellKnownAuthType.BEARER] auth metadata matching the [TOKEN].
     *
     * @throws [IllegalStateException] if the [ConnectionSetupPayload] is unauthenticated
     */
    suspend fun ConnectionSetupPayload.authenticate() {
      withContext(Dispatchers.Default) {
        val byteBuf = sliceMetadata()
        check(AuthMetadataCodec.isWellKnownAuthType(byteBuf)) { "Unrecognized metadata" }
        val authType = AuthMetadataCodec.readWellKnownAuthType(byteBuf)
        check(authType == WellKnownAuthType.BEARER) { "Unexpected auth type $authType" }
        val token = AuthMetadataCodec.readBearerTokenAsCharArray(byteBuf)
        check(token.contentEquals(TOKEN)) { "Received invalid token ${String(token)}" }
      }
    }

    /**
     * Extract the *route* from the [RoutingMetadata] in the [Payload].
     *
     * @return the *route*
     */
    suspend fun Payload.route(): String {
      return withContext(Dispatchers.Default) {
        val routingMetadata = RoutingMetadata(Unpooled.wrappedBuffer(metadata))
        routingMetadata.joinToString(separator = "")
      }
    }

    /**
     * Initialize a [Payload], for the setup connection, that contains the [TOKEN] in the auth
     * metadata.
     *
     * @param uuid the [UUID] representing the connection identifier
     * @return the [Payload]
     */
    suspend fun newSetupPayload(uuid: UUID): Payload {
      return withContext(Dispatchers.Default) {
        val authMetadata = AuthMetadataCodec.encodeBearerMetadata(ByteBufAllocator.DEFAULT, TOKEN)
        ByteBufPayload.create(uuid.serialize(), authMetadata)
      }
    }

    /**
     * Initialize a [Payload] with the [byteBuf] data.
     *
     * @param byteBuf the payload data
     * @param route the *route* to include in the [RoutingMetadata]
     * @return the [Payload]
     */
    suspend fun newPayload(byteBuf: ByteBuf, route: String? = null): Payload {
      return withContext(Dispatchers.Default) {
        ByteBufPayload.create(byteBuf, route?.let { _route -> newRoutingMetadata(_route).content })
      }
    }

    /**
     * Initialize [RoutingMetadata] with the [route].
     *
     * @param route the *route*
     * @return the [RoutingMetadata]
     */
    suspend fun newRoutingMetadata(route: String): RoutingMetadata {
      return withContext(Dispatchers.Default) {
        TaggingMetadataCodec.createRoutingMetadata(ByteBufAllocator.DEFAULT, route.chunked(255))
      }
    }
  }
}
