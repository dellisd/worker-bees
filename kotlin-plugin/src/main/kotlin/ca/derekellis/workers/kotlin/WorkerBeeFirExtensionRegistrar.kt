package ca.derekellis.workers.kotlin

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class WorkerBeeFirExtensionRegistrar : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    +::WorkerBeeDeclarationCheckerExtension
  }
}
