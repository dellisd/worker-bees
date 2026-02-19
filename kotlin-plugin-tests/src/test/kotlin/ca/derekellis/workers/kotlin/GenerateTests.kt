package ca.derekellis.workers.kotlin

import org.jetbrains.kotlin.generators.generateTestGroupSuiteWithJUnit5

fun main() {
  generateTestGroupSuiteWithJUnit5 {
    testGroup(
      testDataRoot = "kotlin-plugin-tests/src/test/data",
      testsRoot = "kotlin-plugin-tests/src/test/java",
    ) {
      testClass<AbstractDiagnosticTest> { model("diagnostic") }
    }
  }
}
