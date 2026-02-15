package ca.derekellis.workers.gradle

import org.gradle.api.attributes.Attribute
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

internal const val CONFIGURATION_PREFIX = "worker"

internal fun workerConfigurationName(mode: KotlinJsBinaryMode) =
  "$CONFIGURATION_PREFIX${mode.capitalizedName()}"

internal const val WORKER_CONFIGURATION_USAGE = "web-worker"

internal val KOTLIN_JS_MODE_ATTRIBUTE =
  Attribute.of("ca.derekellis.worker.mode", KotlinJsBinaryMode::class.java)

internal fun KotlinJsBinaryMode.capitalizedName() =
  name.lowercase().replaceFirstChar { it.uppercase() }

internal infix fun KotlinJsBinaryMode.sameAs(webpackMode: KotlinWebpackConfig.Mode): Boolean {
  return when (this) {
    KotlinJsBinaryMode.PRODUCTION -> webpackMode == KotlinWebpackConfig.Mode.PRODUCTION
    KotlinJsBinaryMode.DEVELOPMENT -> webpackMode == KotlinWebpackConfig.Mode.DEVELOPMENT
  }
}

internal infix fun KotlinWebpackConfig.Mode.sameAs(binaryMode: KotlinJsBinaryMode): Boolean =
  binaryMode sameAs this
