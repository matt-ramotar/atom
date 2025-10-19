package dev.mattramotar.atom.runtime.fsm

/**
 * User-facing action that triggers state transitions.
 *
 * [Intent] represents external commands from the UI or other external systems. Intents are the
 * public API boundary - they're traced, logged, and form the contract between UI and atom logic.
 *
 * ## Intent vs Event
 *
 * - **Intent**: User-facing (UI calls `atom.intent(AddTodo("Buy milk"))`)
 * - **Event**: Internal FSM signal (reducer processes `TodoAdded("Buy milk")`)
 *
 * Intents map to one or more events via the [dev.mattramotar.atom.runtime.Atom.intent] method.
 *
 * ## Implementation
 *
 * Define intents as a sealed hierarchy:
 *
 * ```kotlin
 * sealed interface TodoIntent : Intent {
 *     data class AddTodo(val text: String) : TodoIntent
 *     data class ToggleTodo(val id: String) : TodoIntent
 *     data object RefreshTodos : TodoIntent
 * }
 * ```
 *
 * ## Intent Names
 *
 * [intentName] defaults to the simple class name for tracing:
 *
 * ```kotlin
 * AddTodo("...").intentName  // "AddTodo"
 * ```
 *
 * Override for custom names:
 *
 * ```kotlin
 * data class AddTodo(val text: String) : Intent {
 *     override val intentName = "Todo.Add"
 * }
 * ```
 *
 * @property intentName Human-readable name for tracing and logging
 */
interface Intent {
    val intentName: String
        get() = this::class.simpleName ?: "UnknownIntent"
}
