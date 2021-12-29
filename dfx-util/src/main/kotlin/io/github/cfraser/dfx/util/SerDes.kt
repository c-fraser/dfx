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

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.Unpooled
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Serialize the object as a [ByteBuf].
 *
 * @return the serialized object
 */
suspend fun Any.serialize(): ByteBuf {
  return withContext(Dispatchers.IO) {
    ByteBufOutputStream(Unpooled.buffer()).use { byteBufOutputStream ->
      @Suppress("BlockingMethodInNonBlockingContext")
      ObjectOutputStream(byteBufOutputStream).use { objectOutputStream ->
        objectOutputStream.writeObject(this@serialize)
      }
      byteBufOutputStream.buffer()
    }
  }
}

/**
 * Deserialize the [ByteBuf] as [Any] using the [classLoader].
 *
 * @param classLoader the [ClassLoader] to use to load classes
 * @return the deserialized object
 */
suspend fun ByteBuf.deserialize(classLoader: ClassLoader? = null): Any {
  return withContext(Dispatchers.IO) {
    ByteBufInputStream(this@deserialize).use { byteBufInputStream ->
      @Suppress("BlockingMethodInNonBlockingContext")
      (classLoader?.let { _classLoader -> byteBufInputStream.asObjectInputStream(_classLoader) }
              ?: ObjectInputStream(byteBufInputStream)).use { objectInputStream ->
        objectInputStream.readObject()
      }
    }
  }
}

/**
 * Initialize and return an [ObjectInputStream], from the [InputStream], that uses the [classLoader]
 * to resolve classes.
 *
 * @param classLoader the [ClassLoader] to use to resolve classes
 * @return the [ObjectInputStream]
 */
private fun InputStream.asObjectInputStream(classLoader: ClassLoader): ObjectInputStream {
  return ClassLoaderObjectInputStream(classLoader, this)
}

/**
 * [ClassLoaderObjectInputStream] is an [ObjectInputStream] that uses the [classLoader] to
 * [resolveClass].
 *
 * @property classLoader the [ClassLoader] to use to resolve classes
 * @param inputStream the [InputStream] that is read from
 */
private class ClassLoaderObjectInputStream(
    private val classLoader: ClassLoader,
    inputStream: InputStream
) : ObjectInputStream(inputStream) {

  /**
   * Resolve the [Class] for the [desc].
   *
   * @param desc the [ObjectStreamClass] to resolve the [Class] for
   * @return the [Class]
   */
  override fun resolveClass(desc: ObjectStreamClass): Class<*> {
    return desc.runCatching { Class.forName(name, false, classLoader) }.getOrElse {
      super.resolveClass(desc)
    }
  }
}
