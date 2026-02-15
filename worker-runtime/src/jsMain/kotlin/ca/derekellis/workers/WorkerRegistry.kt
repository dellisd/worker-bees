package ca.derekellis.workers

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.w3c.dom.MessageEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener

class WorkerRegistry {
  private val scope = MainScope()
  val json = Json { classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS }

  private val boundInstances = mutableMapOf<String, BoundInstance>()

  private var idCounter = 1

  fun init() {
    console.dir(self)
    val messageListener =
      object : EventListener {
        override fun handleEvent(event: Event) {
          scope.launch {
            val result =
              when (
                val data =
                  json.decodeFromDynamic<WorkerMessage>(event.unsafeCast<MessageEvent>().data)
              ) {
                is WorkerMessage.Take -> takeInstance(data)
                is WorkerMessage.FunctionCall -> handleFunctionCall(data)
                is WorkerMessage.BoundInstance,
                is WorkerMessage.FunctionResult -> throw UnsupportedOperationException()
              }
            self.postMessage(json.encodeToDynamic(result))
          }
        }
      }

    self.addEventListener("message", messageListener)
  }

  private fun takeInstance(take: WorkerMessage.Take): WorkerMessage.BoundInstance {
    boundInstances[take.name]
      ?: throw IllegalStateException(
        "No instance bound with name ${take.name} for class ${take.className}"
      )

    return WorkerMessage.BoundInstance(take.id, take.name)
  }

  private suspend fun handleFunctionCall(
    functionCall: WorkerMessage.FunctionCall
  ): WorkerMessage.FunctionResult {
    val boundInstance =
      boundInstances[functionCall.instanceId]
        ?: throw IllegalStateException("No instance found for ${functionCall.instanceId}")

    val result =
      boundInstance.invocationHandler.handle(
        instance = boundInstance.instance.unsafeCast<WorkerService>(),
        functionName = functionCall.functionName,
        args = json.decodeFromString(functionCall.encodedArgs),
      )

    return WorkerMessage.FunctionResult(
      functionCall.id,
      JSON.stringify(json.encodeToDynamic(result)),
    )
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : WorkerService> bind(name: String, instance: T, invocationHandler: InvocationHandler<T>) {
    boundInstances[name] =
      BoundInstance(instance, name, invocationHandler as InvocationHandler<WorkerService>)
  }

  private data class BoundInstance(
    val instance: Any,
    val id: String,
    val invocationHandler: InvocationHandler<WorkerService>,
  )
}
