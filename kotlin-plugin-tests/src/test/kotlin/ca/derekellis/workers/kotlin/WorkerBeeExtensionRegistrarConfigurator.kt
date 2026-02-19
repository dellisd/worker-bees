package ca.derekellis.workers.kotlin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.DependencyKind
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

fun TestConfigurationBuilder.configurePlugin() {
  globalDefaults {
    targetPlatform = JsPlatforms.defaultJsPlatform
    targetBackend = TargetBackend.JS_IR
    dependencyKind = DependencyKind.Source
  }

  useConfigurators(::WorkerBeeExtensionRegistrarConfigurator)

  useCustomRuntimeClasspathProviders(::WorkerBeeRuntimeClasspathProvider)
}

@OptIn(ExperimentalCompilerApi::class)
class WorkerBeeExtensionRegistrarConfigurator(testServices: TestServices) :
  EnvironmentConfigurator(testServices) {
  override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
    module: TestModule,
    configuration: CompilerConfiguration,
  ) {
    FirExtensionRegistrarAdapter.registerExtension(WorkerBeeFirExtensionRegistrar())
    IrGenerationExtension.registerExtension(
      WorkerBeeIrGenerationExtension(messageCollector = configuration.messageCollector)
    )
  }
}
