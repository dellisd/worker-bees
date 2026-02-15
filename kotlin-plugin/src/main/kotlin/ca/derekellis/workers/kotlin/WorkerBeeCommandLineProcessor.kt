package ca.derekellis.workers.kotlin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WorkerBeeCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String
    get() = PLUGIN_ID

  override val pluginOptions: Collection<AbstractCliOption> = listOf()
}
