@file:OptIn(KspExperimental::class)

package dev.mattramotar.atom.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import dev.mattramotar.atom.runtime.annotations.AutoAtom
import dev.mattramotar.atom.runtime.annotations.AutoSerializer
import dev.mattramotar.atom.runtime.annotations.InitialState

class AtomProcessor(private val env: SymbolProcessorEnvironment) : SymbolProcessor {

    private val codegen: CodeGenerator = env.codeGenerator
    private val logger = env.logger
    private val options = env.options
    private val diFlavor: String = options["atom.di"]?.lowercase() ?: "manual"
    private val moduleId: String = options["atom.module.id"] ?: "default"
    private val pkgGenerated: String = options["atom.package"]
        ?: if (moduleId == "default") {
            Names.GENERATED_PACKAGE
        } else {
            "${Names.GENERATED_PACKAGE}.${moduleId}"
        }

    private val composeExtensions: Boolean = options["atom.compose.extensions"]?.toBooleanStrictOrNull() ?: false
    private val failOnMissingSerializer: Boolean =
        options["atom.failOnMissingSerializer"]?.toBooleanStrictOrNull() ?: false
    private val metroScope: String = options["atom.metro.scope"] ?: "dev.zacsweers.metro.AppScope"
    private val metroOrigin: Boolean = options["atom.metro.origin"]?.toBooleanStrictOrNull() ?: true
    private val metroInject: String = options["atom.metro.injectAnnotation"] ?: "dev.zacsweers.metro.Inject"

    private data class GenAtom(
        val atomClass: ClassName,
        val stateType: TypeName,
        val paramsType: TypeName,
        val factoryClass: ClassName,
        val entryClass: ClassName,
        val needsContainer: Boolean,
        val scope: ClassName
    )

    private val generated = mutableListOf<GenAtom>()
    private var registryGenerated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val targets = resolver.getSymbolsWithAnnotation(AutoAtom::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        targets.forEach { decl ->
            try {
                generateForAtom(decl)
            } catch (t: Throwable) {
                logger.error("Atom-KSP error: ${t.message}", decl)
            }
        }

        if (generated.isNotEmpty() && !registryGenerated) {
            // Skip GeneratedAtomRegistry in Metro mode to avoid FIR wildcard issues
            if (diFlavor != "metro") {
                generateRegistry()
            }
            when (diFlavor) {
                "koin" -> generateKoinModule()
                "hilt" -> generateHiltModule()
                "metro" -> {
                    generateMetroContributions()
                    generateMetroAggregatorBindings()
                }
            }
            registryGenerated = true
        }

        return emptyList()
    }

    private fun generateForAtom(decl: KSClassDeclaration) {
        if (!decl.isPublic()) {
            logger.error("@AutoAtom class must be public", decl)
            return
        }
        val atomClass = decl.toClassName()
        if (!decl.isAtomSubclass()) {
            logger.error("@AutoAtom must annotate a subclass of Atom<S,*,*,*>", decl)
            return
        }

        // Read @AutoAtom via KSAnnotation so we can access KClass arguments as KSType
        val autoAtomAnn = decl.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == AutoAtom::class.qualifiedName
        } ?: run {
            logger.error("Missing @AutoAtom", decl)
            return
        }

        // Extract S from Atom<S,*,*,*> supertype
        val superAtom =
            decl.superTypes.firstOrNull { it.resolve().declaration.qualifiedName?.asString() == RTypes.Atom.canonicalName }
                ?: run {
                    logger.error("Unable to resolve Atom base type", decl)
                    return
                }
        val sFromSuper: KSType? = superAtom.resolve().arguments.firstOrNull()?.type?.resolve()
        val sFromSuperName = sFromSuper?.toTypeName()
            ?: run {
                logger.error("Unable to extract state type S from Atom<S,...>", decl)
                return
            }

        // Helper to get annotation argument or infer
        fun argTypeName(name: String): TypeName? {
            val a = autoAtomAnn.arguments.firstOrNull { it.name?.asString() == name }?.value as? KSType
                ?: return null
            val typeName = a.toTypeName()
            // Check if it's the InferredType marker
            if (a.declaration.qualifiedName?.asString() == "dev.mattramotar.atom.runtime.annotations.InferredType") {
                return null // Signal to infer
            }
            return typeName
        }

