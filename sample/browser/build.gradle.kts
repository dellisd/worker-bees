plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  id("ca.derekellis.worker-host")
}

kotlin {
  js {
    browser()
    binaries.executable()
  }

  sourceSets {
    val jsMain by getting {
      dependencies {
        api("ca.derekellis.worker:browser-runtime")
        api(libs.kotlin.coroutines)

        implementation(projects.api)
        implementation(compose.runtime)
        implementation(compose.html.core)
      }
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

dependencies {
  add("worker", projects.worker)
}

tasks.named("jsBrowserDevelopmentWebpack").configure {
  println(this::class)
}