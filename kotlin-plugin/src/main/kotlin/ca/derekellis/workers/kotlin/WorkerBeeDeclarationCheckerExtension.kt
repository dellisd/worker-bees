package ca.derekellis.workers.kotlin

import ca.derekellis.workers.kotlin.diagnostics.WorkerBeeFunctionChecker
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

class WorkerBeeDeclarationCheckerExtension(session: FirSession) :
  FirAdditionalCheckersExtension(session) {
  override val declarationCheckers: DeclarationCheckers =
    object : DeclarationCheckers() {
      override val simpleFunctionCheckers: Set<FirSimpleFunctionChecker> =
        setOf(WorkerBeeFunctionChecker())
    }
}
