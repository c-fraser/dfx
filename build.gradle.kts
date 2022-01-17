import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import java.net.URL
import java.util.jar.Attributes
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jreleaser.model.Active

if (JavaVersion.current() < JavaVersion.VERSION_11)
    throw GradleException("Java 11+ is required for this project")

plugins {
  kotlin("jvm")
  `java-library`
  `maven-publish`
  signing
  id("kotlinx-atomicfu")
  id("org.jetbrains.dokka")
  id("com.diffplug.spotless")
  id("io.gitlab.arturbosch.detekt")
  id("io.github.gradle-nexus.publish-plugin")
  id("org.jreleaser")
  id("com.github.ben-manes.versions")
}

allprojects {
  group = "io.github.c-fraser"
  version = "0.0.0"

  repositories { mavenCentral() }
}

dependencies {
  val kotlinxCoroutinesVersion: String by rootProject
  val asmVersion: String by rootProject
  val rsocketVersion: String by rootProject
  val slf4jVersion: String by rootProject

  api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")
  implementation(kotlin("reflect"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinxCoroutinesVersion")
  implementation("org.ow2.asm:asm:$asmVersion")
  implementation("org.ow2.asm:asm-commons:$asmVersion")
  implementation("io.rsocket:rsocket-core:$rsocketVersion")
  implementation("io.rsocket:rsocket-transport-netty:$rsocketVersion")
  implementation("org.slf4j:slf4j-api:$slf4jVersion")

  val junitVersion: String by rootProject
  val commonsLang3Version: String by rootProject

  testImplementation(kotlin("test"))
  testImplementation(kotlin("test-junit5"))
  testImplementation(platform("org.junit:junit-bom:$junitVersion"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.apache.commons:commons-lang3:$commonsLang3Version")
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

atomicfu {
  val atomicfuVersion: String by rootProject

  dependenciesVersion = atomicfuVersion
  transformJvm = true
  variant = "VH"
  verbose = false
}

tasks {
  withType<Jar>().configureEach {
    manifest {
      attributes(
          "${Attributes.Name.IMPLEMENTATION_TITLE}" to project.name,
          "${Attributes.Name.IMPLEMENTATION_VERSION}" to project.version,
          "Automatic-Module-Name" to "io.github.cfraser.${project.name}")
    }
  }

  withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = "${JavaVersion.VERSION_11}"
      freeCompilerArgs =
          listOf(
              "-Xjsr305=strict",
              "-Xopt-in=kotlinx.coroutines.DelicateCoroutinesApi",
              "-Xopt-in=kotlinx.coroutines.FlowPreview")
    }
  }

  withType<Test> {
    useJUnitPlatform()

    dependsOn(":test-worker:startWorkerContainer")
    finalizedBy(":test-worker:stopWorkerContainer")
  }

  withType<DokkaTask>().configureEach {
    dokkaSourceSets {
      named("main") {
        moduleName.set(project.name)
        runCatching { project.file("MODULE.md").takeIf { it.exists() }!! }.onSuccess {
            moduleDocumentation ->
          includes.from(moduleDocumentation)
        }
        platform.set(Platform.jvm)
        jdkVersion.set(JavaVersion.VERSION_11.ordinal)
        sourceLink {
          localDirectory.set(project.file("src/main/kotlin"))
          remoteUrl.set(URL("https://github.com/c-fraser/dfx/tree/main/src/main/kotlin"))
          remoteLineSuffix.set("#L")
        }
      }
    }
  }

  withType<Detekt> {
    jvmTarget = "${JavaVersion.VERSION_11}"
    buildUponDefaultConfig = true
    config.setFrom(rootDir.resolve("detekt.yml"))
  }
}

configure<SpotlessExtension> {
  val ktfmtVersion: String by rootProject

  kotlin {
    ktfmt(ktfmtVersion)
    licenseHeader(
        """
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
        """.trimIndent())
    target(fileTree(rootProject.rootDir) { include("src/**/*.kt") })
  }

  kotlinGradle { ktfmt(ktfmtVersion) }
}

publishing {
  val dokkaJavadocJar by
      tasks.creating(Jar::class) {
        val dokkaJavadoc by tasks.getting(AbstractDokkaTask::class)
        dependsOn(dokkaJavadoc)
        archiveClassifier.set("javadoc")
        from(dokkaJavadoc.outputDirectory.get())
      }

  val sourcesJar by
      tasks.creating(Jar::class) {
        val sourceSets: SourceSetContainer by project
        dependsOn(tasks["classes"])
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
      }

  publications {
    create<MavenPublication>("maven") {
      from(project.components["java"])
      artifact(dokkaJavadocJar)
      artifact(sourcesJar)
      pom {
        name.set(project.name)
        description.set("${project.name}-${project.version}")
        url.set("https://github.com/c-fraser/dfx")
        inceptionYear.set("2021")

        issueManagement {
          system.set("GitHub")
          url.set("https://github.com/c-fraser/dfx/issues")
        }

        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }

        developers {
          developer {
            id.set("c-fraser")
            name.set("Chris Fraser")
          }
        }

        scm {
          url.set("https://github.com/c-fraser/dfx")
          connection.set("scm:git:git://github.com/c-fraser/dfx.git")
          developerConnection.set("scm:git:ssh://git@github.com/c-fraser/dfx.git")
        }
      }
    }
  }

  signing {
    publications.withType<MavenPublication>().all mavenPublication@{
      useInMemoryPgpKeys(System.getenv("GPG_SIGNING_KEY"), System.getenv("GPG_PASSWORD"))
      sign(this@mavenPublication)
    }
  }
}

nexusPublishing {
  repositories {
    sonatype {
      nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
      snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
      username.set(System.getenv("SONATYPE_USERNAME"))
      password.set(System.getenv("SONATYPE_PASSWORD"))
    }
  }
}

jreleaser {
  project {
    website.set("https://github.com/c-fraser/dfx")
    authors.set(listOf("c-fraser"))
    license.set("Apache-2.0")
    extraProperties.put("inceptionYear", "2021")
  }

  release {
    github {
      owner.set("c-fraser")
      overwrite.set(true)
      token.set(System.getenv("GITHUB_TOKEN").orEmpty())
      changelog {
        formatted.set(Active.ALWAYS)
        format.set("- {{commitShortHash}} {{commitTitle}}")
        contributors.enabled.set(false)
        for (status in listOf("added", "changed", "fixed", "removed")) {
          labeler {
            label.set(status)
            title.set(status)
          }
          category {
            title.set(status.capitalize())
            labels.set(listOf(status))
          }
        }
      }
    }
  }
}