        // Infer or validate state type
        val stateType: TypeName = argTypeName("state") ?: sFromSuperName

        // If the state was explicitly provided, validate it matches the supertype
        if (argTypeName("state") != null && argTypeName("state") != sFromSuperName) {
            logger.error(
                "State type mismatch: @AutoAtom.state=${argTypeName("state")} but Atom<S,…> declares ${sFromSuperName}. " +
                        "Either remove the explicit state parameter to infer, or ensure they match.",
                decl
            )
            return
        }

        // Infer params type from @InitialState function or use explicit annotation
        val companion = decl.declarations.filterIsInstance<KSClassDeclaration>().firstOrNull { it.isCompanionObject }
        val initialStateFun =
            companion?.getAllFunctions()?.firstOrNull { it.getAnnotationsByType(InitialState::class).any() }

        val paramsFromInitial: TypeName? = initialStateFun?.parameters?.firstOrNull()?.type?.resolve()?.toTypeName()

        val paramsType: TypeName = argTypeName("params") ?: paramsFromInitial ?: UNIT

        // If params was explicitly provided but doesn't match the initial function, warn
        if (argTypeName("params") != null && paramsFromInitial != null && argTypeName("params") != paramsFromInitial) {
            logger.warn(
                "Params type mismatch: @AutoAtom.params=${argTypeName("params")} but initial() declares ${paramsFromInitial}. " +
                        "Using annotation value.",
                decl
            )
        }

        // Choose constructor
        val ctor = decl.primaryConstructor
            ?: run {
                logger.error("Atom must have a primary constructor", decl)
                return
            }

        // Build initial state expression
        val initialBody = when {
            initialStateFun != null -> buildInitialFromCompanion(initialStateFun)
            isUnitType(paramsType) -> CodeBlock.of("%T()", stateType)
            else -> CodeBlock.of("error(%S)", "Missing @Initial and ${stateType}() no-arg constructor")
        }

        // Serializer
        val serializerArg = autoAtomAnn.arguments.firstOrNull { it.name?.asString() == "serializer" }?.value as? KSType
        val isAutoSerializer =
            serializerArg == null || serializerArg.declaration.qualifiedName?.asString() == AutoSerializer::class.qualifiedName

        // Generate serializer class if state is @Serializable
        val generatedSerializerClass = if (isAutoSerializer) {
            generateSerializerIfSerializable(sFromSuper, stateType)
        } else null

        val serializerExpr = when {
            // If we generated a serializer class, use it
            generatedSerializerClass != null -> CodeBlock.of("%T()", generatedSerializerClass)
            // If auto-serializer but state is not @Serializable, use null
            isAutoSerializer -> CodeBlock.of("null")
            // Otherwise use the explicitly provided serializer (guaranteed non-null here)
            else -> CodeBlock.of("%T() as %T?", serializerArg!!.toClassName(), stateType.stateSerializerOf())
        }

        // Extract Metro scope from annotation or use convention plugin default
        val scopeArg = autoAtomAnn.arguments.firstOrNull { it.name?.asString() == "scope" }?.value as? KSType
        val scopeClassName = if (scopeArg != null &&
            scopeArg.declaration.qualifiedName?.asString() != "dev.mattramotar.atom.runtime.annotations.InferredScope"
        ) {
            // Explicit scope provided
            scopeArg.toClassName()
        } else {
            // Use convention plugin's metroScope
            ClassName.bestGuess(metroScope)
        }

        // Build factory class
        val factoryName = atomClass.simpleName + "_Factory"
        val factoryClass = ClassName(pkgGenerated, factoryName)
        val entryClass = ClassName(pkgGenerated, atomClass.simpleName + "_Entry")

        val ctorParams = ctor.parameters
        val assistedKinds = AssistedKinds(stateType, paramsType)

        // Check if a container is needed (only if there are parameters requiring DI resolution)
        val needsContainer = ctorParams.any { requiresDIResolution(it, assistedKinds) }

