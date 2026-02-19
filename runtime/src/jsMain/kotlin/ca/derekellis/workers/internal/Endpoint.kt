package ca.derekellis.workers.internal

import ca.derekellis.workers.WorkerService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

internal class Endpoint(
  val workerBridge: WorkerBridge,
  val userSerializersModule: SerializersModule,
) {
  private var messageCounter: Int = 0

  val json = Json {
    // For backwards-compatibility, allow new fields to be introduced.
    ignoreUnknownKeys = true

    // Because host and JS may disagree on default values, it's best to encode them.
    encodeDefaults = true

    // Support map keys whose values are arrays or objects.
    allowStructuredMapKeys = true

    classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS

    serializersModule = SerializersModule { include(userSerializersModule) }
  }

  val workerMessageCodec = WorkerMessage.Codec(json)
  private val boundServices = mutableMapOf<String, BoundInstance<*>>()
  private val serviceTypeCache = mutableMapOf<String, WorkerServiceType<*>>()

  internal fun <T : WorkerService> bind(
    name: String,
    instance: T,
    serviceAdapter: WorkerServiceAdapter<T>,
  ) {
    val serviceType = serviceType(serviceAdapter)
    boundServices[name] = BoundInstance(name, instance, serviceAdapter, serviceType)
  }

  internal fun <T : WorkerService> take(name: String, serviceAdapter: WorkerServiceAdapter<T>): T {
    val serviceType = serviceType(serviceAdapter)
    return serviceAdapter.outboundService(OutboundHandler(name, serviceType, this))
  }

  fun newCallId(): Int = messageCounter++

  suspend fun listenToIncoming() {
    workerBridge.incomingMessages(workerMessageCodec).collect { message ->
      when (message) {
        is WorkerMessage.FunctionCall -> {
          workerBridge.postMessage(routeFunctionCall(message), workerMessageCodec)
        }
        is WorkerMessage.FunctionResult -> {
          /* Handled at the outbound service */
        }
        is WorkerMessage.BoundInstance -> TODO()
        is WorkerMessage.Take -> TODO()
      }
    }
  }

  fun newFunctionCall(
    serviceName: String,
    functionId: String,
    encodedArgs: String,
  ): WorkerMessage.FunctionCall {
    return WorkerMessage.FunctionCall(messageCounter++, serviceName, functionId, encodedArgs)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : WorkerService> serviceType(
    adapter: WorkerServiceAdapter<T>
  ): WorkerServiceType<T> {
    return serviceTypeCache.getOrPut(adapter.name) {
      WorkerServiceType(adapter.name, adapter.functionHandlers(json.serializersModule))
    } as WorkerServiceType<T>
  }

  @Suppress("UNCHECKED_CAST")
  suspend fun routeFunctionCall(call: WorkerMessage.FunctionCall): WorkerMessage.FunctionResult {
    val binding =
      boundServices[call.serviceName] as? BoundInstance<WorkerService>
        ?: throw IllegalArgumentException("Binding not found")
    val handler =
      binding.serviceType.functionsById[call.functionId]
        ?: throw IllegalArgumentException("Function handler not found for ${call.functionId}")

    val args = json.decodeFromString(handler.argsListSerializer, call.encodedArgs)
    val result = handler.call(binding.instance, args)

    val encodedResult = json.encodeToString(handler.resultSerializer as KSerializer<Any?>, result)

    return WorkerMessage.FunctionResult(call.callId, encodedResult)
  }

  private class BoundInstance<T : WorkerService>(
    val name: String,
    val instance: T,
    val serviceAdapter: WorkerServiceAdapter<T>,
    val serviceType: WorkerServiceType<T>,
  )
}
