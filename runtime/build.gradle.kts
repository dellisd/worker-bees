plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinSerialization)
}

kotlin {
  js {
    browser()
  }

  sourceSets {
    val jsMain by getting {
      dependencies {
        api(libs.kotlin.coroutines)
        api(libs.kotlin.serialization.json)
      }
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

