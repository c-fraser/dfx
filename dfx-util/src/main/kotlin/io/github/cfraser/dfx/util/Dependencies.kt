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
package io.github.cfraser.dfx.util

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.TypePath
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

/**
 * Tail recursively collect the dependencies for the class corresponding to the [className].
 *
 * @param className the name of the class to collect dependencies for
 * @param collected the class names that have already been collected
 * @param dependencies the dependencies that have been collected
 * @return the direct and transitive dependencies of the class with the [className]
 */
tailrec suspend fun collectDependencies(
    className: String,
    collected: MutableSet<String> = mutableSetOf(),
    dependencies: MutableSet<String> = mutableSetOf()
): Set<String> {
  collected += className
  useSystemResource(className) { inputStream ->
    val classDependencyCollector = ClassDependencyCollector()
    @Suppress("BlockingMethodInNonBlockingContext")
    ClassReader(inputStream).accept(classDependencyCollector, ClassReader.EXPAND_FRAMES)
    classDependencyCollector.dependencies.filterNot { dependency ->
      dependency.startsWithAny("java/", "jdk/", "sun/", "kotlin/")
    }
  }
      ?.apply { dependencies += this }
  return when (val dependency =
      dependencies.firstOrNull { dependency -> dependency !in collected }) {
    null -> dependencies
    else -> collectDependencies(dependency, collected, dependencies)
  }
}

/**
 * [ClassDependencyCollector] is a [ClassRemapper] that uses
 * [ClassDependencyCollector.ClassResourceCollector] to collect the dependencies (references to
 * other classes) of a class file.
 */
private class ClassDependencyCollector : ClassRemapper(NoOpClassVisitor, ClassResourceCollector()) {

  /** Return the class resources collected by the [ClassResourceCollector]. */
  val dependencies: Set<String>
    get() = (remapper as ClassResourceCollector).classResources

  /**
   * [ClassResourceCollector] is a [Remapper] that appends each *mappable internal name* to
   * [classResources] .
   */
  private class ClassResourceCollector : Remapper() {

    val classResources = mutableSetOf<String>()

    override fun map(internalName: String?) =
        internalName?.also { className -> classResources += "$className.class" }
  }

  /**
   * [NoOpClassVisitor] is a [no-op](https://en.wikipedia.org/wiki/NOP_(code)) [ClassVisitor] for
   * [Opcodes.ASM9].
   */
  private object NoOpClassVisitor : ClassVisitor(Opcodes.ASM9) {

    /**
     * [NoOpAnnotationVisitor] is a [no-op](https://en.wikipedia.org/wiki/NOP_(code))
     * [AnnotationVisitor] for the [AnnotationVisitor.api].
     */
    private object NoOpAnnotationVisitor : AnnotationVisitor(api) {
      override fun visitAnnotation(name: String?, descriptor: String?) = apply {}
      override fun visitArray(name: String?) = apply {}
    }

    /**
     * [NoOpFieldVisitor] is a [no-op](https://en.wikipedia.org/wiki/NOP_(code)) [FieldVisitor] for
     * the [FieldVisitor.api].
     */
    private object NoOpFieldVisitor : FieldVisitor(api) {
      override fun visitAnnotation(descriptor: String?, visible: Boolean) = NoOpAnnotationVisitor
      override fun visitTypeAnnotation(
          typeRef: Int,
          typePath: TypePath?,
          descriptor: String?,
          visible: Boolean
      ) = NoOpAnnotationVisitor
    }

    /**
     * [NoOpMethodVisitor] is a [no-op](https://en.wikipedia.org/wiki/NOP_(code)) [MethodVisitor]
     * for the [MethodVisitor.api].
     */
    private object NoOpMethodVisitor : MethodVisitor(api) {
      override fun visitAnnotationDefault() = NoOpAnnotationVisitor
      override fun visitAnnotation(descriptor: String?, visible: Boolean) = NoOpAnnotationVisitor
      override fun visitTypeAnnotation(
          typeRef: Int,
          typePath: TypePath?,
          descriptor: String?,
          visible: Boolean
      ) = NoOpAnnotationVisitor
      override fun visitParameterAnnotation(parameter: Int, descriptor: String?, visible: Boolean) =
          NoOpAnnotationVisitor
      override fun visitInsnAnnotation(
          typeRef: Int,
          typePath: TypePath?,
          descriptor: String?,
          visible: Boolean
      ) = NoOpAnnotationVisitor
      override fun visitTryCatchAnnotation(
          typeRef: Int,
          typePath: TypePath?,
          descriptor: String?,
          visible: Boolean
      ) = NoOpAnnotationVisitor
      override fun visitLocalVariableAnnotation(
          typeRef: Int,
          typePath: TypePath?,
          start: Array<Label?>?,
          end: Array<Label?>?,
          index: IntArray?,
          descriptor: String?,
          visible: Boolean
      ) = NoOpAnnotationVisitor
    }

    override fun visitAnnotation(descriptor: String?, visible: Boolean) = NoOpAnnotationVisitor
    override fun visitTypeAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ) = NoOpAnnotationVisitor
    override fun visitField(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        value: Any?
    ) = NoOpFieldVisitor
    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<String?>?
    ) = NoOpMethodVisitor
  }
}
