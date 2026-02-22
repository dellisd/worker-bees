plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinSerialization)
  id("ca.derekellis.worker")
}

kotlin {
  js {
    browser()
  }

  sourceSets {
    val jsMain by getting {
      dependencies {
        api("ca.derekellis.worker:runtime")
        api(libs.kotlin.coroutines)
      }
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

