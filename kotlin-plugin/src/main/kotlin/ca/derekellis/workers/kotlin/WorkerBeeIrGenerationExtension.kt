package ca.derekellis.workers.kotlin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class WorkerBeeIrGenerationExtension(private val messageCollector: MessageCollector) :
  IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val workerBeeApis = WorkerBeeApis.maybeCreate(pluginContext) ?: return

    // TODO: do something
  }
}
