package ca.derekellis.workers.sample

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ca.derekellis.workers.WorkerHandle
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.Worker

fun main() {
  val worker = WorkerHandle.newWorkerHandle(Worker("/worker.js"))

  val testService = worker.take<TestService>("testService")

  renderComposable(rootElementId = "root") {
    var message by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableStateOf<Int?>(null) }
    var complexType by remember { mutableStateOf<RenderedDateTime?>(null) }
    Div { Text("Hello World") }
    message?.let { Div { Text(it) } }

    value?.let { Div { Text("Squared value: $it") } }

    complexType?.let { Div {
      Text("Rendered at ${it.time} on ${it.date}")
    }}

    LaunchedEffect(Unit) { message = testService.test() }

    LaunchedEffect(Unit) { value = testService.square(5) }

    LaunchedEffect(Unit) { complexType = testService.complexType() }
  }
}
