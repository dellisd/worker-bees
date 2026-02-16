package ca.derekellis.workers.sample

import ca.derekellis.workers.WorkerHandle

fun main() {
  val handle = WorkerHandle.newHostHandle()
  handle.bind<TestService>("testService", RealTestService())
}
