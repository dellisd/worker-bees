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
import kotlin.reflect.KClass
import kotlin.reflect.createInstance

class WorkerRegistry {
  private val scope = MainScope()
  val json = Json {
    classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
  }

  private val factoryRegistry = mutableMapOf<String, Registration>()
  private val instanceRegistry = mutableMapOf<String, RegisteredInstance>()

  private var idCounter = 1

  fun init() {
    console.dir(self)
    val messageListener = object : EventListener {
      override fun handleEvent(event: Event) {
        scope.launch {
        val result = when (val data = json.decodeFromDynamic<WorkerMessage>(event.unsafeCast<MessageEvent>().data)) {
          is WorkerMessage.Bind -> newBoundInstance(data)
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

  @OptIn(ExperimentalJsReflectionCreateInstance::class)
  private fun newBoundInstance(bind: WorkerMessage.Bind): WorkerMessage.BoundInstance {
    val registration = factoryRegistry[bind.className] ?: throw IllegalStateException("No factory binding found for ${bind.className}")

    val newInstanceId = "${registration.typeClass.simpleName}-${idCounter++}"
    instanceRegistry[newInstanceId] = RegisteredInstance(registration.instanceClass.createInstance(), registration)

    return WorkerMessage.BoundInstance(bind.id, newInstanceId)
  }

  private suspend fun handleFunctionCall(functionCall: WorkerMessage.FunctionCall): WorkerMessage.FunctionResult {
    val registeredInstance = instanceRegistry[functionCall.instanceId] ?: throw IllegalStateException("No instance found for ${functionCall.instanceId}")

    val result = registeredInstance.registration.invocationHandler.handle(
      instance = registeredInstance.instance.unsafeCast<WorkerService>(),
      functionName = functionCall.functionName,
      args = json.decodeFromString(functionCall.encodedArgs),
    )

    println(result)

    return WorkerMessage.FunctionResult(functionCall.id, JSON.stringify(json.encodeToDynamic(result)))
  }

  inline fun <reified T : WorkerService> register(clazz: KClass<out T>, invocationHandler: InvocationHandler<T>) {
    register(T::class, clazz, invocationHandler)
  }
  @Suppress("UNCHECKED_CAST")
  fun <T : WorkerService> register(typeClass: KClass<T>, instanceClass: KClass<out T>, invocationHandler: InvocationHandler<T>) {
    console.log("Registering ${typeClass.simpleName} for ${instanceClass.simpleName}")
    factoryRegistry[typeClass.simpleName!!] = Registration(typeClass, instanceClass, invocationHandler as InvocationHandler<WorkerService>)
  }

  private data class Registration(val typeClass: KClass<out WorkerService>, val instanceClass: KClass<out WorkerService>, val invocationHandler: InvocationHandler<WorkerService>)

  private data class RegisteredInstance(val instance: Any, val registration: Registration)
}