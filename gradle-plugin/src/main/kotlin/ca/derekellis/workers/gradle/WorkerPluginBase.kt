package ca.derekellis.workers.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

abstract class WorkerPluginBase : KotlinCompilerPluginSupportPlugin {
  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    return kotlinCompilation.target.project.provider {
      listOf() // No options.
    }
  }

  override fun getCompilerPluginId(): String = "ca.derekellis.workers.kotlin"

  override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact(
      groupId = "ca.derekellis.worker",
      artifactId = "kotlin-plugin",
      // version TODO
    )

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

}