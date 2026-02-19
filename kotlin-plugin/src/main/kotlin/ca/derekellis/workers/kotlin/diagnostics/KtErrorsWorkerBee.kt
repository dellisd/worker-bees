package ca.derekellis.workers.kotlin.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.NAME_IDENTIFIER
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory

object KtErrorsWorkerBee : KtDiagnosticsContainer() {
  val WORKER_SERVICE_METHOD_MUST_BE_SUSPENDING by error0<PsiElement>(NAME_IDENTIFIER)

  override fun getRendererFactory(): BaseDiagnosticRendererFactory = KtDefaultErrorMessagesWorkerBee
}
