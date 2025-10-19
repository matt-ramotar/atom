package dev.mattramotar.atom.runtime.factory

import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.serialization.StateSerializer
import dev.mattramotar.atom.runtime.state.StateHandle
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/**
 * Type-erased factory entry for runtime atom resolution.
 *
 * [AnyAtomFactoryEntry] provides a type-erased interface for atom factories, enabling storage
 * in heterogeneous collections (like [AtomFactoryRegistry]) and runtime atom creation without
 * compile-time type information.
 *
 * This abstract class bridges the gap between:
 * - **Compile-time typed factories**: [AtomFactory]<A, S, P>
 * - **Runtime type-erased resolution**: Looking up factories by `KClass<out AtomLifecycle>`
 *
 * ## Type Erasure
 *
 * [AnyAtomFactoryEntry] erases the specific types `A`, `S`, and `P` to `Any`:
 * - [initialAny]: `(Any) → Any` instead of `(P) → S`
 * - [createAny]: `(CoroutineScope, StateHandle<Any>, Any) → A` instead of typed version
 * - [serializerAny]: `StateSerializer<Any>?` instead of `StateSerializer<S>?`
 *
 * This allows storing all factories in a single `Map<KClass<out AtomLifecycle>, AnyAtomFactoryEntry>`
 * without losing access to factory capabilities.
 *
 * ## Implementation Pattern
 *
 * [AnyAtomFactoryEntry] is abstract - the concrete implementation is [AtomFactoryEntry], which
 * provides type-safe wrappers around typed factory methods:
 *
 * ```kotlin
 * abstract class AnyAtomFactoryEntry<A : AtomLifecycle> {
 *     abstract fun initialAny(params: Any): Any
 *     abstract fun createAny(scope: CoroutineScope, state: StateHandle<Any>, params: Any): A
 * }
 *
 * class AtomFactoryEntry<A, S, P>(...) : AnyAtomFactoryEntry<A>() {
 *     override fun initialAny(params: Any): Any = initial(params as P)  // Unchecked cast
 *     override fun createAny(...): A = create(scope, state as StateHandle<S>, params as P)
 * }
 * ```
 *
 * ## Usage in AtomStore
 *
 * The atom runtime uses [AnyAtomFactoryEntry] for type-erased atom creation:
 *
 * ```kotlin
 * class AtomStore {
 *     fun <A : AtomLifecycle> acquire(key: AtomKey, create: () -> Pair<A, Job?>): A {
 *         val entry: AnyAtomFactoryEntry<out AtomLifecycle> = registry.entryFor(key.type)
 *             ?: error("No factory for ${key.type}")
 *
 *         val state: StateHandle<Any> = stateHandleFactory.create(
 *             key = key,
 *             stateClass = entry.stateClass as KClass<Any>,
 *             initial = { entry.initialAny(params) },
 *             serializer = entry.serializerAny
 *         )
 *
 *         @Suppress("UNCHECKED_CAST")
 *         val atom = entry.createAny(scope, state, params) as A
 *         return atom
 *     }
 * }
 * ```
 *
 * ## Runtime Type Safety
 *
 * While type-erased, [AnyAtomFactoryEntry] maintains runtime type safety through:
 * - [atomClass]: Validates the requested atom type matches the factory
 * - [stateClass]: Validates state types for state handle creation
 * - [paramsClass]: Validates params types before calling [initialAny] or [createAny]
 *
 * Example validation:
 * ```kotlin
 * val entry = registry.entryFor(TodoAtom::class)
 * require(entry.paramsClass == TodoAtomParams::class) {
 *     "Expected ${entry.paramsClass.simpleName} but got ${params::class.simpleName}"
 * }
 * val state = entry.initialAny(params)  // Safe: params type validated
 * ```
 *
 * ## Relationship to AtomFactoryEntry
 *
 * [AnyAtomFactoryEntry] is the abstract parent of [AtomFactoryEntry]:
 *
 * ```
 * AnyAtomFactoryEntry<A> (type-erased methods)
 *        ↑
 *        │ extends
 *        │
 * AtomFactoryEntry<A, S, P> (adds typed methods, delegates to type-erased)
 *        ↑
 *        │ implements
 *        │
 * Generated factories (TodoAtom_Factory, etc.)
 * ```
 *
 * ## Why Abstract?
 *
 * [AnyAtomFactoryEntry] is abstract because it doesn't know how to implement type erasure - that's
 * the responsibility of [AtomFactoryEntry], which has access to the typed methods.
 *
 * @param A The atom type (preserved in the type parameter for some type safety)
 *
 * @property atomClass The atom's `KClass` for runtime type validation
 * @property stateClass The state's `KClass` (erased to `KClass<out Any>`)
 * @property paramsClass The params' `KClass` (erased to `KClass<out Any>`)
 * @property serializerAny Type-erased state serializer
 *
 * @see AtomFactoryEntry for the concrete implementation
 * @see AtomFactoryRegistry for factory lookup by atom type
 */
abstract class AnyAtomFactoryEntry<A : AtomLifecycle> {
    abstract val atomClass: KClass<A>
    abstract val stateClass: KClass<out Any>
    abstract val paramsClass: KClass<out Any>
    abstract val serializerAny: StateSerializer<Any>?

    /**
     * Computes initial state from type-erased params.
     *
     * @param params The initialization parameters (type-erased to `Any`)
     * @return The initial state (type-erased to `Any`)
     */
    abstract fun initialAny(params: Any): Any

    /**
     * Creates an atom instance from type-erased state handle and params.
     *
     * @param scope The coroutine scope for the atom
     * @param state The state handle (type-erased to `StateHandle<Any>`)
     * @param params The initialization parameters (type-erased to `Any`)
     * @return The created atom instance
     */
    abstract fun createAny(scope: CoroutineScope, state: StateHandle<Any>, params: Any): A
}
