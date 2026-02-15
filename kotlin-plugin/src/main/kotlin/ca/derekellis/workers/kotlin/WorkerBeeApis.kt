package ca.derekellis.workers.kotlin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext

internal class WorkerBeeApis private constructor(pluginContext: IrPluginContext) {
  companion object {
    fun maybeCreate(pluginContext: IrPluginContext): WorkerBeeApis? {
      if (pluginContext.referenceClass(workerServiceClassId) == null) {
        // If we don't have ZiplineService, we don't have the runtime. Abort!
        return null
      }

      return WorkerBeeApis(pluginContext)
    }

    private val workersFqPackage = FqPackageName("ca.derekellis.workers")
    val workerServiceClassId = workersFqPackage.classId("WorkerService")
  }
}
