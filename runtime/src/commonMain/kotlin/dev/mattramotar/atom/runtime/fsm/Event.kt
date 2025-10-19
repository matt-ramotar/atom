package dev.mattramotar.atom.runtime.fsm

/**
 * Internal FSM event processed by the reducer.
 *
 * [Event] represents signals that flow through the finite state machine, triggering state
 * transitions. Events are implementation details - not exposed to UI.
 *
 * ## Event vs Intent
 *
 * - **Event**: Internal (reducer processes `TodoAdded("Buy milk")`)
 * - **Intent**: User-facing (UI calls `atom.intent(AddTodo("Buy milk"))`)
 *
 * Events are dispatched via [dev.mattramotar.atom.runtime.Atom.dispatch] from intent handlers.
 *
 * ## Implementation
 *
 * Define events as a sealed hierarchy:
 *
 * ```kotlin
 * sealed interface TodoEvent : Event {
 *     data class TodoAdded(val text: String) : TodoEvent
 *     data class TodoToggled(val id: String) : TodoEvent
 *     data class TodosLoaded(val todos: List<Todo>) : TodoEvent
 *     data object RefreshRequested : TodoEvent
 * }
 * ```
 */
interface Event