        // Build factory constructor
        val factoryCtorBuilder = FunSpec.constructorBuilder()
        if (diFlavor == "metro") {
            // Metro: Emit typed deps as constructor params with @Inject (only for params requiring DI)
            ctorParams.filter { requiresDIResolution(it, assistedKinds) }.forEach { p ->
                val pName = p.name?.asString() ?: "dep"
                val pType = p.type.toTypeName()
                val paramBuilder = ParameterSpec.builder(pName, pType)
                // Propagate @AtomQualifier as @Named
                val atomQualifierName = getAtomQualifierName(p)
                if (atomQualifierName != null) {
                    paramBuilder.addAnnotation(
                        AnnotationSpec.builder(ClassName("javax.inject", "Named"))
                            .addMember("%S", atomQualifierName)
                            .build()
                    )
                }
                // Propagate other qualifier annotations if present
                p.annotations.forEach { ann ->
                    val annDecl = ann.annotationType.resolve().declaration
                    val fqName = annDecl.qualifiedName?.asString() ?: return@forEach
                    val isNamed = fqName.endsWith(".Named")
                    val hasQualifierMeta = annDecl.annotations.any { meta ->
                        val mfq = meta.annotationType.resolve().declaration.qualifiedName?.asString()
                        mfq == "javax.inject.Qualifier" || mfq == "jakarta.inject.Qualifier" || mfq == "dagger.Qualifier"
                    }
                    if (isNamed || hasQualifierMeta) {
                        paramBuilder.addAnnotation(AnnotationSpec.builder(ClassName.bestGuess(fqName)).build())
                    }
                }
                factoryCtorBuilder.addParameter(paramBuilder.build())
            }
            factoryCtorBuilder.addAnnotation(ClassName.bestGuess(metroInject))
        } else if (needsContainer) {
            factoryCtorBuilder.addParameter("container", RTypes.AtomContainer)
        }

        val factoryTypeBuilder = TypeSpec.classBuilder(factoryClass)
            .addModifiers(KModifier.PUBLIC)
            .primaryConstructor(factoryCtorBuilder.build())

        if (diFlavor == "metro") {
            // Store typed deps as properties (only for params requiring DI)
            ctorParams.filter { requiresDIResolution(it, assistedKinds) }.forEach { p ->
                val pName = p.name?.asString() ?: "dep"
                factoryTypeBuilder.addProperty(
                    PropertySpec.builder(pName, p.type.toTypeName())
                        .initializer(pName)
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )
            }
        } else if (needsContainer) {
            // Only add container property if needed
            factoryTypeBuilder.addProperty(
                PropertySpec.Companion.builder("container", RTypes.AtomContainer).initializer("container")
                    .addModifiers(KModifier.PRIVATE).build()
            )
        }

        val factoryType = factoryTypeBuilder
            .addSuperinterface(
                RTypes.AtomFactory.parameterizedBy(
                    atomClass,
                    stateType,
                    paramsType
                )
            )
            .addProperty(
                PropertySpec.Companion.builder("atomClass", atomClass.kclassOf()).initializer("%T::class", atomClass)
                    .addModifiers(KModifier.OVERRIDE).build()
            )
            .addProperty(
                PropertySpec.builder("stateClass", stateType.kclassOf()).initializer("%T::class", stateType)
                    .addModifiers(KModifier.OVERRIDE).build()
            )
            .addProperty(
                PropertySpec.builder("paramsClass", paramsType.kclassOf()).initializer("%T::class", paramsType)
                    .addModifiers(KModifier.OVERRIDE).build()
            )
            .addProperty(
                PropertySpec.builder("serializer", stateType.stateSerializerOf().copy(nullable = true))
                    .initializer(serializerExpr)
                    .addModifiers(KModifier.OVERRIDE)
                    .build()
            )
            .addFunction(
                FunSpec.builder("initial")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(ParameterSpec.builder("params", paramsType).build())
                    .returns(stateType)
                    .addCode("return %L\n", initialBody)
                    .build()
            )
            .addFunction(
                FunSpec.builder("create")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(ParameterSpec.Companion.builder("scope", RTypes.CoroutineScope).build())
                    .addParameter(ParameterSpec.Companion.builder("handle", stateType.stateHandleOf()).build())
                    .addParameter(ParameterSpec.builder("params", paramsType).build())
                    .returns(atomClass)
                    .addCode(
                        buildCreateCall(
                            atomClass,
                            ctorParams,
                            assistedKinds,
                            useTypedFactoryParams = diFlavor == "metro"
                        )
                    )
                    .build()
            )
            .build()

