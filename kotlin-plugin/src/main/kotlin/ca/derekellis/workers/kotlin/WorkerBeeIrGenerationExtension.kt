package ca.derekellis.workers.kotlin

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.isInterface

class WorkerBeeIrGenerationExtension(private val messageCollector: MessageCollector) :
  IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val workerBeeApis = WorkerBeeApis.maybeCreate(pluginContext) ?: return

    val transformer =
      object : IrElementTransformerVoidWithContext() {
        override fun visitClassNew(declaration: IrClass): IrStatement {
          val declaration = super.visitClassNew(declaration) as IrClass

          if (
            declaration.isInterface &&
              declaration.superTypes.any {
                it.getClass()?.classId == WorkerBeeApis.workerServiceClassId
              }
          ) {
            WorkerServiceAdapterGenerator(pluginContext, workerBeeApis, currentScope!!, declaration)
              .generateAdapterIfAbsent()
          }

          return declaration
        }
      }

    moduleFragment.transform(transformer, null)
  }
}
