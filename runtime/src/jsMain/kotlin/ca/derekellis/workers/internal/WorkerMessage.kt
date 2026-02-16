package ca.derekellis.workers.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic

@Serializable
sealed interface WorkerMessage {
  val callId: Int

  @Serializable
  class Take(override val callId: Int, val name: String, val className: String) : WorkerMessage

  @Serializable
  class BoundInstance(override val callId: Int, val instanceId: String) : WorkerMessage

  @Serializable
  class FunctionCall(
    override val callId: Int,
    val serviceName: String,
    val functionId: String,
    val encodedArgs: String,
  ) : WorkerMessage, HasResponse

  @Serializable
  class FunctionResult(override val callId: Int, val encodedResult: String) : WorkerMessage

  class Codec(private val json: Json) {
    fun encode(message: WorkerMessage): dynamic {
      return json.encodeToDynamic(WorkerMessage.serializer(), message)
    }

    fun decode(message: dynamic): WorkerMessage {
      return json.decodeFromDynamic(WorkerMessage.serializer(), message)
    }
  }
}

interface HasResponse
