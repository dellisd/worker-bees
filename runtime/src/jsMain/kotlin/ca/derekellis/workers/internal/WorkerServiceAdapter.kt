package ca.derekellis.workers.internal

import ca.derekellis.workers.WorkerService

internal abstract class WorkerServiceAdapter<T : WorkerService> {
  abstract val functionHandlers: Map<String, FunctionHandler<T>>

  abstract fun outboundService(outboundHandler: OutboundHandler): T
}
