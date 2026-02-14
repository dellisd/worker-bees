package ca.derekellis.workers.gradle

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

@Suppress("unused", "UnstableApiUsage")
class WorkerHostPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val kotlinExtension = target.extensions.findByType(KotlinMultiplatformExtension::class.java)
      ?: return

    val parentConfiguration = target.configurations.dependencyScope(CONFIGURATION_PREFIX)

    kotlinExtension.targets.withType(KotlinJsIrTarget::class.java).configureEach { kotlinTarget ->
      kotlinTarget.binaries.configureEach { kotlinBinary ->
        val config = target.createResolvableConfiguration(kotlinBinary.mode, parentConfiguration)

        configureKotlinBinaryWebpackTask(target, kotlinBinary, config.get())
        configureKotlinBinaryDistributionTask(target, kotlinBinary, config.get())
      }
    }
  }

  private fun Project.createResolvableConfiguration(
    mode: KotlinJsBinaryMode,
    parent: NamedDomainObjectProvider<DependencyScopeConfiguration>
  ): NamedDomainObjectProvider<ResolvableConfiguration> {
    return configurations.resolvable(workerConfigurationName(mode)) { configuration ->
      configuration.extendsFrom(parent.get())
      configuration.attributes {
        it.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, WORKER_CONFIGURATION_USAGE))
        it.attribute(KOTLIN_JS_MODE_ATTRIBUTE, mode)
      }
    }
  }

  private fun configureKotlinBinaryWebpackTask(
    target: Project,
    kotlinBinary: JsIrBinary,
    resolvableConfiguration: ResolvableConfiguration
  ) {
    target.tasks.withType(KotlinWebpack::class.java).configureEach { kotlinWebpack ->
      if (kotlinWebpack.mode sameAs kotlinBinary.mode) {
        val workerDirs = resolvableConfiguration.files.map { it.parent }.toMutableList()
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
    resolvableConfiguration: ResolvableConfiguration
  ) {
    val modeTaskName =
      "jsBrowser${kotlinBinary.distribution.distributionName.get().replaceFirstChar { it.uppercase() }}Distribution"

    target.tasks.withType(Sync::class.java) { syncTask ->
      if (syncTask.name == modeTaskName) {
        syncTask.from(resolvableConfiguration)
      }
    }
  }
}