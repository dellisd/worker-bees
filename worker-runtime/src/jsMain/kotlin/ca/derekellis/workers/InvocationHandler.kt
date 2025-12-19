package ca.derekellis.workers

import kotlinx.serialization.json.JsonElement

fun interface InvocationHandler<in T : WorkerService> {
  suspend fun handle(instance: T, functionName: String, vararg args: JsonElement): JsonElement
}
