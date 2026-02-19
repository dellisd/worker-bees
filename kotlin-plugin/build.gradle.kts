plugins {
  kotlin("jvm")
}

dependencies {
  compileOnly(kotlin("compiler"))
  compileOnly(kotlin("stdlib"))
}

kotlin {
  compilerOptions {
    freeCompilerArgs.add("-Xcontext-parameters")
  }
}
