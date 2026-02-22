import ca.derekellis.workers.WorkerHandle
import ca.derekellis.workers.WorkerService

interface SampleService : WorkerService {
  suspend fun hello(message: String): String
}

fun main() {
  val handle = WorkerHandle.newHostHandle()
  handle.take<SampleService>("sampleService")
}
