import ca.derekellis.workers.WorkerService

interface SampleService : WorkerService {
  fun <!WORKER_SERVICE_METHOD_MUST_BE_SUSPENDING!>foo<!>()
}
