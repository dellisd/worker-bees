package ca.derekellis.workers.sample

class RealTestService : TestService {
  override suspend fun test(): String {
    return "Hello from the worker!"
  }

  override suspend fun square(value: Int): Int {
    return value * value
  }

  fun hello() {}
}
