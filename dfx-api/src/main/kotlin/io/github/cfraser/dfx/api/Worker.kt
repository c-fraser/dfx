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
package io.github.cfraser.dfx.api

import java.io.Closeable
import kotlinx.coroutines.flow.Flow

/** A [Worker] asynchronously processes *work* received from a *distributed* [Flow]. */
interface Worker {

  /** Start the [Worker]. */
  fun start()

  /** Stop the [Worker]. */
  fun stop()

  /**
   * A [Worker.Connection] represents an active session with a *remote* [Worker].
   *
   * [Worker.Connection] implements [Closeable], therefore [close] **should** be invoked
   * appropriately to free the underlying resources.
   */
  interface Connection : Closeable {

    /**
     * Transform the [value] on the *remote* [Worker].
     *
     * @param value the data to transform
     * @return the [Flow] of transformed data
     */
    suspend fun transform(value: Any): Flow<Any>
  }
}
