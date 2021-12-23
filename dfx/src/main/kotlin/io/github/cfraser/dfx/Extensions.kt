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

import java.io.Serializable
import java.net.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull

/**
 * Execute the [transform] on the [remoteWorker].
 *
 * @param In the input type
 * @param Out the output type
 * @param remoteWorker the [InetSocketAddress] of the *remote* [Worker].
 * @param transform the *distributed* transform function
 * @return the [Flow] containing the results of the *distributed* [transform]
 */
inline fun <reified In, reified Out> Flow<In>.distribute(
    remoteWorker: InetSocketAddress,
    noinline transform: (value: In) -> Flow<Out>
): Flow<Out> where In : Any, In : Serializable, Out : Any, Out : Serializable {
  val connection =
      newWorkerConnection(
          remoteWorker, @Suppress("UNCHECKED_CAST") (transform as (Any) -> Flow<Any>))
  return buffer()
      .flatMapConcat { value -> connection.transform(value) }
      .flowOn(Dispatchers.IO)
      .mapNotNull { transformed -> transformed as? Out }
}
