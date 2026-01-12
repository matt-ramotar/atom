package dev.mattramotar.atom.runtime

import dev.mattramotar.atom.runtime.fsm.Event
import dev.mattramotar.atom.runtime.fsm.Intent
import dev.mattramotar.atom.runtime.fsm.SideEffect
import dev.mattramotar.atom.runtime.fsm.Transition
import dev.mattramotar.atom.runtime.state.StateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
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
 * - [onStopInternal]: Called when last reference released - use for cleanup
 * - [onDisposeInternal]: Called before garbage collection - use for resource release
 * [onStop] and [onDispose] are final to enforce channel shutdown behavior.
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
 * @param channelConfig Channel configuration for event/effect backpressure behavior.
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
    private val channelConfig: AtomChannelConfig = AtomChannelConfig(),
) : AtomLifecycle {
    private val scopeJob = scope.coroutineContext[Job]

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
     * Capacity and overflow behavior are configured via [AtomChannelConfig].
     */
    private var events = newEventsChannel()

    /**
     * Internal effect channel for side effect emissions.
     *
     * Effects emitted by the reducer are queued in this channel for asynchronous handling.
     * Capacity and overflow behavior are configured via [AtomChannelConfig].
     */
    private var _effects = newEffectsChannel()

    init {
        scopeJob?.invokeOnCompletion {
            closeChannels()
        }
    }

    /**
     * Observable flow of side effects emitted by the reducer.
     *
     * Collect this flow to handle async operations like network requests, database writes,
     * analytics events, etc.
     */
    val effects: Flow<F>
        get() = _effects.receiveAsFlow()

    /**
     * Starts the event processing loop when the atom becomes active.
     *
     * Launches a coroutine that processes events from the [events] channel, invokes the
     * [reduce] function, updates state, and emits side effects.
     *
     * This method is called by the runtime when the atom is first acquired.
     */
    override fun onStart() {
        ensureChannelsOpen()
        scope.launch {
            for (event in events) {
                val current = handle.get()
                val (next, fx) = reduce(current, event)
                if (next != current) handle.set(next)
                for (effect in fx) {
                    if (_effects.isClosedForSend) break
                    try {
                        _effects.send(effect)
                    } catch (e: ClosedSendChannelException) {
                        break
                    }
                }
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
     * **Shutdown**: After [onStop]/[onDispose], dispatch is a no-op.
     *
     * @param event The event to dispatch to the reducer
     */
    protected fun dispatch(event: E) {
        val job = scopeJob
        if (job != null && !job.isActive) return
        if (events.isClosedForSend) return

        val result = events.trySend(event)
        if (result.isSuccess) return
        if (events.isClosedForSend) return
        if (channelConfig.events.onBufferOverflow != BufferOverflow.SUSPEND) return

        scope.launch {
            try {
                events.send(event)
            } catch (e: ClosedSendChannelException) {
                // Drop after shutdown.
            }
        }
    }

    /**
     * Hook for subclasses to clean up when the atom stops.
     *
     * Override this instead of [onStop].
     * Called after channels are closed, so dispatch/effect sends are ignored.
     */
    protected open fun onStopInternal() {}

    /**
     * Hook for subclasses to clean up when the atom is disposed.
     *
     * Override this instead of [onDispose].
     * Called after channels are closed, so dispatch/effect sends are ignored.
     */
    protected open fun onDisposeInternal() {}

    final override fun onStop() {
        closeChannels()
        onStopInternal()
    }

    final override fun onDispose() {
        closeChannels()
        onDisposeInternal()
    }

    private fun closeChannels() {
        events.close()
        _effects.close()
    }

    private fun ensureChannelsOpen() {
        if (events.isClosedForSend) {
            events = newEventsChannel()
        }
        if (_effects.isClosedForSend) {
            _effects = newEffectsChannel()
        }
    }

    private fun newEventsChannel() = Channel<E>(
        capacity = channelConfig.events.capacity,
        onBufferOverflow = channelConfig.events.onBufferOverflow,
    )

    private fun newEffectsChannel(): Channel<F> {
        require(channelConfig.effects.capacity != Channel.UNLIMITED) {
            "Effect channel must be bounded. Configure a capacity and overflow policy."
        }
        return Channel(
            capacity = channelConfig.effects.capacity,
            onBufferOverflow = channelConfig.effects.onBufferOverflow,
        )
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
