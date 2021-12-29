plugins {
  `java-library`
  `maven-publish`
  signing
}

dependencies {
  val kotlinxCoroutinesVersion: String by rootProject
  val asmVersion: String by rootProject
  val nettyVersion: String by rootProject

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")
  implementation("org.ow2.asm:asm:$asmVersion")
  implementation("org.ow2.asm:asm-commons:$asmVersion")
  implementation("io.netty:netty-buffer:$nettyVersion")
}
