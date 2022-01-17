import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import com.google.cloud.tools.jib.gradle.BuildDockerTask

plugins {
  application
  kotlin("jvm")
  id("com.google.cloud.tools.jib")
  id("com.bmuschko.docker-remote-api")
}

val testWorker = "io.github.cfraser.dfx.test.WorkerKt"

application { mainClass.set(testWorker) }

dependencies {
  val slf4jVersion: String by rootProject

  implementation(rootProject)
  runtimeOnly("org.slf4j:slf4j-simple:$slf4jVersion")
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

  val copyWorkerContainerLogs by
      creating(DockerLogsContainer::class) {
        targetContainerId(startWorkerContainer.containerId)
        tailAll.set(true)
      }

  create("stopWorkerContainer", DockerStopContainer::class) {
    dependsOn(copyWorkerContainerLogs)
    targetContainerId(createWorkerContainer.containerId)
  }
}
