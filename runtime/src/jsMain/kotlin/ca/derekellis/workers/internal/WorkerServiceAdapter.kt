package ca.derekellis.workers.internal

import ca.derekellis.workers.WorkerService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule

@PublishedApi
internal abstract class WorkerServiceAdapter<T : WorkerService> {
  abstract val name: String
  abstract val serializers: List<KSerializer<*>>

  abstract fun functionHandlers(serializerModule: SerializersModule): List<FunctionHandler<T>>

  abstract fun outboundService(outboundHandler: OutboundHandler): T
}
