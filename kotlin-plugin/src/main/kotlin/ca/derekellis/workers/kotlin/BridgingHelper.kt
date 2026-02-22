package ca.derekellis.workers.kotlin

import kotlin.collections.set
import kotlin.text.set
import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.substitute

internal class BridgingHelper(
  private val pluginContext: IrPluginContext,
  private val workerBeeApis: WorkerBeeApis,
  private val scope: ScopeWithIr,

  /** A specific type identifier that knows the values of its generic parameters. */
  val type: IrType,

  /** A potentially-generic declaration that doesn't have values for its generic parameters. */
  private val classSymbol: IrClassSymbol,
) {
  val typeIrClass = classSymbol.owner

  val bridgedFunctionsWithOverrides: List<List<IrSimpleFunctionSymbol>>
    get() {
      val result = mutableMapOf<String, MutableList<IrSimpleFunctionSymbol>>()
      for (supertype in listOf(classSymbol.owner.defaultType) + classSymbol.owner.superTypes) {
        val supertypeClass = supertype.getClass() ?: continue
        val functions = mutableListOf<IrSimpleFunction>()
        for (function in supertypeClass.functions) {
          if (function.name.identifier in NON_INTERFACE_FUNCTION_NAMES) continue
          functions += function
        }
        for (property in supertypeClass.properties) {
          property.getter?.let { functions += it }
          property.setter?.let { functions += it }
        }
        for (function in functions) {
          val overrides = result.getOrPut(function.signature) { mutableListOf() }
          overrides += function.symbol
        }
      }
      return result.values.toList()
    }

  val bridgedFunctions: List<IrSimpleFunctionSymbol>
    get() = bridgedFunctionsWithOverrides.map { it[0] }

  /** Call this on any declaration returned by [classSymbol] to fill in the generic parameters. */
  fun resolveTypeParameters(type: IrType): IrType {
    val simpleType = this.type as? IrSimpleType ?: return type
    val parameters = typeIrClass.typeParameters
    val arguments = simpleType.arguments.map { it as IrType }
    return type.substitute(parameters, arguments)
  }

  /** Declares local vars for all the serializers needed to bridge this interface. */
  fun declareSerializerTemporaries(
    statementsBuilder: IrStatementsBuilder<*>,
    serializersModuleParameter: IrValueParameter,
    serializersExpression: IrVariable,
  ): Map<IrType, IrVariable> {
    return statementsBuilder.irDeclareSerializerTemporaries(
      serializersModuleParameter = serializersModuleParameter,
      serializersExpression = serializersExpression,
    )
  }

  private fun IrStatementsBuilder<*>.irDeclareSerializerTemporaries(
    serializersModuleParameter: IrValueParameter,
    serializersExpression: IrVariable,
  ): Map<IrType, IrVariable> {
    val requiredTypes = mutableSetOf<IrType>()
    for (bridgedFunction in bridgedFunctions) {
      for (valueParameter in bridgedFunction.owner.parameters) {
        if (valueParameter.kind != IrParameterKind.Regular) continue
        requiredTypes += resolveTypeParameters(valueParameter.type)
      }
      val resolvedReturnType = resolveTypeParameters(bridgedFunction.owner.returnType)
      requiredTypes += resolvedReturnType
    }

    val result = mutableMapOf<IrType, IrVariable>()
    for (requiredType in requiredTypes) {
      val serializerExpression =
        serializerExpression(
          type = requiredType,
          serializersModuleParameter = serializersModuleParameter,
          serializersExpression = serializersExpression,
          contextual = requiredType.isContextual,
        )
      result[requiredType] =
        irTemporary(
          value = serializerExpression.expression,
          nameHint = "serializer",
          isMutable = false,
        )
    }
    return result
  }

  private val IrType.isContextual
    get() = annotations.any { it.type.getClass()?.classId == WorkerBeeApis.contextualClassId }

  class SerializerExpression(val expression: IrExpression, val hasTypeParameter: Boolean)

  /**
   * To serialize generic types that include type variables ('T'), we use [serializersExpression] to
   * extract the corresponding serializer from the list, matching by index.
   *
   * Whenever serializers for type parameters are returned they must be used! Otherwise, we will try
   * to look up a serializer for a type variable, and that will fail because the concrete type is
   * not known.
   */
  private fun IrBuilderWithScope.serializerExpression(
    type: IrType,
    serializersModuleParameter: IrValueParameter,
    serializersExpression: IrVariable,
    contextual: Boolean,
  ): SerializerExpression {
    val originalArguments = (type as IrSimpleType).arguments
    val resolvedArguments = originalArguments.map { resolveTypeParameters(it as IrType) }

    val parameterExpressions =
      resolvedArguments.map { argumentType ->
        serializerExpression(
          type = argumentType,
          serializersModuleParameter = serializersModuleParameter,
          serializersExpression = serializersExpression,
          contextual = argumentType.isContextual,
        )
      }
    //    val parameterList = irCall(workerBeeApis.listOfFunction).apply {
    //      this.type = workerBeeApis.listOfKSerializerStar
    //      typeArguments[0] = workerBeeApis.kSerializer.starProjectedType
    //      arguments[0] = irVararg(
    //        workerBeeApis.kSerializer.starProjectedType,
    //        parameterExpressions.map { it.expression },
    //      )
    //    }

    val hasTypeParameter = parameterExpressions.any { it.hasTypeParameter }
    val classifierOwner = type.classifier.owner
    val isTypeParameter = classifierOwner is IrTypeParameter

    val expression =
      when {
        classifierOwner is IrTypeParameter -> {
          // serializers.get(0)
          irCall(
              callee = workerBeeApis.listGetFunction,
              type = workerBeeApis.kSerializer.starProjectedType,
            )
            .apply {
              dispatchReceiver = irGet(serializersExpression)
              arguments[1] = irInt(classifierOwner.index)
            }
        }

        // TODO: Support worker serialization
        //      type.isSubtypeOfClass(workerBeeApis.workerService) -> {
        //        // ServiceType.Companion.Adapter(
        //        //   serializers,
        //        //   serialName("com.example.ServiceType", serializers),
        //        // )
        //        WorkerServiceAdapterGenerator(
        //          pluginContext,
        //          workerBeeApis,
        //          this@BridgingHelper.scope,
        //          pluginContext.referenceClass(type.getClass()!!.classId!!)!!.owner,
        //        ).adapterExpression(
        //          serializersListExpression = parameterList,
        //          adapterType = type,
        //        )
        //      }

        //      hasTypeParameter || contextual || type.isFlow || type.isStateFlow -> {
        //        // serializersModule.requireContextual<T>(root KClass, recurse on type args)
        //        val contextualSerializerExpression = irCall(
        //          callee = workerBeeApis.requireContextual,
        //          type = workerBeeApis.kSerializer.starProjectedType,
        //        ).apply {
        //          // TODO: call remapTypeParameters passing typeIrClass and the AdapterClass we're
        // making
        //          typeArguments[0] = type
        //          arguments[0] = irGet(serializersModuleParameter)
        //          arguments[1] =
        // irKClass(pluginContext.referenceClass(type.getClass()!!.classId!!)!!.owner)
        //          arguments[2] = parameterList
        //        }
        //        wrapWithNullableSerializerIfNeeded(
        //          type,
        //          contextualSerializerExpression,
        //          workerBeeApis.nullableSerializer,
        //        )
        //      }

        else -> {
          // serializersModule.serializer<T>()
          irCall(
              callee = workerBeeApis.serializerFunctionTypeParam,
              type = workerBeeApis.kSerializer.starProjectedType,
            )
            .apply {
              // TODO: call remapTypeParameters passing typeIrClass and the AdapterClass we're
              // making
              typeArguments[0] = type
              arguments[0] = irGet(serializersModuleParameter)
            }
        }
      }

    return SerializerExpression(
      expression = expression,
      hasTypeParameter = hasTypeParameter || isTypeParameter,
    )
  }

  companion object {
    /** Don't bridge these. */
    // TODO: Deal with `close`
    internal val NON_INTERFACE_FUNCTION_NAMES = setOf("equals", "hashCode", "toString", "close")

    fun create(
      pluginContext: IrPluginContext,
      workerBeeApis: WorkerBeeApis,
      scope: ScopeWithIr,
      element: IrElement,
      functionName: String,
      type: IrType,
    ): BridgingHelper {
      val classSymbol = type.getClass()?.classId?.let { pluginContext.referenceClass(it) }
      if (classSymbol == null || !classSymbol.owner.isInterface) {
        //        throw IllegalStateException(
        //          element = element,
        //          message = "The type argument to $functionName must be an interface type" +
        //            " (but was ${type.classFqName})",
        //        )
        // TODO: compilation exception?
        error(
          "The type argument to $functionName must be an interface type" +
            " (but was ${type.classFqName})"
        )
      }

      return BridgingHelper(pluginContext, workerBeeApis, scope, type, classSymbol)
    }
  }
}
