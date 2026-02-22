import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsCompilerAttribute

plugins {
  kotlin("jvm")
}

val testJsRuntime: Configuration by configurations.creating {
  attributes {
    attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
    attribute(KotlinJsCompilerAttribute.jsCompilerAttribute, KotlinJsCompilerAttribute.ir)
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
  }
}

dependencies {
  testImplementation(projects.kotlinPlugin)
  testImplementation(kotlin("compiler"))
  testImplementation(kotlin("compiler-internal-test-framework"))
  testImplementation(kotlin("test-junit5"))
  testImplementation(libs.assertk)
  testImplementation(libs.kotlin.coroutines.test)

  testJsRuntime(projects.runtime)
  testJsRuntime(kotlin("stdlib"))
  testJsRuntime(kotlin("test-js"))

  testRuntimeOnly(kotlin("reflect"))
  testRuntimeOnly(kotlin("test"))
  testRuntimeOnly(kotlin("script-runtime"))
  testRuntimeOnly(kotlin("annotations-jvm"))
}

tasks.register<JavaExec>("generateTests") {
  inputs
    .dir(layout.projectDirectory.dir("src/test/data"))
    .withPropertyName("testData")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  outputs.dir(layout.projectDirectory.dir("src/test/java")).withPropertyName("generatedTests")

  classpath = sourceSets.test.get().runtimeClasspath
  mainClass.set("ca.derekellis.workers.kotlin.GenerateTestsKt")
  workingDir = rootDir
}

tasks.withType<Test> {
  dependsOn(testJsRuntime)
  inputs
    .dir(layout.projectDirectory.dir("src/test/data"))
    .withPropertyName("testData")
    .withPathSensitivity(PathSensitivity.RELATIVE)

  workingDir = rootDir

  useJUnitPlatform()

  systemProperty("jsTestRuntime.classpath", testJsRuntime.asPath)

  // Properties required to run the internal test framework.
  systemProperty("idea.ignore.disabled.plugins", "true")
  systemProperty("idea.home.path", rootDir)
}
