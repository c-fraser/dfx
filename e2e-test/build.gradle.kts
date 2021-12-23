import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import com.google.cloud.tools.jib.gradle.BuildDockerTask

plugins {
  application
  id("com.google.cloud.tools.jib")
  id("com.bmuschko.docker-remote-api")
}

val testWorker = "io.github.cfraser.dfx.test.MainKt"

application { mainClass.set(testWorker) }

dependencies {
  val commonsLang3Version: String by rootProject

  implementation(project(":dfx"))
  testImplementation("org.apache.commons:commons-lang3:$commonsLang3Version")
}

jib {
  from { image = "gcr.io/distroless/java:11" }
  container {
    ports = listOf("8787")
    mainClass = testWorker
  }
}

tasks {
  val jibDockerBuild by getting(BuildDockerTask::class)

  val createWorkerContainer by
      creating(DockerCreateContainer::class) {
        dependsOn(jibDockerBuild)
        targetImageId("${project.name}:${project.version}")
        hostConfig.portBindings.set(listOf("8787:8787"))
      }

  val startWorkerContainer by
      creating(DockerStartContainer::class) {
        dependsOn(createWorkerContainer)
        targetContainerId(createWorkerContainer.containerId)
      }

  val stopWorkerContainer by
      creating(DockerStopContainer::class) { targetContainerId(createWorkerContainer.containerId) }

  named<Test>("e2eTest") {
    dependsOn(startWorkerContainer)
    finalizedBy(stopWorkerContainer)
  }
}
