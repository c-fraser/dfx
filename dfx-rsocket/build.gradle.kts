plugins {
  `java-library`
  `maven-publish`
  signing
  id("kotlinx-atomicfu")
}

atomicfu {
  val atomicfuVersion: String by rootProject

  dependenciesVersion = atomicfuVersion
  transformJvm = true
  variant = "VH"
  verbose = false
}

dependencies {
  val kotlinxCoroutinesVersion: String by rootProject
  val rsocketVersion: String by rootProject
  val slf4jVersion: String by rootProject

  api(project(":dfx-api"))
  implementation(project(":dfx-util"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinxCoroutinesVersion")
  implementation("io.rsocket:rsocket-core:$rsocketVersion")
  implementation("io.rsocket:rsocket-transport-netty:$rsocketVersion")
  implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