        FileSpec.builder(pkgGenerated, factoryName)
            .addFileComment("Generated by Atom-KSP. Do not edit.")
            .addImport("dev.mattramotar.atom.runtime.di", "resolve")
            .addType(factoryType)
            .build()
            .writeTo(codegen, aggregating = true)

        // Bridge entry
        val entryType = TypeSpec.classBuilder(entryClass)
            .addModifiers(KModifier.PUBLIC)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("f", factoryClass).build())
            .addProperty(
                PropertySpec.builder("f", factoryClass).initializer("f").addModifiers(KModifier.PRIVATE).build()
            )
            .superclass(RTypes.AnyAtomFactoryEntry.parameterizedBy(atomClass))
            .addProperty(
                PropertySpec.builder("atomClass", atomClass.kclassOf()).initializer("f.atomClass")
                    .addModifiers(KModifier.OVERRIDE).build()
            )
            .addProperty(
                PropertySpec.builder("stateClass", stateType.kclassOf()).initializer("f.stateClass")
                    .addModifiers(KModifier.OVERRIDE).build()
            )
            .addProperty(
                PropertySpec.builder("paramsClass", paramsType.kclassOf()).initializer("f.paramsClass")
                    .addModifiers(KModifier.OVERRIDE).build()
            )
            .addProperty(
                PropertySpec.builder("serializerAny", RTypes.StateSerializer.parameterizedBy(ANY).copy(nullable = true))
                    .initializer("f.serializer as %T?", RTypes.StateSerializer.parameterizedBy(ANY))
                    .addModifiers(KModifier.OVERRIDE)
                    .build()
            )
            .addFunction(
                FunSpec.builder("initialAny")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("params", ANY)
                    .returns(ANY)
                    .addCode("return f.initial(params as %T)\n", paramsType)
                    .build()
            )
            .addFunction(
                FunSpec.builder("createAny")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("scope", RTypes.CoroutineScope)
                    .addParameter("state", RTypes.StateHandle.parameterizedBy(ANY))
                    .addParameter("params", ANY)
                    .returns(atomClass)
                    .addCode(
                        "return f.create(scope, state as %T, params as %T)\n",
                        stateType.stateHandleOf(),
                        paramsType
                    )
                    .build()
            )
            .build()

        FileSpec.builder(pkgGenerated, entryClass.simpleName)
            .addFileComment("Generated by Atom-KSP. Do not edit.")
            .addType(entryType)
            .build()
            .writeTo(codegen, aggregating = true)

        generated += GenAtom(atomClass, stateType, paramsType, factoryClass, entryClass, needsContainer, scopeClassName)

        if (composeExtensions) {
            generateComposeHelper(atomClass, paramsType)
        }
    }

    private fun generateComposeHelper(atomClass: ClassName, paramsType: TypeName) {
        val file = FileSpec.builder(pkgGenerated, "${atomClass.simpleName}_Compose")
            .addFileComment("Generated by Atom-KSP. Do not edit.")
            .addFunction(
                FunSpec.builder("remember${atomClass.simpleName}")
                    .addAnnotation(ClassName("androidx.compose.runtime", "Composable"))
                    .returns(atomClass)
                    .addParameter(ParameterSpec.builder("key", ANY.copy(nullable = true)).defaultValue("null").build())
                    .addParameter(
                        ParameterSpec.builder("params", paramsType)
                            .defaultValue(if (isUnitType(paramsType)) "Unit" else "throw IllegalStateException(\"params required\")")
                            .build()
                    )
                    .addCode(
                        CodeBlock.of(
                            "return dev.mattramotar.atom.runtime.compose.atom<%T>(key, params)\n",
                            atomClass
                        )
                    )
                    .build()
            )
            .build()
        file.writeTo(codegen, aggregating = true)
    }

    private fun generateRegistry() {
        val entryType =
            RTypes.AnyAtomFactoryEntry.parameterizedBy(WildcardTypeName.Companion.producerOf(RTypes.AtomLifecycle))
        val keyType = RTypes.KClassClass.parameterizedBy(WildcardTypeName.Companion.producerOf(RTypes.AtomLifecycle))
        val mapType = ClassName("kotlin.collections", "Map")
            .parameterizedBy(keyType, entryType)

        val type = TypeSpec.Companion.classBuilder(Names.REGISTRY)
            .addSuperinterface(RTypes.AtomFactoryRegistry)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec.Companion.builder("container", RTypes.AtomContainer).build())
                    .build()
            )
            .addProperty(
                PropertySpec.builder(
                    "entries",
                    mapType,
                    KModifier.PRIVATE
                ).initializer(CodeBlock.builder().apply {
                    add("mapOf(\n")
                    generated.forEachIndexed { i, g ->
                        val factoryInit = if (g.needsContainer) "%T(container)" else "%T()"
                        add("  %T::class to %T($factoryInit)", g.atomClass, g.entryClass, g.factoryClass)
                        if (i != generated.lastIndex) add(",\n") else add("\n")
                    }
                    add(")")
                }.build()).build()
            )
            .addFunction(
                FunSpec.builder("entryFor")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(
                        ParameterSpec.builder(
                            "type",
                            keyType
                        ).build()
                    )
                    .returns(entryType.copy(nullable = true))
                    .addCode("return entries[type]\n")
                    .build()
            )
            .build()

        FileSpec.builder(pkgGenerated, Names.REGISTRY)
            .addFileComment("Generated by Atom-KSP. Do not edit.")
            .addType(type)
            .build()
            .writeTo(codegen, aggregating = true)
    }

    private fun generateKoinModule() {
        val moduleFn = FunSpec.builder("Atom_Koin_Module")
            .returns(ClassName("org.koin.core.module", "Module"))
            .addCode("return org.koin.dsl.module {\n")
            .apply {
                generated.forEach { g ->
                    val factoryInit = if (g.needsContainer) "%T(get())" else "%T()"
                    addCode(
                        "  single<%T<out %T>>(org.koin.core.qualifier.named(%T::class.qualifiedName!!)) { %T($factoryInit) }\n",
                        RTypes.AnyAtomFactoryEntry, RTypes.AtomLifecycle, g.atomClass, g.entryClass, g.factoryClass
                    )
                }
            }
            .addCode("}\n")
            .build()

        FileSpec.builder(pkgGenerated, "Atom_Koin_Module")
            .addFileComment("Generated by Atom-KSP. Do not edit.")
            .addFunction(moduleFn)
            .build()
            .writeTo(codegen, aggregating = true)
    }

    private fun generateHiltModule() {
        // Android-only generation
        // Safe to emit regardless, Android compilation will pick it up when present
        val moduleType = TypeSpec.objectBuilder("Atom_Hilt_Module")
            .addAnnotation(ClassName("dagger", "Module"))
            .addAnnotation(
                AnnotationSpec.builder(ClassName("dagger.hilt", "InstallIn"))
                    .addMember("%T::class", ClassName("dagger.hilt.components", "SingletonComponent"))
                    .build()
            )
            .apply {
                generated.forEach { g ->
                    val fnBuilder = FunSpec.builder("provide${g.atomClass.simpleName}")
                        .addAnnotation(ClassName("dagger", "Provides"))
                        .addAnnotation(ClassName("dagger.multibindings", "IntoMap"))
                        .addAnnotation(
                            AnnotationSpec.builder(ClassName("dagger.multibindings", "ClassKey"))
                                .addMember("%T::class", g.atomClass)
                                .build()
                        )
                        .returns(RTypes.AnyAtomFactoryEntry.parameterizedBy(RTypes.AtomLifecycle))

                    if (g.needsContainer) {
                        fnBuilder.addParameter(
                            ParameterSpec.Companion.builder("container", RTypes.AtomContainer).build()
                        )
                        fnBuilder.addCode("return %T(%T(container))\n", g.entryClass, g.factoryClass)
                    } else {
                        fnBuilder.addCode("return %T(%T())\n", g.entryClass, g.factoryClass)
                    }

                    addFunction(fnBuilder.build())
                }
            }
            .build()

        FileSpec.builder(pkgGenerated, "Atom_Hilt_Module")
            .addFileComment("Generated by Atom-KSP. Do not edit.")
            .addType(moduleType)
            .build()
            .writeTo(codegen, aggregating = true)
    }

    private fun generateMetroContributions() {
        val injectClassName = ClassName.bestGuess(metroInject)

        generated.forEach { g ->
            // Build the Metro-annotated entry class
            val metroEntryName = "${g.atomClass.simpleName}_MetroEntry"
            val metroEntryClass = ClassName(pkgGenerated, metroEntryName)

            val typeBuilder = TypeSpec.classBuilder(metroEntryClass)
                .addModifiers(KModifier.PUBLIC)

            // Add @ContributesIntoMap annotation with per-atom scope
            val contributeAnnotation = AnnotationSpec.Companion.builder(RTypes.ContributesIntoMap)
                .addMember("scope = %T::class", g.scope)
                .build()
            typeBuilder.addAnnotation(contributeAnnotation)

            // Add @ClassKey annotation
            val classKeyAnnotation = AnnotationSpec.Companion.builder(RTypes.ClassKey)
                .addMember("%T::class", g.atomClass)
                .build()
            typeBuilder.addAnnotation(classKeyAnnotation)

            // Add @Origin if enabled
            if (metroOrigin) {
                val originAnnotation = AnnotationSpec.Companion.builder(RTypes.Origin)
                    .addMember("%T::class", g.atomClass)
                    .build()
                typeBuilder.addAnnotation(originAnnotation)
            }

            // Collect constructor parameters from Atom's primary constructor
            // We need to read the constructor parameters to inject them
            // For now, we'll inject the factory's dependencies directly
            // This requires collecting the non-assisted parameters from the Atom constructor

            // Add constructor with @Inject
            val ctorBuilder = FunSpec.constructorBuilder()

            // For simplicity, we inject the factory directly
            // In a more complete implementation, we'd inject the factory's dependencies
            ctorBuilder.addParameter("factory", g.factoryClass)
            ctorBuilder.addAnnotation(injectClassName)

            typeBuilder.primaryConstructor(ctorBuilder.build())

            // Add factory property
            typeBuilder.addProperty(
                PropertySpec.builder("factory", g.factoryClass)
                    .initializer("factory")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )

            // Implement Provider interface
            val providerType = ClassName("dev.zacsweers.metro", "Provider")
                .parameterizedBy(RTypes.AnyAtomFactoryEntry.parameterizedBy(g.atomClass))
            typeBuilder.addSuperinterface(providerType)

            // Add invoke() method
            typeBuilder.addFunction(
                FunSpec.builder("invoke")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(RTypes.AnyAtomFactoryEntry.parameterizedBy(g.atomClass))
                    .addCode("return %T(factory)\n", g.entryClass)
                    .build()
            )

            val fileSpec = FileSpec.builder(pkgGenerated, metroEntryName)
                .addFileComment("Generated by Atom-KSP. Do not edit.")
                .addType(typeBuilder.build())
                .build()

            fileSpec.writeTo(codegen, aggregating = true)
        }
    }

    private fun generateMetroAggregatorBindings() {
        val metroRegistry = ClassName("dev.mattramotar.atom.metro", "MetroAtomFactoryRegistry")
        val providerType = ClassName("dev.zacsweers.metro", "Provider")

        val anyEntryOut =
            RTypes.AnyAtomFactoryEntry.parameterizedBy(WildcardTypeName.Companion.producerOf(RTypes.AtomLifecycle))
        val providerOut = providerType.parameterizedBy(anyEntryOut)

        // Group atoms by scope
        val atomsByScope = generated.groupBy { it.scope }

        // Generate one binding object per scope
        atomsByScope.forEach { (scope, atoms) ->
            val scopeSimpleName = scope.simpleName
            val bindingsName = "AtomMetroGeneratedBindings_$scopeSimpleName"

            val type = TypeSpec.objectBuilder(bindingsName)
                .addAnnotation(RTypes.BindingContainer)
                .addAnnotation(
                    AnnotationSpec.Companion.builder(RTypes.ContributesTo)
                        .addMember("%T::class", scope)
                        .build()
                )
                .addFunction(
                    FunSpec.builder("provideAtomFactoryRegistry")
                        .addAnnotation(RTypes.Provides)
                        .addAnnotation(
                            AnnotationSpec.Companion.builder(RTypes.SingleIn)
                                .addMember("%T::class", scope)
                                .build()
                        )
                        .returns(RTypes.AtomFactoryRegistry)
                        .apply {
                            // Inject each MetroEntry for this scope individually
                            atoms.forEach { g ->
                                val paramName = g.atomClass.simpleName.replaceFirstChar { it.lowercase() } + "Entry"
                                val metroEntryClass = ClassName(pkgGenerated, g.atomClass.simpleName + "_MetroEntry")
                                addParameter(paramName, metroEntryClass)
                            }

                            // Build the map construction code
                            val code = CodeBlock.builder().apply {
                                if (atoms.isEmpty()) {
                                    add("return %T(emptyMap())\n", metroRegistry)
                                } else {
                                    add("return %T(mapOf(\n", metroRegistry)
                                    atoms.forEachIndexed { i, g ->
                                        val paramName =
                                            g.atomClass.simpleName.replaceFirstChar { it.lowercase() } + "Entry"
                                        add("  %T::class to (%N as %T)", g.atomClass, paramName, providerOut)
                                        if (i != atoms.lastIndex) add(",\n") else add("\n")
                                    }
                                    add("))\n")
                                }
                            }.build()
                            addCode(code)
                        }
                        .build()
                )
                .build()

            FileSpec.builder(pkgGenerated, bindingsName)
                .addFileComment("Generated by Atom-KSP. Do not edit.")
                .addType(type)
                .build()
                .writeTo(codegen, aggregating = true)
        }
    }

    private fun buildCreateCall(
        atomClass: ClassName,
        ctorParams: List<KSValueParameter>,
        assisted: AssistedKinds,
        useTypedFactoryParams: Boolean
    ): CodeBlock {
        val argParts = mutableListOf<String>()
        var hasSkippedDefault = false

        ctorParams.forEach { p ->
            val pName = p.name?.asString() ?: "dep"

            // Skip parameters with defaults (unless they have @AtomQualifier)
            val shouldSkip = !assisted.isAssisted(p) && p.hasDefault && !hasAtomQualifier(p)

            if (shouldSkip) {
                hasSkippedDefault = true
                return@forEach
            }

            // Use named args if we've skipped a default (Kotlin requires all args to be named after a default is skipped)
            val useNamed = hasSkippedDefault

            val argValue = when {
                assisted.isScope(p) -> "scope"
                assisted.isHandle(p) -> "handle"
                assisted.isParams(p) -> "params"
                useTypedFactoryParams -> pName
                else -> {
                    val qualifierName = getAtomQualifierName(p)
                    if (qualifierName != null) {
                        val escaped = qualifierName.replace("\\", "\\\\").replace("\"", "\\\"").replace("\$", "\\\$")
                        "container.resolve<${p.type.toTypeName()}>(\"$escaped\")"
                    } else {
                        "container.resolve<${p.type.toTypeName()}>()"
                    }
                }
            }

            argParts.add(if (useNamed) "$pName = $argValue" else argValue)
        }

        return CodeBlock.of("return %T(${argParts.joinToString(", ")})\n", atomClass)
    }

    private class AssistedKinds(private val state: TypeName, private val params: TypeName) {
        fun isAssisted(p: KSValueParameter): Boolean = isScope(p) || isHandle(p) || isParams(p)
        fun isScope(p: KSValueParameter): Boolean =
            p.type.resolve().declaration.qualifiedName?.asString() == RTypes.CoroutineScope.canonicalName

        fun isHandle(p: KSValueParameter): Boolean = p.type.toTypeName() == state.stateHandleOf()
        fun isParams(p: KSValueParameter): Boolean = p.type.toTypeName() == params
    }

    /**
     * Checks if a parameter has an @AtomQualifier annotation.
     * When present, the parameter should be resolved via DI even if it has a default value.
     */
    private fun hasAtomQualifier(p: KSValueParameter): Boolean {
        return p.annotations.any { ann ->
            ann.annotationType.resolve().declaration.qualifiedName?.asString() ==
                "dev.mattramotar.atom.runtime.annotations.AtomQualifier"
        }
    }

    /**
     * Extracts the name value from @AtomQualifier annotation if present.
     */
    private fun getAtomQualifierName(p: KSValueParameter): String? {
        val ann = p.annotations.firstOrNull { ann ->
            ann.annotationType.resolve().declaration.qualifiedName?.asString() ==
                "dev.mattramotar.atom.runtime.annotations.AtomQualifier"
        } ?: return null

        return ann.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String
    }

    /**
     * Determines if a parameter requires DI resolution.
     * A parameter requires DI if:
     * - It is NOT an assisted parameter (scope, handle, params)
     * - AND either has @AtomQualifier OR does not have a default value
     */
    private fun requiresDIResolution(p: KSValueParameter, assistedKinds: AssistedKinds): Boolean {
        if (assistedKinds.isAssisted(p)) return false

        // @AtomQualifier explicitly requests DI resolution, even with a default
        if (hasAtomQualifier(p)) return true

        // Otherwise, require DI only if no default value
        return !p.hasDefault
    }

    private fun KSClassDeclaration.isAtomSubclass(): Boolean =
        superTypes.any { it.resolve().declaration.qualifiedName?.asString() == RTypes.Atom.canonicalName }

    private fun buildInitialFromCompanion(initialFun: KSFunctionDeclaration): CodeBlock {
        val fnName = initialFun.simpleName.asString()
        val recv = (initialFun.parentDeclaration as? KSClassDeclaration)?.toClassName()?.canonicalName
        val hasParam = initialFun.parameters.size == 1
        return if (hasParam) CodeBlock.of("%L.%L(params)", recv, fnName) else CodeBlock.of("%L.%L()", recv, fnName)
    }

    /**
     * Generates a StateSerializer implementation for @Serializable state types.
     * Returns the ClassName of the generated serializer, or null if state is not @Serializable.
     */
    private fun generateSerializerIfSerializable(stateType: KSType, stateTypeName: TypeName): ClassName? {
        // Check if state type has @Serializable annotation
        val isSerializable = stateType.declaration.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == "kotlinx.serialization.Serializable"
        }

        if (!isSerializable) {
            return null
        }

        // Generate serializer class name
        val stateSimpleName = (stateTypeName as? ClassName)?.simpleName
            ?: stateTypeName.toString().substringAfterLast('.')
        val serializerClassName = ClassName(pkgGenerated, "${stateSimpleName}_KotlinxSerializer")

        // Generate the serializer class
        val jsonType = ClassName("kotlinx.serialization.json", "Json")

        val serializerType = TypeSpec.classBuilder(serializerClassName)
            .addModifiers(KModifier.PUBLIC)
            .addSuperinterface(RTypes.StateSerializer.parameterizedBy(stateTypeName))
            .addProperty(
                PropertySpec.builder("json", jsonType, KModifier.PRIVATE)
                    .initializer(
                        CodeBlock.builder()
                            .add("%T {\n", jsonType)
                            .indent()
                            .addStatement("ignoreUnknownKeys = true")
                            .addStatement("encodeDefaults = true")
                            .unindent()
                            .add("}")
                            .build()
                    )
                    .build()
            )
            .addFunction(
                FunSpec.builder("serialize")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", stateTypeName)
                    .returns(String::class)
                    .addStatement("return json.encodeToString(%T.serializer(), value)", stateTypeName)
                    .build()
            )
            .addFunction(
                FunSpec.builder("deserialize")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("text", String::class)
                    .returns(stateTypeName)
                    .addStatement("return json.decodeFromString(%T.serializer(), text)", stateTypeName)
                    .build()
            )
            .build()

        FileSpec.builder(pkgGenerated, serializerClassName.simpleName)
            .addFileComment("Generated by Atom-KSP. Do not edit.")
            .addType(serializerType)
            .build()
            .writeTo(codegen, aggregating = true)

        return serializerClassName
    }

    private fun isUnitType(t: TypeName): Boolean = t.toString() == "kotlin.Unit"
}
