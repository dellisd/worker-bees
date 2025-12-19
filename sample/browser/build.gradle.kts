plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

kotlin {
  js {
    browser()
    binaries.executable()
  }

  sourceSets {
    val jsMain by getting {
      dependencies {
        api(projects.browserRuntime)
        api(libs.kotlin.coroutines)

        implementation(projects.sample.api)
        implementation(compose.runtime)
        implementation(compose.html.core)
      }
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

val workerDependencies by configurations.dependencyScope("workerDependencies")
val workerConfiguration by configurations.resolvable("worker") {
  extendsFrom(workerDependencies)

  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "web-worker"))
  }
}

dependencies {
 workerDependencies(projects.sample.worker)
}

tasks.named<ProcessResources>("jsProcessResources").configure {
  from(workerConfiguration)
}