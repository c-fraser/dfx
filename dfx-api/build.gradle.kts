plugins {
  `java-library`
  `maven-publish`
  signing
}

dependencies {
  val kotlinxCoroutinesVersion: String by rootProject

  api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")
}
