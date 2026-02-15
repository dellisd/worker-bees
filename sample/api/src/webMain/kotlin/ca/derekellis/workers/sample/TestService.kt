package ca.derekellis.workers.sample

import ca.derekellis.workers.WorkerService

interface TestService : WorkerService {
  suspend fun test(): String

  suspend fun square(value: Int): Int
}
