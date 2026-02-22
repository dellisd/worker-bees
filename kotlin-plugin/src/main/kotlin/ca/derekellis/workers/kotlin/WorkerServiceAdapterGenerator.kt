package ca.derekellis.workers.kotlin

import kotlin.collections.plusAssign
import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildReceiverParameter
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addFakeOverrides
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.remapTypeParameters
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class WorkerServiceAdapterGenerator(
  private val pluginContext: IrPluginContext,
  private val workerBeeApis: WorkerBeeApis,
  private val scope: ScopeWithIr,
  private val original: IrClass,
) {
  private val irFactory = pluginContext.irFactory
  private val irTypeSystemContext = IrTypeSystemContextImpl(pluginContext.irBuiltIns)

  private val bridgingHelper =
    BridgingHelper.create(
      pluginContext,
      workerBeeApis,
      scope,
      original,
      "WorkerHandle.take()",
      original.defaultType,
    )

  fun generateAdapterIfAbsent(): IrClass {
    val companion = getOrCreateCompanion(original, pluginContext)
    return getOrCreateAdapterClass(companion)
  }

  /**
   * Copy the type parameters on one type or function to another type or function. This adds a
   * suffix to the type parameter to make it easier to see what's happening in dumped code.
   */
  private fun IrTypeParametersContainer.copyTypeParametersFromOriginal(
    suffix: String,
    isReified: Boolean = false,
  ) {
    for (typeParameter in original.typeParameters) {
      addTypeParameter {
        this.name = Name.identifier("${typeParameter.name.identifier}$suffix")
        this.superTypes += typeParameter.superTypes
        this.variance = typeParameter.variance
        this.isReified = isReified
      }
    }
  }

  private fun getOrCreateAdapterClass(companion: IrClass): IrClass {
    // class Adapter : WorkerServiceAdapter<SampleService>(
    //   override val serializers: List<KSerializer<*>>,
    // ) {
    //   ...
    // }
    val existing =
      companion.declarations.firstOrNull { it is IrClass && it.name.identifier == "Adapter" }
    if (existing != null) return existing as IrClass

    val adapterClass =
      irFactory
        .buildClass {
          initDefaults(original)
          name = Name.identifier("Adapter")
          visibility = DescriptorVisibilities.INTERNAL
        }
        .apply {
          copyTypeParametersFromOriginal("X")
          parent = companion
          val serviceT = original.defaultType.remapTypeParameters(original, this@apply)
          superTypes = listOf(workerBeeApis.workerServiceAdapter.typeWith(serviceT))
          createThisReceiverParameter()
        }

    val constructor =
      adapterClass
        .addConstructor {
          initDefaults(original)
          visibility = DescriptorVisibilities.INTERNAL
        }
        .apply {
          addValueParameter {
            initDefaults(original)
            name = Name.identifier("serializers")
            type = workerBeeApis.listOfKSerializerStar
          }
          irConstructorBody(pluginContext) { statements ->
            statements +=
              irDelegatingConstructorCall(
                context = pluginContext,
                symbol = workerBeeApis.workerServiceAdapter.constructors.single(),
                typeArgumentsCount = 1,
              ) {
                typeArguments[0] = original.defaultType.remapTypeParameters(original, adapterClass)
              }
            statements +=
              irInstanceInitializerCall(context = pluginContext, classSymbol = adapterClass.symbol)
          }
        }

    val nameProperty = irNameProperty(adapterClass)
    adapterClass.declarations += nameProperty

    val serializersProperty = irSerializersProperty(adapterClass, constructor)
    adapterClass.declarations += serializersProperty

    var nextId = 0
    val functionAdapterClasses =
      bridgingHelper.bridgedFunctions.associateWith {
        irFunctionHandlerClass("FunctionAdapter${nextId++}", bridgingHelper, adapterClass, it)
      }

    adapterClass.declarations += functionAdapterClasses.values

    val functionHandlersFunction =
      irFunctionHandlersFunction(
        bridgingHelper = bridgingHelper,
        adapterClass = adapterClass,
        functionAdapterClasses = functionAdapterClasses,
        serializersProperty = serializersProperty,
      )

    val outboundServiceClass = irOutboundServiceClass(adapterClass, bridgingHelper)
    val outboundServiceFunction =
      irOutboundServiceFunction(bridgingHelper, adapterClass, outboundServiceClass)

    adapterClass.declarations += outboundServiceClass

    adapterClass.addFakeOverrides(
      irTypeSystemContext,
      mapOf(adapterClass to listOf(functionHandlersFunction, outboundServiceFunction)),
    )

    companion.declarations += adapterClass
    companion.patchDeclarationParents(original)

    return adapterClass
  }

  private fun irOutboundServiceClass(
    adapterClass: IrClass,
    bridgingHelper: BridgingHelper,
  ): IrClass {
    val outboundServiceClass =
      irFactory
        .buildClass {
          initDefaults(original)
          name = Name.identifier("GeneratedOutboundService")
          visibility = DescriptorVisibilities.PRIVATE
        }
        .apply {
          parent = adapterClass
          createThisReceiverParameter()
          copyTypeParametersFromOriginal("S")
          thisReceiver?.type = defaultDispatchReceiver
        }

    val bridgeT = bridgingHelper.type.remapTypeParameters(original, outboundServiceClass)
    outboundServiceClass.superTypes = listOf(bridgeT, workerBeeApis.outboundService.defaultType)

    val constructor =
      outboundServiceClass
        .addConstructor { initDefaults(original) }
        .apply {
          addValueParameter {
            initDefaults(original)
            name = Name.identifier("handler")
            type = workerBeeApis.outboundService.defaultType
          }
        }
    constructor.irConstructorBody(pluginContext) { statements ->
      statements +=
        irDelegatingConstructorCall(
          context = pluginContext,
          symbol = workerBeeApis.any.constructors.single(),
        )
      statements +=
        irInstanceInitializerCall(
          context = pluginContext,
          classSymbol = outboundServiceClass.symbol,
        )
    }

    val handlerProperty =
      irVal(
        pluginContext = pluginContext,
        propertyType = workerBeeApis.outboundHandler.defaultType,
        declaringClass = outboundServiceClass,
        propertyName = workerBeeApis.outboundServiceHandler.owner.name,
        overriddenProperty = workerBeeApis.outboundServiceHandler,
      ) {
        irExprBody(irGet(constructor.parameters[0]))
      }
    outboundServiceClass.declarations += handlerProperty

    bridgingHelper.bridgedFunctionsWithOverrides.forEach {
      outboundServiceClass.irBridgedFunction(
        bridgingHelper = bridgingHelper,
        handlerProperty = handlerProperty,
        overridesList = it,
      )
    }

    return outboundServiceClass
  }

  private fun irNameProperty(adapterClass: IrClass): IrProperty {
    // override val name: String = "MyClass"
    return irVal(
      pluginContext = pluginContext,
      propertyType = pluginContext.irBuiltIns.stringType,
      declaringClass = adapterClass,
      propertyName = workerBeeApis.workerServiceAdapterName.owner.name,
      overriddenProperty = workerBeeApis.workerServiceAdapterName,
    ) {
      irExprBody(irString(original.name.identifier))
    }
  }

  private fun irSerializersProperty(adapterClass: IrClass, value: IrConstructor): IrProperty {
    // override val serializers: List<KSerializer<*>> = serializers
    return irVal(
      pluginContext = pluginContext,
      propertyType = workerBeeApis.listOfKSerializerStar,
      declaringClass = adapterClass,
      propertyName = workerBeeApis.workerServiceAdapterSerializers.owner.name,
      overriddenProperty = workerBeeApis.workerServiceAdapterSerializers,
    ) {
      irExprBody(irGet(value.parameters[0]))
    }
  }

  private fun irFunctionHandlersFunction(
    bridgingHelper: BridgingHelper,
    adapterClass: IrClass,
    functionAdapterClasses: Map<IrSimpleFunctionSymbol, IrClass>,
    serializersProperty: IrProperty,
  ): IrSimpleFunction {
    val bridgeT = bridgingHelper.type.remapTypeParameters(original, adapterClass)
    val functionHandlerT = workerBeeApis.functionHandler.typeWith(bridgeT)

    val function =
      adapterClass
        .addFunction {
          initDefaults(original)
          name = workerBeeApis.workerServiceAdapterFunctionHandlers.owner.name
          returnType = workerBeeApis.list.typeWith(workerBeeApis.functionHandler.typeWith(bridgeT))
        }
        .apply {
          parameters += buildReceiverParameter {
            initDefaults(original)
            type = adapterClass.typeWith(adapterClass.typeParameters.map { it.defaultType })
          }
          addValueParameter {
            initDefaults(original)
            name = Name.identifier("serializersModule")
            type = workerBeeApis.serializersModule.defaultType
          }
          overriddenSymbols = listOf(workerBeeApis.workerServiceAdapterFunctionHandlers)
        }

    function.irFunctionBody(context = pluginContext, scopeOwnerSymbol = function.symbol) {
      val serializersLocal =
        irTemporary(
            value =
              irCall(callee = serializersProperty.getter!!).apply {
                dispatchReceiver = irGet(function.parameters[0])
              },
            nameHint = "serializers",
            isMutable = false,
          )
          .apply { origin = IrDeclarationOrigin.DEFINED }

      val serializers =
        bridgingHelper.declareSerializerTemporaries(
          statementsBuilder = this@irFunctionBody,
          serializersModuleParameter = function.parameters[1],
          serializersExpression = serializersLocal,
        )

      val expressions = mutableListOf<IrExpression>()
      // Call every FunctionAdapter constructor
      for ((bridgedFunction, handlerClass) in functionAdapterClasses) {
        expressions +=
          irCallConstructor(
              callee = handlerClass.constructors.single().symbol,
              typeArguments = adapterClass.typeParameters.map { it.defaultType },
            )
            .apply {
              type = functionHandlerT
              arguments[0] =
                irCall(workerBeeApis.listOfFunction).apply {
                  typeArguments[0] = workerBeeApis.kSerializer.starProjectedType
                  arguments[0] =
                    irVararg(
                      workerBeeApis.kSerializer.starProjectedType,
                      bridgedFunction.owner.parameters
                        .filter { it.kind == IrParameterKind.Regular }
                        .map { irGet(serializers[it.type]!!) },
                    )
                }
              val returnType = bridgedFunction.owner.returnType
              arguments[1] = irGet(serializers[returnType]!!)
            }
      }

      // return listOf<FunctionAdapter<SampleService>>(
      //   FunctionAdapter0(..),
      //   FunctionAdapter1(...),
      // )
      +irReturn(
        irCall(workerBeeApis.listOfFunction).apply {
          typeArguments[0] = functionHandlerT
          arguments[0] = irVararg(functionHandlerT, expressions)
        }
      )
    }

    return function
  }

  // override WorkerServiceAdapter.outboundService(outboundHandler: OutboundHandler): T
  private fun irOutboundServiceFunction(
    bridgingHelper: BridgingHelper,
    adapterClass: IrClass,
    outboundServiceClass: IrClass,
  ): IrSimpleFunction {
    val bridgeT = bridgingHelper.type.remapTypeParameters(original, outboundServiceClass)

    val outboundServiceFunction =
      adapterClass
        .addFunction {
          initDefaults(original)
          name = workerBeeApis.workerServiceAdapterOutboundService.owner.name
          returnType = bridgeT
        }
        .apply {
          parameters += buildReceiverParameter {
            initDefaults(original)
            type = adapterClass.defaultDispatchReceiver
          }
          addValueParameter {
            initDefaults(original)
            name = Name.identifier("outboundHandler")
            type = workerBeeApis.outboundHandler.defaultType
          }
          overriddenSymbols = listOf(workerBeeApis.workerServiceAdapterOutboundService)
        }

    outboundServiceFunction.irFunctionBody(
      context = pluginContext,
      scopeOwnerSymbol = outboundServiceFunction.symbol,
    ) {
      +irReturn(
        irCallConstructor(
            callee = outboundServiceClass.constructors.single().symbol,
            typeArguments = adapterClass.typeParameters.map { it.defaultType },
          )
          .apply {
            arguments[0] = irGet(outboundServiceFunction.parameters[1])
            type = bridgeT
          }
      )
    }

    return outboundServiceFunction
  }

  private fun irFunctionHandlerClass(
    className: String,
    bridgingHelper: BridgingHelper,
    adapterClass: IrClass,
    bridgedFunction: IrSimpleFunctionSymbol,
  ): IrClass {
    val functionClass =
      irFactory
        .buildClass {
          initDefaults(original)
          name = Name.identifier(className)
          visibility = DescriptorVisibilities.PRIVATE
        }
        .apply {
          parent = adapterClass
          createThisReceiverParameter()
          copyTypeParametersFromOriginal("F")
          thisReceiver?.type = defaultDispatchReceiver
        }

    val bridgeT = bridgingHelper.type.remapTypeParameters(original, functionClass)
    functionClass.superTypes = listOf(workerBeeApis.functionHandler.typeWith(bridgeT))

    functionClass
      .addConstructor { initDefaults(original) }
      .apply {
        addValueParameter {
          initDefaults(original)
          name = Name.identifier("argSerializers")
          type = workerBeeApis.listOfKSerializerStar
        }
        addValueParameter {
          initDefaults(original)
          name = Name.identifier("resultSerializer")
          type = workerBeeApis.kSerializer.starProjectedType
        }

        irConstructorBody(pluginContext) { statements ->
          statements +=
            irDelegatingConstructorCall(
              context = pluginContext,
              symbol = workerBeeApis.functionHandler.constructors.single(),
              valueArgumentsCount = 3,
              typeArgumentsCount = 1,
            ) {
              typeArguments[0] = bridgeT
              arguments[0] = irString(bridgedFunction.owner.signature.signatureHash())
              arguments[1] = irGet(parameters[0])
              arguments[2] = irGet(parameters[1])
            }
          statements +=
            irInstanceInitializerCall(context = pluginContext, classSymbol = functionClass.symbol)
        }
      }

    val callFunction =
      functionClass
        .addFunction {
          initDefaults(original)
          name = workerBeeApis.functionHandlerCall.owner.name
          isSuspend = true
          returnType = workerBeeApis.any.defaultType.makeNullable()
        }
        .apply {
          overriddenSymbols = listOf(workerBeeApis.functionHandlerCall)

          parameters += buildReceiverParameter {
            initDefaults(original)
            type = functionClass.typeWith(functionClass.typeParameters.map { it.defaultType })
          }
          addValueParameter {
            initDefaults(original)
            name = Name.identifier("instance")
            type = bridgeT
          }
          addValueParameter {
            initDefaults(original)
            name = Name.identifier("args")
            type = workerBeeApis.list.starProjectedType
          }
        }

    callFunction.irFunctionBody(context = pluginContext, scopeOwnerSymbol = callFunction.symbol) {
      // return instance.function(args[0], args[1], ...)
      +irReturn(
        irCall(bridgedFunction).apply {
          dispatchReceiver = irGet(callFunction.parameters[1])

          bridgedFunction.owner.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .forEachIndexed { i, param ->
              arguments[i + 1] =
                irCall(workerBeeApis.listGetFunction).apply {
                  dispatchReceiver = irGet(callFunction.parameters[2])
                  arguments[1] = irInt(i)
                }
            }
        }
      )
    }

    return functionClass
  }

  private fun IrClass.irBridgedFunction(
    bridgingHelper: BridgingHelper,
    handlerProperty: IrProperty,
    overridesList: List<IrSimpleFunctionSymbol>,
  ): IrSimpleFunction {
    val bridgedFunction = overridesList[0].owner
    val functionReturnType =
      bridgingHelper
        .resolveTypeParameters(bridgedFunction.returnType)
        .remapTypeParameters(original, this@irBridgedFunction)

    val function =
      factory
        .buildFun {
          initDefaults(original)
          name = bridgedFunction.name
          isSuspend = true
          returnType = functionReturnType
        }
        .apply {
          overriddenSymbols = overridesList
          parameters += buildReceiverParameter {
            initDefaults(original)
            type = defaultDispatchReceiver
          }
        }

    declarations += function
    function.parent = this@irBridgedFunction

    // We don't support property because they can't suspend

    val regularParameters = bridgedFunction.parameters.filter { it.kind == IrParameterKind.Regular }

    for (parameter in regularParameters) {
      function.addValueParameter {
        initDefaults(original)
        name = parameter.name
        type =
          bridgingHelper
            .resolveTypeParameters(parameter.type)
            .remapTypeParameters(original, this@irBridgedFunction)
      }
    }

    function.irFunctionBody(context = pluginContext, scopeOwnerSymbol = function.symbol) {
      val handlerLocal =
        irTemporary(
            value =
              irCall(handlerProperty.getter!!).apply {
                dispatchReceiver = irGet(function.dispatchReceiverParameter!!)
              },
            nameHint = "handler",
            isMutable = false,
          )
          .apply { origin = IrDeclarationOrigin.DEFINED }

      // handler.call(functionId, arg0, arg1, arg2)
      val call =
        irCall(workerBeeApis.outboundHandlerCall).apply {
          dispatchReceiver = irGet(handlerLocal)
          arguments[1] = irString(function.signature.signatureHash())
          arguments[2] =
            irVararg(
              elementType = pluginContext.irBuiltIns.anyType.makeNullable(),
              values =
                function.parameters
                  .filter { it.kind == IrParameterKind.Regular }
                  .map { irGet(type = it.type, variable = it.symbol) },
            )
        }

      +irReturn(
        value = irAs(argument = call, type = functionReturnType),
        returnTargetSymbol = function.symbol,
        type = pluginContext.irBuiltIns.nothingType,
      )
    }

    return function
  }

  private val IrClass.defaultDispatchReceiver
    get() = typeWith(typeParameters.map { it.defaultType })
}
