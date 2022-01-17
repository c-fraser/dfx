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
package io.github.cfraser.dfx.test

import io.github.cfraser.dfx.newWorker
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/** The entry point for the *worker* application. */
fun main() {
  newWorker(8787).run {
    try {
      start()
      awaitShutdown()
    } finally {
      stop()
    }
  }
}

/** Await JVM shutdown. */
private fun awaitShutdown() {
  CountDownLatch(1).run {
    Runtime.getRuntime().addShutdownHook(thread(start = false, block = ::countDown))
    await()
  }
}
