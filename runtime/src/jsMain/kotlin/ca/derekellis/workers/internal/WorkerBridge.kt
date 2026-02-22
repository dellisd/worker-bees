package ca.derekellis.workers.internal

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import org.w3c.dom.DedicatedWorkerGlobalScope
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener

@PublishedApi
internal interface WorkerBridge {
  fun incomingMessages(codec: WorkerMessage.Codec): Flow<WorkerMessage>

  suspend fun postMessage(message: WorkerMessage, codec: WorkerMessage.Codec): WorkerMessage?

  fun terminate()
}

internal class BrowserWorkerBridge(private val worker: Worker) : WorkerBridge {
  override fun incomingMessages(codec: WorkerMessage.Codec): Flow<WorkerMessage> = callbackFlow {
    val messageListener =
      object : EventListener {
        override fun handleEvent(event: Event) {
          val data = codec.decode(event.unsafeCast<MessageEvent>().data)
          trySend(data)
        }
      }

    worker.addEventListener("message", messageListener)

    awaitClose { worker.removeEventListener("message", messageListener) }
  }

  override suspend fun postMessage(
    message: WorkerMessage,
    codec: WorkerMessage.Codec,
  ): WorkerMessage? = coroutineScope {
    val responses = incomingMessages(codec)
    val deferredResponse =
      if (message is HasResponse)
         async { responses.first { it.callId == message.callId } }
      else null

    worker.postMessage(codec.encode(message))
    return@coroutineScope deferredResponse?.await()
  }

  override fun terminate() {
    worker.terminate()
  }
}

internal external val self: DedicatedWorkerGlobalScope

internal class WorkerWorkerBridge() : WorkerBridge {
  override fun incomingMessages(codec: WorkerMessage.Codec): Flow<WorkerMessage> = callbackFlow {
    val messageListener =
      object : EventListener {
        override fun handleEvent(event: Event) {
          val data = codec.decode(event.unsafeCast<MessageEvent>().data)
          trySend(data)
        }
      }

    self.addEventListener("message", messageListener)

    awaitClose { self.removeEventListener("message", messageListener) }
  }

  override suspend fun postMessage(
    message: WorkerMessage,
    codec: WorkerMessage.Codec,
  ): WorkerMessage? {
    val responses = incomingMessages(codec)
    val deferredResponse =
      if (message is HasResponse)
        coroutineScope { async { responses.first { it.callId == message.callId } } }
      else null

    self.postMessage(codec.encode(message))
    return deferredResponse?.await()
  }

  override fun terminate() {
    TODO("Not yet implemented")
  }
}
