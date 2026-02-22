package ca.derekellis.workers.sample

import kotlin.js.Date

class RealTestService : TestService {
  override suspend fun test(): String {
    return "Hello from the worker!"
  }

  override suspend fun square(value: Int): Int {
    return value * value
  }

  override suspend fun complexType(): RenderedDateTime {
    val date = Date()
    return RenderedDateTime(
      date = date.toLocaleDateString(emptyArray()),
      time = date.toLocaleTimeString(emptyArray())
    )
  }
}
