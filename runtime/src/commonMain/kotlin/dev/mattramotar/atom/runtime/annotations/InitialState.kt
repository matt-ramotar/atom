package dev.mattramotar.atom.runtime.annotations

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * Marks the companion object function that provides the initial state for an atom.
 *
 * The KSP processor locates this function to extract the params type `P` and generate the
 * appropriate factory initialization logic. The function must be in the atom's companion object
 * and must return the atom's state type `S`.
 *
 * ## Function Signature Requirements
 *
 * The [InitialState] function must satisfy:
 * - **Location**: Declared in the atom's companion object
 * - **Return type**: Must return `S` (the atom's state type)
 * - **Parameters**: Either:
 *   - Single parameter of type `P` (params type), or
 *   - No parameters (implies `P = Unit`)
 * - **Visibility**: Can be public, internal, or private - visibility doesn't affect generation
 *
 * ## With Parameters
 *
 * When atoms require external configuration or context:
 *
 * ```kotlin
 * @AutoAtom
 * class TodoAtom(
 *     scope: CoroutineScope,
 *     handle: StateHandle<TodoState>,
 *     params: TodoAtomParams
 * ) : Atom<TodoState, TodoIntent, TodoEvent, TodoEffect>(scope, handle) {
 *
 *     companion object {
 *         @InitialState
 *         fun initial(params: TodoAtomParams): TodoState = TodoState(
 *             todo = params.initialTodo,
 *             loading = false,
 *             error = null
 *         )
 *     }
 * }
 *
 * data class TodoAtomParams(
 *     val initialTodo: Todo,
 *     val priorityFilter: Priority? = null
 * )
 * ```
 *
 * ## Without Parameters
 *
 * For atoms with fixed or empty initial state:
 *
 * ```kotlin
 * @AutoAtom
 * class TodoListAtom(
 *     scope: CoroutineScope,
 *     handle: StateHandle<TodoListState>
 * ) : Atom<TodoListState, TodoListIntent, TodoListEvent, TodoListEffect>(scope, handle) {
 *
 *     companion object {
 *         @InitialState
 *         fun initial(): TodoListState = TodoListState(
 *             todos = emptyList(),
 *             loading = false
 *         )
 *     }
 * }
 * ```
 *
 * ## Advanced: Computed Initial State
 *
 * The initial state function can perform computation or validation:
 *
 * ```kotlin
 * companion object {
 *     @InitialState
 *     fun initial(params: TodoAtomParams): TodoState {
 *         // Validation
 *         require(params.userId.isNotBlank()) { "User ID required" }
 *
 *         // Computation
 *         val defaultFilter = if (params.showCompleted) null else TodoFilter.Active
 *
 *         return TodoState(
 *             userId = params.userId,
 *             todos = emptyList(),
 *             filter = defaultFilter,
 *             loading = true
 *         )
 *     }
 * }
 * ```
 *
 * ## Function Naming
 *
 * While `initial` is the conventional name, any function name is valid:
 *
 * ```kotlin
 * companion object {
 *     @InitialState
 *     fun createInitialState(params: Params): State { ... }
 * }
 * ```
 *
 * ## Multiple Functions (Error)
 *
 * Only one function in the companion object can be marked [InitialState]. Multiple annotations
 * will cause a compilation error:
 *
 * ```kotlin
 * companion object {
 *     @InitialState  // Error: multiple @InitialState functions
 *     fun initial(): State = State()
 *
 *     @InitialState  // Error: multiple @InitialState functions
 *     fun initialFromParams(params: Params): State = State(params)
 * }
 * ```
 *
 * ## State Restoration
 *
 * When using state persistence (via [AutoAtom.serializer]), the initial state function is
 * only called when no saved state exists. On process recreation, saved state is deserialized
 * instead of calling this function.
 *
 * @see AutoAtom for atom code generation configuration
 */
@Retention(BINARY)
@Target(FUNCTION)
annotation class InitialState
