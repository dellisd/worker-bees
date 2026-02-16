package ca.derekellis.workers.gradle

import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

class WorkerBeePlugin : KotlinCompilerPluginSupportPlugin {
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

  override fun apply(target: Project) {
    val kotlinExtension =
      target.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return

    val writeWebpackConfigTask =
      target.tasks.register("writeWorkerWebpackConfig", WriteWebpackConfigTask::class.java) {
        writeWebpackConfig ->
        writeWebpackConfig.target.set("webworker")
      }

    kotlinExtension.targets.withType(KotlinJsIrTarget::class.java).configureEach { kotlinTarget ->
      kotlinTarget.binaries.configureEach { kotlinBinary ->
        target.createConsumableConfiguration(kotlinBinary.mode)

        target.tasks.withType(KotlinWebpack::class.java).configureEach { kotlinWebpack ->
          if (kotlinWebpack.name.contains(kotlinBinary.mode.capitalizedName())) {
            kotlinWebpack.dependsOn(writeWebpackConfigTask)
          }
        }

        val zipTask =
          target.tasks.register(
            "zip${kotlinBinary.mode.capitalizedName()}WebpackOutput",
            Zip::class.java,
          ) { zip ->
            val kotlinWebpack =
              target.tasks.named(
                "jsBrowser${kotlinBinary.mode.capitalizedName()}Webpack",
                KotlinWebpack::class.java,
              )
            zip.from(kotlinWebpack.map { it.outputDirectory.asFileTree })
            zip.archiveBaseName.set("${target.name}${kotlinBinary.mode.capitalizedName()}")
            zip.dependsOn(kotlinWebpack)

            zip.outputs.upToDateWhen { kotlinWebpack.get().state.upToDate }
          }

        target.artifacts.add(workerConfigurationName(kotlinBinary.mode), zipTask) {
          it.builtBy(zipTask)
        }
      }
    }
  }

  private fun Project.createConsumableConfiguration(mode: KotlinJsBinaryMode) {
    configurations.consumable(workerConfigurationName(mode)) { configuration ->
      configuration.attributes {
        it.attribute(
          Usage.USAGE_ATTRIBUTE,
          objects.named(Usage::class.java, WORKER_CONFIGURATION_USAGE),
        )
        it.attribute(KOTLIN_JS_MODE_ATTRIBUTE, mode)
      }
    }
  }
}
