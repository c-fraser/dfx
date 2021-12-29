plugins {
  `java-library`
  `maven-publish`
  signing
}

dependencies {
  api(project(":dfx-api"))
  implementation(project(":dfx-rsocket"))
}

tasks {
  val writeDfxClassesFile by creating {
    val compileClasspath = sourceSets["main"].output
    val runtimeClasspath = configurations["runtimeClasspath"]
    val dfxClassesFile =
        sourceSets["main"].output.resourcesDir!!.resolve(
            "${project.name}-${project.version}-classes.txt")
    inputs.files(compileClasspath, runtimeClasspath)
    outputs.file(dfxClassesFile)
    doLast {
      dfxClassesFile.printWriter().use { writer ->
        (compileClasspath + runtimeClasspath)
            .mapNotNull { file ->
              when {
                file.isDirectory -> file to fileTree(file)
                file.extension == "jar" -> file to zipTree(file)
                else -> null
              }
            }
            .forEach { (file, tree) ->
              tree.matching { include("**/*.class") }.forEach { _file ->
                if (file.name in _file.path)
                    writer.println(
                        _file.path.substringAfter(file.name).substringAfter(File.separatorChar))
              }
            }
      }
    }
  }

  build { dependsOn(writeDfxClassesFile) }
}
