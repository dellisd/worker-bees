import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinPackageJsonTask
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  id("ca.derekellis.worker")
}

kotlin {
  js {
    browser {
      webpackTask {
        mainOutputFileName.set("worker.js")
      }
    }
    binaries.executable()
  }

  sourceSets {
    val jsMain by getting {
      dependencies {
        api("ca.derekellis.worker:worker-runtime")
        api("ca.derekellis.worker:runtime")
        api(libs.kotlin.coroutines)

        implementation(projects.api)
      }
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

