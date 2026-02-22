import ca.derekellis.workers.WorkerService

interface SampleService : WorkerService {
  suspend fun hello(message: String): String
}
