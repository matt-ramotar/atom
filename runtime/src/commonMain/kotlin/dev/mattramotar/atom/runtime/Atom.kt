package dev.mattramotar.atom.runtime

import dev.mattramotar.atom.runtime.fsm.Event
import dev.mattramotar.atom.runtime.fsm.Intent
import dev.mattramotar.atom.runtime.fsm.SideEffect
import dev.mattramotar.atom.runtime.fsm.Transition
import dev.mattramotar.atom.runtime.state.StateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Base class for finite state machine (FSM) based atoms with intent-driven architecture.
 *
 * [Atom] provides a structured approach to managing application state through:
 * - **[Intent]**: User-facing actions that trigger state transitions
 * - **[Event]**: Internal FSM signals that flow through the reducer
 * - **Reducer**: Pure function that computes state transitions
 * - **[SideEffect]**: Async operations emitted by the reducer
 * - **[StateHandle]**: Persistent, observable state container
 *
 * ## Architecture Overview
 *
 * ```
 * ┌─────────┐
 * │   UI    │
 * └────┬────┘
 *      │ intent(AddTodo)
 *      ▼
 * ┌─────────────┐
 * │    Atom     │
 * │             │
 * │  intent()   │──┬───▶ dispatch(event) ───▶ [Event Channel]
 * │             │  │                                │
 * │  reduce()   │◀─┴────────────────────────────────┘
 * │     │       │
 * │     ├──▶ State' ──▶ [StateHandle] ──▶ UI
 * │     │       │
 * │     └──▶ Effects ──▶ [Effect Channel] ──▶ Side effect handlers
 * └─────────────┘
 * ```
 *
 * ## Example: Todo Atom
 *
 * ```kotlin
 * @AutoAtom
 * class TodoAtom(
 *     scope: CoroutineScope,
 *     handle: StateHandle<TodoState>,
 *     params: TodoAtomParams
 * ) : Atom<TodoState, TodoIntent, TodoEvent, TodoEffect>(scope, handle) {
 *
 *     private val repository = params.repository
 *
 *     companion object {
 *         @InitialState
 *         fun initial(params: TodoAtomParams) = TodoState(
 *             todos = emptyList(),
 *             loading = false
 *         )
 *     }
 *
 *     // Intent → Event translation
 *     override fun intent(intent: TodoIntent) {
 *         when (intent) {
 *             is TodoIntent.AddTodo -> dispatch(TodoEvent.TodoAdded(intent.text))
 *             is TodoIntent.ToggleTodo -> dispatch(TodoEvent.TodoToggled(intent.id))
 *             TodoIntent.RefreshTodos -> dispatch(TodoEvent.RefreshRequested)
 *         }
 *     }
 *
 *     // Event → State + Effects
 *     override fun reduce(state: TodoState, event: TodoEvent): Transition<TodoState, TodoEffect> {
 *         return when (event) {
 *             is TodoEvent.TodoAdded -> Transition(
 *                 to = state.copy(todos = state.todos + Todo(text = event.text)),
 *                 effects = listOf(TodoEffect.SaveTodo(event.text))
 *             )
 *             is TodoEvent.TodoToggled -> {
 *                 val updated = state.todos.map {
 *                     if (it.id == event.id) it.copy(completed = !it.completed) else it
 *                 }
 *                 Transition(
 *                     to = state.copy(todos = updated),
 *                     effects = listOf(TodoEffect.UpdateTodo(event.id))
 *                 )
 *             }
 *             TodoEvent.RefreshRequested -> Transition(
 *                 to = state.copy(loading = true),
 *                 effects = listOf(TodoEffect.LoadTodos)
 *             )
 *             is TodoEvent.TodosLoaded -> Transition(
 *                 to = state.copy(todos = event.todos, loading = false)
 *             )
 *         }
 *     }
 *
 *     override fun onStart() {
 *         // Handle side effects
 *         scope.launch {
 *             effects.collect { effect ->
 *                 when (effect) {
 *                     is TodoEffect.LoadTodos -> {
 *                         val todos = repository.loadTodos()
 *                         dispatch(TodoEvent.TodosLoaded(todos))
 *                     }
 *                     is TodoEffect.SaveTodo -> repository.saveTodo(effect.text)
 *                     is TodoEffect.UpdateTodo -> repository.updateTodo(effect.id)
 *                 }
 *             }
 *         }
 *
 *         // Initial load
 *         dispatch(TodoEvent.RefreshRequested)
 *     }
 * }
 * ```
 *
 * ## Intent Handling
 *
 * Override [intent] to map user actions to internal events:
 *
 * ```kotlin
 * override fun intent(intent: TodoIntent) {
 *     when (intent) {
 *         is TodoIntent.AddTodo -> {
 *             // Validation before dispatching event
 *             if (intent.text.isNotBlank()) {
 *                 dispatch(TodoEvent.TodoAdded(intent.text))
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * ## Reducer Pattern
 *
 * The [reduce] function is a pure function `(S, E) → (S', [F])`:
 * - **Input**: Current state `S`, event `E`
 * - **Output**: New state `S'`, side effects `[F]`
 * - **Purity**: No I/O, no mutations, deterministic
 *
 * ```kotlin
 * override fun reduce(state: TodoState, event: TodoEvent): Transition<TodoState, TodoEffect> {
 *     return when (event) {
 *         is TodoEvent.Loaded -> Transition(
 *             to = state.copy(todos = event.todos, loading = false)
 *         )
 *         is TodoEvent.Error -> Transition(
 *             to = state.copy(error = event.message, loading = false),
 *             effects = listOf(TodoEffect.ShowError(event.message))
 *         )
 *     }
 * }
 * ```
 *
 * ## Side Effect Handling
 *
 * Side effects are emitted via the [effects] flow and handled asynchronously:
 *
 * ```kotlin
 * override fun onStart() {
 *     scope.launch {
 *         effects.collect { effect ->
 *             when (effect) {
 *                 is TodoEffect.LoadTodos -> {
 *                     try {
 *                         val todos = repository.loadTodos()
 *                         dispatch(TodoEvent.TodosLoaded(todos))
 *                     } catch (e: Exception) {
 *                         dispatch(TodoEvent.LoadFailed(e.message ?: "Unknown error"))
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * ## State Observation
 *
 * The [state] property is a `StateFlow<S>` for reactive UI updates:
 *
 * ```kotlin
 * @Composable
 * fun TodoScreen() {
 *     val atom = atom<TodoAtom>()
 *     val state by atom.state.collectAsState()
 *
 *     Column {
 *         state.todos.forEach { todo ->
 *             TodoItem(
 *                 todo = todo,
 *                 onToggle = { atom.intent(TodoIntent.ToggleTodo(todo.id)) }
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * ## Lifecycle Integration
 *
 * [Atom] implements [AtomLifecycle]:
 * - [onStart]: Called when first acquired - use for initialization
 * - [onStop]: Called when last reference released - use for cleanup
 * - [onDispose]: Called before garbage collection - use for resource release
 *
 * ## Thread Safety
 *
 * - **State updates**: Serialized through the event channel - no race conditions
 * - **Intent calls**: Thread-safe - can be called from any thread
 * - **Effect emissions**: Serialized - effects are emitted in order
 * - **Coroutine scope**: Automatically cancelled on [onStop]
 *
 * ## Lifecycle and Scope
 *
 * The atom's [scope] is a supervised coroutine scope:
 * - Child coroutines are launched in this scope
 * - Scope is automatically cancelled when the atom is stopped
 * - Cancellation propagates to all child coroutines
 *
 * @param S State type, subtype of [Any].
 * @param I [Intent] type.
 * @param E [Event] type.
 * @param F [SideEffect] type.
 * @param scope The coroutine scope for this atom. Automatically cancelled on [onStop].
 * @param handle The state container for this atom. Provides state persistence and observation.
 *
 * @see Intent for user-facing action interface.
 * @see Event for FSM event interface.
 * @see SideEffect for side effect interface.
 * @see Transition for state transition representation.
 * @see StateHandle for state persistence and observation.
 */
abstract class Atom<S : Any, I : Intent, E : Event, F : SideEffect>(
    val scope: CoroutineScope,
    val handle: StateHandle<S>,
) : AtomLifecycle {
    /**
     * Observable state flow for reactive UI updates.
     *
     * Emits the current state whenever it changes via the reducer or direct updates.
     * Backed by the [StateHandle]'s flow.
     */
    val state: StateFlow<S> = handle.flow

    /**
     * Internal event channel for serialized event processing.
     *
     * Events dispatched via [dispatch] are queued in this channel and processed sequentially
     * by the reducer. This ensures state updates are serialized and race-free.
     */
    private val events = Channel<E>(capacity = Channel.BUFFERED)

    /**
     * Internal effect channel for side effect emissions.
     *
     * Effects emitted by the reducer are queued in this unlimited channel for asynchronous
     * handling. Effects are processed in the order they are emitted.
     */
    private val _effects = Channel<F>(Channel.UNLIMITED)

    /**
     * Observable flow of side effects emitted by the reducer.
     *
     * Collect this flow to handle async operations like network requests, database writes,
     * analytics events, etc.
     */
    val effects: Flow<F> = _effects.receiveAsFlow()

    /**
     * Starts the event processing loop when the atom becomes active.
     *
     * Launches a coroutine that processes events from the [events] channel, invokes the
     * [reduce] function, updates state, and emits side effects.
     *
     * This method is called by the runtime when the atom is first acquired.
     */
    override fun onStart() {
        scope.launch {
            for (event in events) {
                val current = handle.get()
                val (next, fx) = reduce(current, event)
                if (next != current) handle.set(next)
                fx.forEach { _effects.send(it) }
            }
        }
    }

    /**
     * Dispatches an event to the reducer for state transition.
     *
     * Events are queued in the internal event channel and processed sequentially. This is an
     * internal API called from [intent] implementations.
     *
     * **Thread Safety**: Safe to call from any thread.
     *
     * @param event The event to dispatch to the reducer
     */
    protected fun dispatch(event: E) {
        events.trySend(event).getOrElse {
            scope.launch {
                events.send(event)
            }
        }
    }

    /**
     * Handles user-facing intents by translating them to internal events.
     *
     * Override this method to define how intents map to events. This is the public API
     * that UI code calls to trigger state changes.
     *
     * The default implementation throws an error for all intents.
     *
     * **Thread Safety**: Safe to call from any thread.
     *
     * @param intent The user-facing action to handle
     * @throws IllegalStateException if the intent is not handled
     */
    open fun intent(intent: I) {
        error("Intent not handled: ${intent::class.simpleName}")
    }

    /**
     * Reads the current state synchronously.
     *
     * Use this for immediate state access outside of flows. Prefer observing [state] for
     * reactive updates.
     *
     * **Thread Safety**: Safe to call from any thread.
     *
     * @return The current state
     */
    fun get(): S = handle.get()

    /**
     * Computes the next state and side effects for a given event.
     *
     * This is the core FSM reducer function. Override this to define state transition logic.
     *
     * **Requirements**:
     * - Pure function: no I/O, no side effects, deterministic
     * - No mutations: return a new state, don't modify the input
     * - Efficient: called for every event, should complete quickly
     *
     * The default implementation returns the current state unchanged with no effects.
     *
     * **Thread Safety**: Called from the event processing coroutine. State updates are serialized.
     *
     * @param state The current state
     * @param event The event to process
     * @return A [Transition] containing the new state and side effects to emit
     */
    protected open fun reduce(state: S, event: E): Transition<S, F> {
        return Transition(state)
    }
}
