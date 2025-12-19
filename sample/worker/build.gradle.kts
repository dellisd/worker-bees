import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinPackageJsonTask
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
  alias(libs.plugins.kotlinMultiplatform)
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
        api(projects.workerRuntime)
        api(libs.kotlin.coroutines)

        implementation(projects.sample.api)
      }
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

configurations {
  consumable("worker") {
    attributes {
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "web-worker"))
    }
  }
}

artifacts {
  add("worker", tasks.named<KotlinWebpack>("jsBrowserDevelopmentWebpack").flatMap { it.mainOutputFile })
}
