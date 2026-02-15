package ca.derekellis.workers

import kotlin.reflect.KClass
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener

class WorkerHandle(private val worker: Worker) : AutoCloseable {
  private val scope = MainScope()
  private val json = Json { classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS }

  private var messageCounter = 0

  fun <T : WorkerService> takeBinding(clazz: KClass<T>, binder: (WorkerServiceBinding<T>) -> T): T {
    val deferredResponse =
      scope.async {
        worker.sendMessage<WorkerMessage.Bind, WorkerMessage.BoundInstance>(
          WorkerMessage.Bind(messageCounter++, clazz.simpleName!!)
        )
      }

    return binder(ServiceBindingImpl(deferredResponse))
  }

  private inner class ServiceBindingImpl<T : WorkerService>(
    private val deferred: Deferred<WorkerMessage.BoundInstance>
  ) : WorkerServiceBinding<T> {
    override val json: Json = this@WorkerHandle.json

    override suspend fun invoke(functionName: String, vararg args: JsonElement): JsonElement {
      val instanceId = deferred.await().instanceId

      val response =
        worker.sendMessage<WorkerMessage.FunctionCall, WorkerMessage.FunctionResult>(
          WorkerMessage.FunctionCall(
            messageCounter++,
            instanceId,
            functionName,
            json.encodeToString(ListSerializer(JsonElement.serializer()), args.toList()),
          )
        )

      return json.decodeFromString(response.encodedResult)
    }
  }

  private suspend inline fun <reified T : WorkerMessage, reified R : WorkerMessage> Worker
    .sendMessage(message: T): R = suspendCancellableCoroutine { continuation ->
    val messageListener =
      object : EventListener {
        override fun handleEvent(event: Event) {
          console.dir(event)
          val data = json.decodeFromDynamic<WorkerMessage>(event.unsafeCast<MessageEvent>().data)
          if (data.id != message.id) return

          continuation.resume(data as R) { _, _, _ -> }
          this@sendMessage.removeEventListener("message", this)
        }
      }

    this.addEventListener("message", messageListener)
    this.postMessage(json.encodeToDynamic<T>(message))

    continuation.invokeOnCancellation { this.removeEventListener("message", messageListener) }
  }

  override fun close() {
    scope.cancel()
    worker.terminate()
  }
}
