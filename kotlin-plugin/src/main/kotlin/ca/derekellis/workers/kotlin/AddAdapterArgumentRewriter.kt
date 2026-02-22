package ca.derekellis.workers.kotlin

import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.util.patchDeclarationParents

internal class AddAdapterArgumentRewriter(
  private val pluginContext: IrPluginContext,
  private val workerBeeApis: WorkerBeeApis,
  private val scope: ScopeWithIr,
  private val declarationParent: IrDeclarationParent,
  private val original: IrCall,
  private val rewrittenFunction: IrSimpleFunctionSymbol,
) {
  /** The user-defined interface type, like `SampleService`. */
  private val bridgedInterfaceType: IrType = original.typeArguments[0]!!

  private val bridgingHelper = BridgingHelper.create(
    pluginContext,
    workerBeeApis,
    scope,
    original,
    "WorkerHandle.${original.symbol.owner.name.identifier}()",
    bridgedInterfaceType,
  )

  fun rewrite(): IrCall {
    val adapterExpression = WorkerServiceAdapterGenerator(
      pluginContext,
      workerBeeApis,
      scope,
      bridgingHelper.typeIrClass
    ).adapterExpression(bridgedInterfaceType as IrSimpleType)

    return irCall(original, rewrittenFunction).apply {
      arguments += adapterExpression
      patchDeclarationParents(declarationParent)
    }
  }
}