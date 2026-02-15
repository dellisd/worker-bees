package ca.derekellis.workers.sample

import ca.derekellis.workers.WorkerRegistry
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

fun main() {
  WorkerRegistry()
    .apply {
      bind<TestService>("testService", RealTestService()) { instance, functionName, args ->
        if (functionName == "test") {
          return@bind json.encodeToJsonElement(instance.test())
        }

        if (functionName == "square") {
          return@bind json.encodeToJsonElement(instance.square(json.decodeFromJsonElement(args[0])))
        }

        throw UnsupportedOperationException()
      }
    }
    .init()
}
