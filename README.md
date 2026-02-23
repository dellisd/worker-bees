# Worker Bees

Define an interface, implement it in a web worker, call it from the browser!

----

```kotlin
// Shared API
interface MyInterface : WorkerService {
  suspend fun hello(name: String): String
}

// Worker Implementation
class RealMyInterface : MyInterface {
  override suspend fun hello(name: String): String {
    return "Hello $name"
  }
}

fun main() {
  val handle = WorkerHandle.newBrowserHandle()
  handle.bind<MyInterface>("myInterface", RealMyInterface())
}

// Browser usage
fun main() {
  val handle = WorkerHandle.newWorkerHandle(Worker("/worker.js"))

  val testService = handle.take<MyInterface>("myInterface")
  
  println(testService.hello("world"))
}
```
