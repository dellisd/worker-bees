package ca.derekellis.workers.kotlin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol

internal class WorkerBeeApis
private constructor(
  private val pluginContext: IrPluginContext,
  val invocationHandlerSymbol: IrClassSymbol?, // null in browser contexts
) {
  companion object {
    fun maybeCreate(pluginContext: IrPluginContext): WorkerBeeApis? {
      if (pluginContext.referenceClass(workerServiceClassId) == null) {
        // If we don't have ZiplineService, we don't have the runtime. Abort!
        return null
      }

      val invocationHandlerSymbol = pluginContext.referenceClass(invocationHandlerClassId)

      return WorkerBeeApis(pluginContext, invocationHandlerSymbol)
    }

    private val workersFqPackage = FqPackageName("ca.derekellis.workers")
    val workerServiceClassId = workersFqPackage.classId("WorkerService")
    private val invocationHandlerClassId = workersFqPackage.classId("InvocationHandler")
  }
}
