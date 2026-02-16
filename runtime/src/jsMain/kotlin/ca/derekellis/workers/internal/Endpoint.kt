package ca.derekellis.workers.internal

import ca.derekellis.workers.WorkerService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json

internal class Endpoint(val workerBridge: WorkerBridge) {
  private var messageCounter: Int = 0

  val json = Json {
    encodeDefaults = false

    classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
  }

  val workerMessageCodec = WorkerMessage.Codec(json)
  private val boundServices = mutableMapOf<String, BoundInstance<*>>()

  internal fun <T : WorkerService> bind(
    name: String,
    instance: T,
    serviceAdapter: WorkerServiceAdapter<T>,
  ) {
    boundServices[name] = BoundInstance(name, instance, serviceAdapter)
  }

  internal fun <T : WorkerService> take(name: String, serviceAdapter: WorkerServiceAdapter<T>): T {
    return serviceAdapter.outboundService(OutboundHandler(name, this))
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
  suspend fun routeFunctionCall(call: WorkerMessage.FunctionCall): WorkerMessage.FunctionResult {
    val binding =
      boundServices[call.serviceName] as? BoundInstance<WorkerService>
        ?: throw IllegalArgumentException("Binding not found")
    val handler =
      binding.serviceAdapter.functionHandlers[call.functionId]
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
  )
}
