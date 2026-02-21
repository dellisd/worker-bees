package ca.derekellis.workers.kotlin.diagnostics

import ca.derekellis.workers.kotlin.WorkerBeeApis
import ca.derekellis.workers.kotlin.diagnostics.KtErrorsWorkerBee.WORKER_SERVICE_METHOD_MUST_BE_SUSPENDING
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.utils.isInterface
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol

class WorkerBeeFunctionChecker : FirSimpleFunctionChecker(MppCheckerKind.Platform) {
  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirNamedFunction) {
    val containingClassSymbol =
      declaration.dispatchReceiverType?.toRegularClassSymbol(context.session)
    if (!containingClassSymbol.isWorkerService(context.session)) return
    if (!containingClassSymbol.isInterface) return

    if (!declaration.isSuspend) {
      reporter.reportOn(declaration.source, WORKER_SERVICE_METHOD_MUST_BE_SUSPENDING)
    }
  }

  @OptIn(ExperimentalContracts::class)
  private fun FirClassSymbol<*>?.isWorkerService(session: FirSession): Boolean {
    contract { returns(true) implies (this@isWorkerService != null) }

    if (this == null) return false

    return resolvedSuperTypeRefs.any { superTypeRef ->
      val symbol = superTypeRef.coneType.toRegularClassSymbol(session)
      symbol?.classId == WorkerBeeApis.workerServiceClassId
    }
  }
}
