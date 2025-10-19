package dev.mattramotar.atom.runtime.state

import kotlinx.coroutines.flow.StateFlow

/**
 * Persistent, observable container for atom state.
 *
 * [StateHandle] provides the storage layer for atom state, supporting:
 * - **Reactive observation**: State changes are observable via [flow]
 * - **Synchronous access**: Immediate state reads via [get]
 * - **Atomic updates**: Thread-safe state modifications via [set] and [update]
 * - **Persistence**: Optional state serialization for process death recovery
 *
 * ## Implementations
 *
 * - [InMemoryStateHandle]: In-memory state (lost on process death)
 * - Platform-specific handles: SavedStateHandle integration (Android), UserDefaults (iOS), etc.
 *
 * ## Usage in Atoms
 *
 * Atoms receive a [StateHandle] via constructor and use it for state management:
 *
 * ```kotlin
 * class TodoAtom(
 *     scope: CoroutineScope,
 *     val handle: StateHandle<TodoState>
 * ) : Atom<TodoState, TodoIntent, TodoEvent, TodoEffect>(scope, handle) {
 *
 *     val state: StateFlow<TodoState> = handle.flow  // Observe state
 *
 *     override fun reduce(state: TodoState, event: TodoEvent): Transition<TodoState, TodoEffect> {
 *         val nextState = when (event) {
 *             is TodoEvent.TodoAdded -> state.copy(todos = state.todos + event.todo)
 *         }
 *         return Transition(nextState)  // Runtime calls handle.set(nextState)
 *     }
 * }
 * ```
 *
 * ## Thread Safety
 *
 * All methods must be thread-safe:
 * - [get], [set], and [update] can be called from any thread
 * - [update] must be atomic (no race conditions)
 * - [flow] emissions must be serialized
 *
 * @param S The state type
 *
 * @property flow Reactive state observation (emits on every state change)
 *
 * @see InMemoryStateHandle for the default in-memory implementation
 * @see StateHandleFactory for creating state handles
 */
interface StateHandle<S : Any> {
    val flow: StateFlow<S>
    fun get(): S
    fun set(value: S)
    fun update(transform: (S) -> S): S
}
