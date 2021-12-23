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

import io.github.cfraser.dfx.distribute
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.junit.jupiter.api.Tag

@Tag("e2e")
class E2ETest {

  @Test
  fun testDistributedTransform() {
    val randomStrings = buildList { repeat(10) { this += RandomStringUtils.random(5) } }
    val reversedRandomStrings =
        randomStrings
            .asFlow()
            .distribute(InetSocketAddress(8787)) { s -> flowOf(StringUtils.reverse(s)) }
            .run { runBlocking { toList() } }
    assertEquals(randomStrings, reversedRandomStrings.map { s -> StringUtils.reverse(s) })
  }
}
