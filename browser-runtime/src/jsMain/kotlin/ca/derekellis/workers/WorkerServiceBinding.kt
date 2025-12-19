package ca.derekellis.workers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

interface WorkerServiceBinding<T : WorkerService> : AutoCloseable {
  val json: Json

  suspend fun invoke(functionName: String, vararg args: JsonElement): JsonElement

  override fun close() {
  }
}