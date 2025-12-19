plugins {
  alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
  js {
    browser()
  }

  sourceSets {
    val jsMain by getting {
      dependencies {
        api(projects.runtime)
        api(libs.kotlin.coroutines)
      }
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

