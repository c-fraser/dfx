plugins {
  `java-library`
  `maven-publish`
  signing
  id("kotlinx-atomicfu")
  /*id("com.github.johnrengelman.shadow")*/
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
  val asmVersion: String by rootProject
  val rsocketVersion: String by rootProject
  val kotlinLoggingVersion: String by rootProject

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinxCoroutinesVersion")
  implementation("org.ow2.asm:asm:$asmVersion")
  implementation("org.ow2.asm:asm-commons:$asmVersion")
  implementation("io.rsocket:rsocket-core:$rsocketVersion")
  implementation("io.rsocket:rsocket-transport-netty:$rsocketVersion")
  implementation("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")
}

/*tasks {
   val configureShadowRelocation by
       creating(ConfigureShadowRelocation::class) {
         target = shadowJar.get()
         prefix = "dfx"
       }

   shadowJar {
     dependsOn(configureShadowRelocation)

     dependencies {
       exclude(dependency("org.jetbrains.kotlin::.*"))
       exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:.*"))
     }
   }
 }

 publishing {
   publications {
     create<MavenPublication>("shadow") mavenPublication@{
       val shadowJar by tasks.getting(ShadowJar::class)
       artifact(shadowJar)
       pom {
         withXml {
           with(asNode().appendNode("dependencies")) {
             fun appendDependency(group: String?, name: String?, version: String?, scope: String) {
               with(appendNode("dependency")) {
                 appendNode("groupId", group)
                 appendNode("artifactId", name)
                 appendNode("version", version)
                 appendNode("scope", scope)
               }
             }

             configurations.named("shadow").get().allDependencies.onEach { dependency ->
               appendDependency(dependency.group, dependency.name, dependency.version, "runtime")
             }

             val kotlinVersion: String by rootProject
             val kotlinxCoroutinesVersion: String by rootProject

             appendDependency("org.jetbrains.kotlin", "kotlin-stdlib", kotlinVersion, "runtime")
             appendDependency("org.jetbrains.kotlin", "kotlin-reflect", kotlinVersion, "runtime")
             appendDependency(
                 "org.jetbrains.kotlinx",
                 "kotlinx-coroutines-core-jvm",
                 kotlinxCoroutinesVersion,
                 "compile")
           }
         }
       }
     }
   }
 }*/