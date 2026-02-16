package ca.derekellis.workers.kotlin.diagnostics

import ca.derekellis.workers.kotlin.diagnostics.KtErrorsWorkerBee.WORKER_SERVICE_METHOD_MUST_BE_SUSPENDING
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory

object KtDefaultErrorMessagesWorkerBee : BaseDiagnosticRendererFactory() {
  override val MAP by
    KtDiagnosticFactoryToRendererMap("WorkerBee") { map ->
      map.put(WORKER_SERVICE_METHOD_MUST_BE_SUSPENDING, "'WorkerService' method must be suspending")
    }
}
