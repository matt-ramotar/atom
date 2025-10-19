package dev.mattramotar.atom.runtime.factory

import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.serialization.StateSerializer
import dev.mattramotar.atom.runtime.state.StateHandle
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/**
 * Strongly typed factory interface for creating atom instances.
 *
 * [AtomFactory] provides a type-safe abstraction for atom creation, encapsulating:
 * - Initial state computation
 * - Atom instance construction
 * - Type metadata for runtime resolution
 * - Optional state serialization
 *
 * The KSP processor generates implementations of this interface for each `@AutoAtom`-annotated class,
 * enabling type-safe, compile-time validated atom creation with dependency injection support.
 *
 * ## Type Parameters
 *
 * - **A**: The atom type (extends [AtomLifecycle])
 * - **S**: The state type (must be `Any`)
 * - **P**: The params type (must be `Any`, use `Unit` for param-less atoms)
 *
 * ## Generated Implementation
 *
 * For an atom `TodoAtom`:
 *
 * ```kotlin
 * @AutoAtom
 * class TodoAtom(
 *     scope: CoroutineScope,
 *     handle: StateHandle<TodoState>,
 *     params: TodoAtomParams,
 *     repository: TodoRepository
 * ) : Atom<TodoState, TodoIntent, TodoEvent, TodoEffect>(scope, handle) {
 *     companion object {
 *         @InitialState
 *         fun initial(params: TodoAtomParams): TodoState = TodoState(...)
 *     }
 * }
 * ```
 *
 * The KSP processor generates:
 *
 * ```kotlin
 * class TodoAtom_Factory(
 *     private val container: AtomContainer  // Or @Inject constructor for Metro
 * ) : AtomFactory<TodoAtom, TodoState, TodoAtomParams> {
 *     override val atomClass = TodoAtom::class
 *     override val stateClass = TodoState::class
 *     override val paramsClass = TodoAtomParams::class
 *     override val serializer = KotlinxStateSerializer(TodoState.serializer())
 *
 *     override fun initial(params: TodoAtomParams): TodoState {
 *         return TodoAtom.initial(params)
 *     }
 *
 *     override fun create(
 *         scope: CoroutineScope,
 *         handle: StateHandle<TodoState>,
 *         params: TodoAtomParams
 *     ): TodoAtom {
 *         val repository = container.resolve<TodoRepository>()
 *         return TodoAtom(scope, handle, params, repository)
 *     }
 * }
 * ```
 *
 * ## Usage in AtomStore
 *
 * Factories are used internally by the atom runtime to create instances:
 *
 * ```kotlin
 * val registry: AtomFactoryRegistry = ...
 * val factory = registry.entryFor(TodoAtom::class) as AtomFactoryEntry<TodoAtom, TodoState, TodoAtomParams>
 *
 * val params = TodoAtomParams(userId = "alice")
 * val initialState = factory.initial(params)
 * val handle = stateHandleFactory.create(key, TodoState::class, { initialState }, factory.serializer)
 * val atom = factory.create(scope, handle, params)
 * ```
 *
 * ## Serialization
 *
 * The [serializer] property enables state persistence:
 * - **Non-null**: State is serialized for process death recovery
 * - **Null**: State is in-memory only (lost on process death)
 *
 * ```kotlin
 * override val serializer: StateSerializer<TodoState> = KotlinxStateSerializer(TodoState.serializer())
 * ```
 *
 * ## Type Safety
 *
 * [AtomFactory] enforces type safety at compile time:
 * - [initial] must return type `S`
 * - [create] must accept `StateHandle<S>` and return type `A`
 * - [serializer] must be `StateSerializer<S>` or `null`
 *
 * ## Dependency Injection
 *
 * Factories integrate with DI frameworks to resolve dependencies:
 *
 * ### Metro Mode
 * ```kotlin
 * class TodoAtom_Factory @Inject constructor(
 *     private val repository: TodoRepository
 * ) : AtomFactory<TodoAtom, TodoState, TodoAtomParams> {
 *     override fun create(...): TodoAtom = TodoAtom(scope, handle, params, repository)
 * }
 * ```
 *
 * ### Non-Metro Mode
 * ```kotlin
 * class TodoAtom_Factory(
 *     private val container: AtomContainer
 * ) : AtomFactory<TodoAtom, TodoState, TodoAtomParams> {
 *     override fun create(...): TodoAtom {
 *         val repository = container.resolve<TodoRepository>()
 *         return TodoAtom(scope, handle, params, repository)
 *     }
 * }
 * ```
 *
 * ## Thread Safety
 *
 * Factory methods must be thread-safe:
 * - [initial] may be called concurrently for different params
 * - [create] may be called concurrently for different scopes/handles
 * - Implementations should be stateless or use proper synchronization
 *
 * @property atomClass The atom's `KClass`, used for runtime type resolution
 * @property stateClass The state's `KClass`, used for state handle creation
 * @property paramsClass The params' `KClass`, used for params validation
 * @property serializer The state serializer, or `null` for in-memory-only state
 *
 * @see AtomFactoryEntry for the base implementation used by generated code
 * @see AnyAtomFactoryEntry for type-erased factory entry
 * @see AtomFactoryRegistry for factory lookup by atom type
 */
interface AtomFactory<A : AtomLifecycle, S : Any, P : Any> {
    val atomClass: KClass<A>
    val stateClass: KClass<S>
    val paramsClass: KClass<P>
    val serializer: StateSerializer<S>?

    /**
     * Computes the initial state for the given params.
     *
     * This method delegates to the atom's `@InitialState` companion function.
     *
     * @param params The initialization parameters
     * @return The initial state
     */
    fun initial(params: P): S

    /**
     * Creates a new atom instance.
     *
     * This method constructs the atom with the provided scope, state handle, and params,
     * resolving any additional dependencies from the DI container.
     *
     * @param scope The coroutine scope for the atom
     * @param handle The state handle for the atom
     * @param params The initialization parameters
     * @return The created atom instance
     */
    fun create(scope: CoroutineScope, handle: StateHandle<S>, params: P): A
}
