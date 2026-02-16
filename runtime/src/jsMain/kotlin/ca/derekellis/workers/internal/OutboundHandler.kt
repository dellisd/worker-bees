package ca.derekellis.workers.internal

internal class OutboundHandler(private val serviceName: String, private val endpoint: Endpoint) {
  suspend fun call(function: FunctionHandler<*>, args: List<*>): Any? {
    val functionCall = newFunctionCall(function, args)
    val result =
      endpoint.workerBridge.postMessage(functionCall, endpoint.workerMessageCodec)
        as WorkerMessage.FunctionResult

    return endpoint.json.decodeFromString(function.resultSerializer, result.encodedResult)
  }

  private fun newFunctionCall(
    function: FunctionHandler<*>,
    args: List<*>,
  ): WorkerMessage.FunctionCall =
    WorkerMessage.FunctionCall(
      callId = endpoint.newCallId(),
      serviceName = serviceName,
      functionId = function.id,
      encodedArgs = endpoint.json.encodeToString(function.argsListSerializer, args),
    )
}
