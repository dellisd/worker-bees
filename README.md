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
  WorkerRegistry().init()
}

// Browser usage
fun main() {
  val worker = WorkerHandle(Worker("/worker.js"))

  val testService = worker.takeBinding(TestService::class)
  
  println(testService.hello("world"))
}
```

WIP:
* :sample:browser:jsProcessResources task is incorrectly skipped when :sample:worker is rebundled.
* Encoding/decoding function calls is done manually, but can be generated automatically like in [Zipline](https://github.com/cashapp/zipline).
