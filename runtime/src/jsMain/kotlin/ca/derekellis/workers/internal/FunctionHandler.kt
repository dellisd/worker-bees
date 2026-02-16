package ca.derekellis.workers.internal

import ca.derekellis.workers.WorkerService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

internal abstract class FunctionHandler<T : WorkerService>(
  val id: String,
  argSerializers: List<KSerializer<*>>,
  resultSerializer: KSerializer<*>,
) {
  val argsListSerializer = ArgsListSerializer(argSerializers)

  val resultSerializer = resultSerializer

  abstract suspend fun call(instance: T, args: List<*>): Any?
}

/** This uses [Int] as a placeholder; in practice the element type depends on the argument type. */
private val argsListDescriptor = ListSerializer(Int.serializer()).descriptor

internal class ArgsListSerializer(internal val serializers: List<KSerializer<*>>) :
  KSerializer<List<*>> {
  override val descriptor = argsListDescriptor

  override fun serialize(encoder: Encoder, value: List<*>) {
    check(value.size == serializers.size)
    encoder.encodeStructure(descriptor) {
      for (i in serializers.indices) {
        @Suppress("UNCHECKED_CAST") // We don't have a type argument T for each parameter.
        encodeSerializableElement(descriptor, i, serializers[i] as KSerializer<Any?>, value[i])
      }
    }
  }

  override fun deserialize(decoder: Decoder): List<*> {
    return decoder.decodeStructure(descriptor) {
      val result = mutableListOf<Any?>()
      for (i in serializers.indices) {
        check(decodeElementIndex(descriptor) == i)
        result += decodeSerializableElement(descriptor, i, serializers[i])
      }
      check(decodeElementIndex(descriptor) == DECODE_DONE)
      return@decodeStructure result
    }
  }
}
