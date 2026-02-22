package ca.derekellis.workers.gradle

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

@Suppress("unused", "UnstableApiUsage")
class WorkerHostPlugin : WorkerPluginBase() {
  override fun apply(target: Project) {
    val kotlinExtension =
      target.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return

    val parentConfiguration = target.configurations.dependencyScope(CONFIGURATION_PREFIX)

    kotlinExtension.targets.withType(KotlinJsIrTarget::class.java).configureEach { kotlinTarget ->
      kotlinTarget.binaries.configureEach { kotlinBinary ->
        val config = target.createResolvableConfiguration(kotlinBinary.mode, parentConfiguration)
        val workerDirectory =
          target.layout.buildDirectory
            .dir("workers/${kotlinBinary.mode.toString().lowercase()}")
            .get()
            .asFile

        val copyTask =
          target.tasks.register(
            "unzip${kotlinBinary.mode.capitalizedName()}Workers",
            Copy::class.java,
          ) { copy ->
            copy.dependsOn(config)
            config.get().forEach { file -> copy.from(target.zipTree(file)) }

            copy.destinationDir = workerDirectory
          }

        configureKotlinBinaryWebpackTask(target, kotlinBinary, copyTask)
        configureKotlinBinaryDistributionTask(target, kotlinBinary, copyTask)
      }
    }
  }

  private fun Project.createResolvableConfiguration(
    mode: KotlinJsBinaryMode,
    parent: NamedDomainObjectProvider<DependencyScopeConfiguration>,
  ): NamedDomainObjectProvider<ResolvableConfiguration> {
    return configurations.resolvable(workerConfigurationName(mode)) { configuration ->
      configuration.extendsFrom(parent.get())
      configuration.attributes {
        it.attribute(
          Usage.USAGE_ATTRIBUTE,
          objects.named(Usage::class.java, WORKER_CONFIGURATION_USAGE),
        )
        it.attribute(KOTLIN_JS_MODE_ATTRIBUTE, mode)
      }
    }
  }

  private fun configureKotlinBinaryWebpackTask(
    target: Project,
    kotlinBinary: JsIrBinary,
    workerTask: TaskProvider<Copy>,
  ) {
    target.tasks.withType(KotlinWebpack::class.java).configureEach { kotlinWebpack ->
      if (kotlinWebpack.name.contains(kotlinBinary.mode.capitalizedName())) {
        val workerDirs = mutableListOf(workerTask.get().destinationDir.path)
        kotlinWebpack.dependsOn(workerTask)
        kotlinWebpack.webpackConfigApplier {
          if (it.devServer == null) {
            it.devServer = KotlinWebpackConfig.DevServer(static = workerDirs)
          } else if (it.devServer!!.static == null) {
            it.devServer!!.static = workerDirs
          } else {
            it.devServer!!.static!!.addAll(workerDirs)
          }
        }
      }
    }
  }

  private fun configureKotlinBinaryDistributionTask(
    target: Project,
    kotlinBinary: JsIrBinary,
    workerTask: TaskProvider<Copy>,
  ) {
    val modeTaskName =
      if (kotlinBinary.mode == KotlinJsBinaryMode.DEVELOPMENT) {
        "jsBrowser${kotlinBinary.distribution.distributionName.get().replaceFirstChar { it.uppercase() }}Distribution"
      } else {
        "jsBrowserDistribution"
      }

    target.tasks.withType(Sync::class.java) { syncTask ->
      if (syncTask.name == modeTaskName) {
        syncTask.from(workerTask.map { it.destinationDir })
      }
    }
  }
}
