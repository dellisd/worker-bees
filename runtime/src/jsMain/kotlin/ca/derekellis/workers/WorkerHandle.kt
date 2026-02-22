package ca.derekellis.workers

import ca.derekellis.workers.internal.BrowserWorkerBridge
import ca.derekellis.workers.internal.Endpoint
import ca.derekellis.workers.internal.WorkerServiceAdapter
import ca.derekellis.workers.internal.WorkerWorkerBridge
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.w3c.dom.Worker

class WorkerHandle internal constructor(private val endpoint: Endpoint) : AutoCloseable {
  private val scope = MainScope()

  init {
    scope.launch { endpoint.listenToIncoming() }
  }

  fun <T : WorkerService> bind(name: String, instance: T) {
    error("Unexpected call to WorkerHandle.bind. Is the worker-bee plugin applied?")
  }

  @PublishedApi
  internal fun <T : WorkerService> bind(
    name: String,
    instance: T,
    serviceAdapter: WorkerServiceAdapter<T>,
  ) {
    endpoint.bind(name, instance, serviceAdapter)
  }

  fun <T : WorkerService> take(name: String): T {
    error("Unexpected call to WorkerHandle.take. Is the worker-bee plugin applied?")
  }

  @PublishedApi
  internal fun <T : WorkerService> take(name: String, serviceAdapter: WorkerServiceAdapter<T>): T {
    return endpoint.take(name, serviceAdapter)
  }

  override fun close() {
    scope.cancel()
    endpoint.workerBridge.terminate()
  }

  companion object {
    fun newWorkerHandle(
      worker: Worker,
      serializersModule: SerializersModule = EmptySerializersModule(),
    ): WorkerHandle {
      return WorkerHandle(Endpoint(BrowserWorkerBridge(worker), serializersModule))
    }

    fun newHostHandle(
      serializersModule: SerializersModule = EmptySerializersModule()
    ): WorkerHandle {
      return WorkerHandle(Endpoint(WorkerWorkerBridge(), serializersModule))
    }
  }
}
