package ca.derekellis.workers

interface WorkerService : AutoCloseable {
  override fun close() {
  }
}