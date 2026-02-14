package ca.derekellis.workers.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget

class WorkerBeePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val kotlinExtension = target.extensions.findByType(KotlinMultiplatformExtension::class.java)
      ?: return

    kotlinExtension.targets.withType(KotlinJsIrTarget::class.java).configureEach { kotlinTarget ->
      kotlinTarget.binaries.configureEach { kotlinBinary ->
        target.createConsumableConfiguration(kotlinBinary.mode)
        target.artifacts.add(workerConfigurationName(kotlinBinary.mode), kotlinBinary.mainFile)
      }
    }
  }

  private fun Project.createConsumableConfiguration(mode: KotlinJsBinaryMode) {
    configurations.consumable(workerConfigurationName(mode)) { configuration ->
      configuration.attributes {
        it.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, WORKER_CONFIGURATION_USAGE))
        it.attribute(KOTLIN_JS_MODE_ATTRIBUTE, mode)
      }
    }
  }
}