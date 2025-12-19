package ca.derekellis.workers.sample

import ca.derekellis.workers.WorkerServiceBinding
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class BoundTestService(private val workerServiceBinding: WorkerServiceBinding<TestService>) : TestService {
  override suspend fun test(): String = workerServiceBinding.json.decodeFromJsonElement(workerServiceBinding.invoke("test"))

  override suspend fun square(value: Int): Int {
    return workerServiceBinding.json.decodeFromJsonElement(
      workerServiceBinding.invoke("square", workerServiceBinding.json.encodeToJsonElement(value))
    )
  }
}
