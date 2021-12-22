plugins {
  application
  id("com.google.cloud.tools.jib")
}

val testWorker = "io.github.cfraser.dfx.test.MainKt"

application { mainClass.set(testWorker) }

dependencies {
  val commonsLang3Version: String by rootProject

  implementation(project(":dfx"))
  testImplementation("org.apache.commons:commons-lang3:$commonsLang3Version")
}

jib {
  from { image = "openjdk:11-alpine" }
  to {
    image = "${System.getenv("DOCKER_REPOSITORY")}:dfx-$name-$version"
    auth {
      username = System.getenv("DOCKER_USERNAME")
      password = System.getenv("DOCKER_PASSWORD")
    }
  }
  container {
    ports = listOf("8787")
    mainClass = testWorker
  }
}
