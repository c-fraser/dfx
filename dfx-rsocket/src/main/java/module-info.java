module dfx.rsocket {
  requires kotlin.stdlib;
  requires kotlinx.coroutines.reactor;
  requires rsocket.core;
  requires rsocket.transport.netty;
  requires org.slf4j;
  requires transitive dfx.api;
  requires dfx.util;
  exports io.github.cfraser.dfx.rsocket to dfx.core;
}