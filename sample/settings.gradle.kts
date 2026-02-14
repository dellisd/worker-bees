rootProject.name = "worker-bees"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
  }

  versionCatalogs {
    create("libs").from(files("../gradle/libs.versions.toml"))
  }
}

includeBuild("..") {
  dependencySubstitution {
    substitute(module("ca.derekellis.worker:gradle-plugin")).using(project(":gradle-plugin"))
    substitute(module("ca.derekellis.worker:runtime")).using(project(":runtime"))
    substitute(module("ca.derekellis.worker:browser-runtime")).using(project(":browser-runtime"))
    substitute(module("ca.derekellis.worker:worker-runtime")).using(project(":worker-runtime"))
  }
}

include(":api")
include(":browser")
include(":worker")