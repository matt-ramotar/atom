package dev.mattramotar.atom.ksp

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import kotlin.reflect.KClass

internal object RTypes {
    // Packages
    private const val P_ATOM = "dev.mattramotar.atom.runtime"
    private const val P_ATOM_ANNOTATIONS = "$P_ATOM.annotations"
    private const val P_ATOM_CHILD = "$P_ATOM.child"
    private const val P_ATOM_COMPOSE = "$P_ATOM.compose"
    private const val P_ATOM_FACTORY = "$P_ATOM.factory"
    private const val P_ATOM_FSM = "$P_ATOM.fsm"
    private const val P_ATOM_SERIALIZATION = "$P_ATOM.serialization"
    private const val P_ATOM_STATE = "$P_ATOM.state"
    private const val P_ATOM_STORE = "$P_ATOM.store"
    private const val P_DI = "dev.mattramotar.atom.di"
    private const val P_METRO = "dev.zacsweers.metro"

    // Runtime classes
    val AtomLifecycle = ClassName(P_ATOM, "AtomLifecycle")
    val Atom = ClassName(P_ATOM, "Atom")
    val AtomFactory = ClassName(P_ATOM_FACTORY, "AtomFactory")
    val AnyAtomFactoryEntry = ClassName(P_ATOM_FACTORY, "AnyAtomFactoryEntry")
    val AtomFactoryRegistry = ClassName(P_ATOM_FACTORY, "AtomFactoryRegistry")
    val StateHandle = ClassName(P_ATOM_STATE, "StateHandle")
    val StateSerializer = ClassName(P_ATOM_SERIALIZATION, "StateSerializer")
    val KotlinxStateSerializer = ClassName(P_ATOM_SERIALIZATION, "KotlinxStateSerializer")

    // DI adapter
    val AtomContainer = ClassName(P_DI, "AtomContainer")

    // Metro annotations
    val ContributesIntoMap = ClassName(P_METRO, "ContributesIntoMap")
    val ContributesTo = ClassName(P_METRO, "ContributesTo")
    val BindingContainer = ClassName(P_METRO, "BindingContainer")
    val Provides = ClassName(P_METRO, "Provides")
    val SingleIn = ClassName(P_METRO, "SingleIn")
    val ClassKey = ClassName(P_METRO, "ClassKey")
    val Origin = ClassName(P_METRO, "Origin")

    // Kotlin types
    val KClassClass = KClass::class.asClassName()
    val CoroutineScope = ClassName("kotlinx.coroutines", "CoroutineScope")
    val UnitClass = Unit::class.asClassName()
}

internal object Names {
    const val GENERATED_PACKAGE = "dev.mattramotar.atom.generated"
    const val REGISTRY = "GeneratedAtomRegistry"
}

internal fun TypeName.kclassOf() = RTypes.KClassClass.parameterizedBy(this)
internal fun TypeName.stateHandleOf() = RTypes.StateHandle.parameterizedBy(this)
internal fun TypeName.stateSerializerOf() = RTypes.StateSerializer.parameterizedBy(this)

internal val public = arrayOf(KModifier.PUBLIC)


