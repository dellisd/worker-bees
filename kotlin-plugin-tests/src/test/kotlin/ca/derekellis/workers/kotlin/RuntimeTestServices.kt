package ca.derekellis.workers.kotlin

import java.io.File
import java.io.File.pathSeparator
import java.io.File.separator
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices

private val jsTestRuntimeClasspath =
  System.getProperty("jsTestRuntime.classpath")?.split(File.pathSeparator)?.map(::File)
    ?: error("Unable to get a valid classpath from 'jsTestRuntime.classpath' property")

class WorkerBeeRuntimeClasspathProvider(testServices: TestServices) :
  RuntimeClasspathProvider(testServices) {
  override fun runtimeClassPaths(module: TestModule): List<File> {
    return jsTestRuntimeClasspath
  }
}

object ClasspathBasedStandardLibrariesPathProvider : KotlinStandardLibrariesPathProvider {
  private val SEP = "\\$separator"

  private val GRADLE_DEPENDENCY =
    (".*?" +
        SEP +
        "(?<name>[^$SEP]*)" +
        SEP +
        "(?<version>[^$SEP]*)" +
        SEP +
        "[^$SEP]*" +
        SEP +
        "\\1-\\2\\.jar")
      .toRegex()

  private val jars =
    System.getProperty("java.class.path")
      .split("\\$pathSeparator".toRegex())
      .dropLastWhile(String::isEmpty)
      .map(::File)
      .associateBy {
        GRADLE_DEPENDENCY.matchEntire(it.path)?.let { it.groups["name"]!!.value } ?: it.name
      }

  private val files = jsTestRuntimeClasspath

  private fun getFile(name: String): File {
    return jars[name]
      ?: error("Jar $name not found in classpath:\n${jars.entries.joinToString("\n")}")
  }

  private fun getFileForJs(name: String): File {
    return files.find { it.name.contains(name) }
      ?: error("Klib $name not found in classpath:\n${jars.entries.joinToString("\n")}")
  }

  override fun runtimeJarForTests(): File = getFile("kotlin-stdlib")

  override fun runtimeJarForTestsWithJdk8(): File = getFile("kotlin-stdlib-jdk8")

  override fun minimalRuntimeJarForTests(): File = getFile("kotlin-stdlib")

  override fun reflectJarForTests(): File = getFile("kotlin-reflect")

  override fun kotlinTestJarForTests(): File = getFile("kotlin-test")

  override fun scriptRuntimeJarForTests(): File = getFile("kotlin-script-runtime")

  override fun jvmAnnotationsForTests(): File = getFile("kotlin-annotations-jvm")

  override fun getAnnotationsJar(): File = getFile("kotlin-annotations-jvm")

  override fun fullJsStdlib(): File = getFileForJs("kotlin-stdlib-js")

  override fun defaultJsStdlib(): File = getFileForJs("kotlin-stdlib-js")

  override fun kotlinTestJsKLib(): File = getFileForJs("kotlin-test-js")

  override fun scriptingPluginFilesForTests(): Collection<File> {
    TODO("KT-67573")
  }

  override fun commonStdlibForTests(): File = getFile("kotlin-common-stdlib")

  override fun webStdlibForTests(): File = getFile("kotlin-stdlib-web")
}
