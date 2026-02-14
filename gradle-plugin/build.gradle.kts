plugins {
  id("java-gradle-plugin")
  kotlin("jvm")
}

gradlePlugin {
  plugins {
    create("worker-bee") {
      id = "ca.derekellis.worker"
      implementationClass = "ca.derekellis.workers.gradle.WorkerBeePlugin"
    }
    create("worker-host") {
      id = "ca.derekellis.worker-host"
      implementationClass = "ca.derekellis.workers.gradle.WorkerHostPlugin"
    }
  }
}

dependencies {
  implementation(kotlin("gradle-plugin-api"))
  implementation(kotlin("gradle-plugin"))
}
