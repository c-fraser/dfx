pluginManagement {
  val kotlinVersion: String by settings
  val spotlessVersion: String by settings
  val detektVersion: String by settings
  val nexusPublishVersion: String by settings
  val jreleaserVersion: String by settings
  val versionsVersion: String by settings
  val shadowVersion: String by settings
  val dokkaVersion: String by settings
  val atomicfuVersion: String by settings
  val knitVersion: String by settings
  val jibVersion: String by settings
  val dockerApiVersion: String by settings

  plugins {
    kotlin("jvm") version kotlinVersion
    id("com.diffplug.spotless") version spotlessVersion
    id("io.gitlab.arturbosch.detekt") version detektVersion
    id("io.github.gradle-nexus.publish-plugin") version nexusPublishVersion
    id("org.jreleaser") version jreleaserVersion
    id("com.github.ben-manes.versions") version versionsVersion
    id("com.github.johnrengelman.shadow") version shadowVersion
    id("org.jetbrains.dokka") version dokkaVersion
    id("com.google.cloud.tools.jib") version jibVersion
    id("com.bmuschko.docker-remote-api") version dockerApiVersion
  }

  repositories {
    gradlePluginPortal()
    mavenCentral()
  }

  resolutionStrategy {
    eachPlugin {
      when (requested.id.id) {
        "kotlinx-atomicfu" ->
            useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfuVersion")
        "kotlinx-knit" -> useModule("org.jetbrains.kotlinx:kotlinx-knit:$knitVersion")
      }
    }
  }
}

rootProject.name = "dfx"

include("test-worker")

project(":test-worker").projectDir = file("src/test-worker")
