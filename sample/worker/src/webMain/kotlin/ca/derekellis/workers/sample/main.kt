package ca.derekellis.workers.sample

import ca.derekellis.workers.WorkerRegistry
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

fun main() {
  WorkerRegistry()
    .apply {
      register<TestService>(RealTestService::class) { instance, functionName, args ->
        if (functionName == "test") {
          return@register json.encodeToJsonElement(instance.test())
        }

        if (functionName == "square") {
          return@register json.encodeToJsonElement(
            instance.square(json.decodeFromJsonElement(args[0]))
          )
        }

        throw UnsupportedOperationException()
      }
    }
    .init()
}
