package ca.derekellis.workers

import kotlinx.serialization.Serializable

@Serializable
sealed interface WorkerMessage {
  val id: Int

  @Serializable
  class Take(override val id: Int, val name: String, val className: String) : WorkerMessage

  @Serializable class BoundInstance(override val id: Int, val instanceId: String) : WorkerMessage

  @Serializable
  class FunctionCall(
    override val id: Int,
    val instanceId: String,
    val functionName: String,
    val encodedArgs: String,
  ) : WorkerMessage

  @Serializable
  class FunctionResult(override val id: Int, val encodedResult: String) : WorkerMessage
}
