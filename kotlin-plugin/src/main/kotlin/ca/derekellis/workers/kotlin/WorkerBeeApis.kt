package ca.derekellis.workers.kotlin

import kotlin.collections.get
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds

internal class WorkerBeeApis private constructor(private val pluginContext: IrPluginContext) {
  companion object {
    fun maybeCreate(pluginContext: IrPluginContext): WorkerBeeApis? {
      if (pluginContext.referenceClass(workerServiceClassId) == null) {
        // If we don't have ZiplineService, we don't have the runtime. Abort!
        return null
      }

      return WorkerBeeApis(pluginContext)
    }

    private val workersFqPackage = FqPackageName("ca.derekellis.workers")
    private val workersInternalFqPackage = FqPackageName("ca.derekellis.workers.internal")
    val workerServiceClassId = workersFqPackage.classId("WorkerService")
    val workerServiceAdapterClassId = workersInternalFqPackage.classId("WorkerServiceAdapter")
    val outboundServiceClassId = workersInternalFqPackage.classId("OutboundService")
    val outboundHandlerClassId = workersInternalFqPackage.classId("OutboundHandler")
    val functionHandlerClassId = workersInternalFqPackage.classId("FunctionHandler")
    val workerHandleClassId = workersFqPackage.classId("WorkerHandle")

    private val kotlinCollectionsFqPackage = FqPackageName("kotlin.collections")

    private val serializationFqPackage = FqPackageName("kotlinx.serialization")
    private val serializationJsonFqPackage = FqPackageName("kotlinx.serialization.json")
    private val serializationModulesFqPackage = FqPackageName("kotlinx.serialization.modules")
    private val serializersModuleClassId =
      serializationModulesFqPackage.classId("SerializersModule")
    val contextualClassId = serializationFqPackage.classId("Contextual")
    val jsonElementClassId = serializationJsonFqPackage.classId("JsonElement")
    private val lenientUnitSerializerClassId = workersInternalFqPackage.classId("LenientUnitSerializer")
  }

  val any: IrClassSymbol
    get() = pluginContext.referenceClass(StandardClassIds.Any)!!

  val workerService
    get() = pluginContext.referenceClass(workerServiceClassId)!!

  val workerServiceAdapter
    get() = pluginContext.referenceClass(workerServiceAdapterClassId)!!

  val workerServiceAdapterFunctionHandlers
    get() = workerServiceAdapter.functions.single { it.owner.name.identifier == "functionHandlers" }

  val workerServiceAdapterOutboundService
    get() = workerServiceAdapter.functions.single { it.owner.name.identifier == "outboundService" }

  val workerServiceAdapterSerializers
    get() =
      pluginContext
        .referenceProperties(workerServiceAdapterClassId.callableId("serializers"))
        .single()

  val workerServiceAdapterName
    get() =
      pluginContext.referenceProperties(workerServiceAdapterClassId.callableId("name")).single()

  val functionHandler
    get() = pluginContext.referenceClass(functionHandlerClassId)!!

  val functionHandlerCall
    get() = functionHandler.functions.single { it.owner.name.identifier == "call" }

  val jsonElement
    get() = pluginContext.referenceClass(jsonElementClassId)!!

  val outboundService
    get() = pluginContext.referenceClass(outboundServiceClassId)!!

  val outboundHandler
    get() = pluginContext.referenceClass(outboundHandlerClassId)!!

  val outboundHandlerCall
    get() = outboundHandler.functions.single { it.owner.name.identifier == "call" }

  val outboundServiceHandler: IrPropertySymbol
    get() = pluginContext.referenceProperties(outboundServiceClassId.callableId("handler")).single()

  val kSerializer: IrClassSymbol
    get() = pluginContext.referenceClass(serializationFqPackage.classId("KSerializer"))!!

  /** This symbol for `SerializersModule.serializer<T>()`. */
  val serializerFunctionTypeParam: IrSimpleFunctionSymbol
    get() =
      pluginContext.referenceFunctions(serializationFqPackage.callableId("serializer")).single {
        it.owner.typeParameters.size == 1 &&
          it.owner.parameters.size == 1 &&
          it.owner.parameters[0].kind == IrParameterKind.ExtensionReceiver &&
          it.owner.parameters[0].type.getClass()?.classId == serializersModuleClassId
      }

  /** This symbol for `serializer<T>()`. */
  val serializerFunctionNoReceiver: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(serializationFqPackage.callableId("serializer"))
      .single {
        it.owner.parameters.isEmpty() &&
          it.owner.typeParameters.size == 1
      }

  val serializersModule: IrClassSymbol
    get() = pluginContext.referenceClass(serializersModuleClassId)!!

  /** The symbol for `listOf(vararg T)`. */
  val listOfFunction: IrSimpleFunctionSymbol
    get() =
      pluginContext.referenceFunctions(kotlinCollectionsFqPackage.callableId("listOf")).single {
        it.owner.parameters.firstOrNull()?.isVararg == true
      }

  val listGetFunction: IrSimpleFunctionSymbol
    get() = pluginContext.referenceFunctions(StandardClassIds.List.callableId("get")).single()

  val mapOfFunction: IrSimpleFunctionSymbol
    get() =
      pluginContext.referenceFunctions(kotlinCollectionsFqPackage.callableId("mapOf")).single {
        it.owner.parameters.firstOrNull()?.isVararg == true
      }

  val map: IrClassSymbol
    get() = pluginContext.referenceClass(StandardClassIds.Map)!!

  val list: IrClassSymbol
    get() = pluginContext.referenceClass(StandardClassIds.List)!!

  val listOfKSerializerStar: IrSimpleType
    get() = list.typeWith(kSerializer.starProjectedType)

  val lenientUnitSerializer: IrClassSymbol
    get() = pluginContext.referenceClass(lenientUnitSerializerClassId)!!

  /** Keys are renderings of functions like `Zipline.take()` and values are their rewrite targets. */
  val workerServiceAdapterFunctions: Map<String, IrSimpleFunctionSymbol> = mapOf(
    rewritePair(workerHandleClassId.callableId("take")),
    rewritePair(workerHandleClassId.callableId("bind")),
  )

  /** Maps overloads from the user-friendly function to its internal rewrite target. */
  private fun rewritePair(funName: CallableId): Pair<String, IrSimpleFunctionSymbol> {
    val overloads = pluginContext.referenceFunctions(funName)
    val rewriteTarget = overloads.single {
      it.owner.parameters.lastOrNull()?.type?.getClass()?.classId == workerServiceAdapterClassId
    }
    val original = overloads.single {
      it.owner.parameters.size + 1 == rewriteTarget.owner.parameters.size
    }
    return original.toString() to rewriteTarget
  }
}
