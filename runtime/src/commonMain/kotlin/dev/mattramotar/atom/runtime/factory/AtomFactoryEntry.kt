package dev.mattramotar.atom.runtime.factory

import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.serialization.StateSerializer
import dev.mattramotar.atom.runtime.state.StateHandle
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/**
 * Concrete implementation of [AnyAtomFactoryEntry] with type-safe factory methods.
 *
 * [AtomFactoryEntry] serves as the base class for all generated atom factories, providing:
 * - Typed factory methods via constructor parameters
 * - Type-erased implementations via [AnyAtomFactoryEntry]
 * - Type metadata for runtime resolution
 *
 * This class bridges compile-time type safety (via typed lambdas) with runtime type erasure
 * (via [AnyAtomFactoryEntry]), enabling flexible atom creation patterns.
 *
 * ## Constructor Parameters
 *
 * [AtomFactoryEntry] is constructed with lambdas that capture the atom's creation logic:
 * - [atomClass]: The atom's `KClass<A>`
 * - [typedStateClass]: The state's `KClass<S>`
 * - [paramsClass]: The params' `KClass<P>`
 * - [create]: Lambda that creates an atom: `(CoroutineScope, StateHandle<S>, P) → A`
 * - [initial]: Lambda that computes initial state: `(P) → S`
 * - [serializer]: Optional state serializer: `StateSerializer<S>?`
 *
 * ## Generated Usage
 *
 * The KSP processor generates [AtomFactoryEntry] instances via [atomFactory] builder:
 *
 * ```kotlin
 * val TodoAtom_Entry = atomFactory<TodoAtom, TodoState, TodoAtomParams>(
 *     create = { scope, handle, params -> TodoAtom(scope, handle, params, repository) },
 *     initial = { params -> TodoAtom.initial(params) },
 *     serializer = KotlinxStateSerializer(TodoState.serializer())
 * )
 * ```
 *
 * Expands to:
 * ```kotlin
 * val TodoAtom_Entry = AtomFactoryEntry(
 *     atomClass = TodoAtom::class,
 *     typedStateClass = TodoState::class,
 *     paramsClass = TodoAtomParams::class,
 *     create = { scope, handle, params -> TodoAtom(scope, handle, params, repository) },
 *     initial = { params -> TodoAtom.initial(params) },
 *     serializer = KotlinxStateSerializer(TodoState.serializer())
 * )
 * ```
 *
 * ## Type Erasure Implementation
 *
 * [AtomFactoryEntry] implements [AnyAtomFactoryEntry]'s type-erased methods by delegating to
 * the typed lambdas with unchecked casts:
 *
 * ```kotlin
 * override fun initialAny(params: Any): Any {
 *     @Suppress("UNCHECKED_CAST")
 *     return initial(params as P)  // Type-erased → typed
 * }
 *
 * override fun createAny(scope: CoroutineScope, state: StateHandle<Any>, params: Any): A {
 *     @Suppress("UNCHECKED_CAST")
 *     return create(scope, state as StateHandle<S>, params as P)
 * }
 * ```
 *
 * The runtime validates types before calling these methods, ensuring the casts are safe.
 *
 * ## Serializer Type Erasure
 *
 * The serializer is also type-erased from `StateSerializer<S>` to `StateSerializer<Any>`:
 *
 * ```kotlin
 * override val serializerAny: StateSerializer<Any>?
 *     get() = serializer as StateSerializer<Any>?
 * ```
 *
 * This is safe because serialization operates on the concrete state type `S` at runtime.
 *
 * ## Thread Safety
 *
 * [AtomFactoryEntry] is immutable and thread-safe:
 * - All properties are `val` and set via constructor
 * - Lambdas are typically stateless (or capture immutable state)
 * - Safe to share across threads and call concurrently
 *
 * ## Extension Point
 *
 * [AtomFactoryEntry] is `open`, allowing custom factory implementations to extend it:
 *
 * ```kotlin
 * class CustomTodoAtom_Entry(
 *     container: AtomContainer
 * ) : AtomFactoryEntry<TodoAtom, TodoState, TodoAtomParams>(
 *     atomClass = TodoAtom::class,
 *     typedStateClass = TodoState::class,
 *     paramsClass = TodoAtomParams::class,
 *     create = { scope, handle, params ->
 *         val repository = container.resolve<TodoRepository>()
 *         TodoAtom(scope, handle, params, repository)
 *     },
 *     initial = { params -> TodoAtom.initial(params) }
 * )
 * ```
 *
 * @param A The atom type
 * @param S The state type
 * @param P The params type
 * @param atomClass The atom's `KClass<A>`
 * @param typedStateClass The state's `KClass<S>`
 * @param paramsClass The params' `KClass<P>`
 * @param create Lambda that creates the atom
 * @param initial Lambda that computes initial state
 * @param serializer Optional state serializer
 *
 * @see AnyAtomFactoryEntry for the type-erased parent
 * @see atomFactory for the builder function used by generated code
 * @see AtomFactoryRegistry for factory registration
 */
open class AtomFactoryEntry<A : AtomLifecycle, S : Any, P : Any>(
    override val atomClass: KClass<A>,
    val typedStateClass: KClass<S>,
    override val paramsClass: KClass<P>,
    private val create: (CoroutineScope, StateHandle<S>, P) -> A,
    private val initial: (P) -> S,
    private val serializer: StateSerializer<S>? = null
) : AnyAtomFactoryEntry<A>() {
    override val stateClass: KClass<out Any> get() = typedStateClass

    @Suppress("UNCHECKED_CAST")
    override val serializerAny: StateSerializer<Any>?
        get() = serializer as StateSerializer<Any>?

    @Suppress("UNCHECKED_CAST")
    override fun initialAny(params: Any): Any = initial(params as P)

    @Suppress("UNCHECKED_CAST")
    override fun createAny(scope: CoroutineScope, state: StateHandle<Any>, params: Any): A =
        create(scope, state as StateHandle<S>, params as P)
}
