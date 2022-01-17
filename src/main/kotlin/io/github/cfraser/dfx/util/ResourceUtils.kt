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
package io.github.cfraser.dfx.util

import java.io.InputStream
import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [Resource] is a [Serializable] class for a classpath resource that must be initialized on the
 * *remote worker* so that *distributed transform* can be executed.
 *
 * @property path the path of the classpath resource
 * @property data the content of the classpath resource
 */
class Resource(val path: String, val data: ByteArray) : Serializable {

  private companion object {

    const val serialVersionUID = 888L
  }
}

/**
 * Execute the [block] with the [InputStream] of the system resource with [name].
 *
 * @param T the type returned by [block]
 * @param name the name of the system resource
 * @param block the function to execute
 * @return the instance of [T] returned by [block] or `null` if a resource with [name] was not found
 */
suspend fun <T> useSystemResource(name: String, block: suspend (InputStream) -> T): T? {
  return withContext(Dispatchers.IO) {
    ClassLoader.getSystemResourceAsStream(name)?.buffered()?.use { inputStream ->
      block(inputStream)
    }
  }
}
