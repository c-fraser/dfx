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
package io.github.cfraser.dfx

import io.github.cfraser.dfx.rsocket.RSocketWorker
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.Flow

/**
 * Initialize a [Worker] that binds to the [port].
 *
 * @param port the port to bind to
 * @return the [Worker]
 */
fun newWorker(port: Int): Worker {
  return RSocketWorker(port)
}

/**
 * Initialize a [Worker.Connection] which executes the [transform] on the *remote worker* at
 * [address].
 *
 * @param address the [InetSocketAddress] of the *remote worker*
 * @param transform the *distributed transform* function
 * @return the [Worker.Connection]
 */
@PublishedApi
internal fun newWorkerConnection(
    address: InetSocketAddress,
    transform: (value: Any) -> Flow<Any>
): Worker.Connection {
  return RSocketWorker.Connection(address, transform)
}
