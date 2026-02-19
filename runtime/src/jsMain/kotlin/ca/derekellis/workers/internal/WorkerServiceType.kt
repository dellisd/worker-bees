package ca.derekellis.workers.internal

import ca.derekellis.workers.WorkerService

internal class WorkerServiceType<T : WorkerService>(
  val name: String,
  val functions: List<FunctionHandler<T>>,
) {
  val functionsById: Map<String, FunctionHandler<T>> = functions.associateBy { it.id }
}
