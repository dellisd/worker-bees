package ca.derekellis.workers.kotlin

import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction

class FunctionHandlerGenerator(
  private val context: IrGeneratorContext,
  private val function: IrSimpleFunction,
) {
  private val irFactory = context.irFactory

  fun createHandlerClass(): IrClass {
    irFactory.buildClass { initDefaults(function) }

    TODO()
  }
}
