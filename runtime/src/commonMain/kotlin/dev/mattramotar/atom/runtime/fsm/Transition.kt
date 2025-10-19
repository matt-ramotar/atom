package dev.mattramotar.atom.runtime.fsm

import androidx.compose.runtime.Immutable

/**
 * State transition result from the reducer.
 *
 * [Transition] encapsulates the output of the reducer function:
 * - **Next state**: The new state after processing the event
 * - **Side effects**: Async operations to perform
 *
 * ## Usage
 *
 * Return [Transition] from [dev.mattramotar.atom.runtime.Atom.reduce]:
 *
 * ```kotlin
 * override fun reduce(state: TodoState, event: TodoEvent): Transition<TodoState, TodoEffect> {
 *     return when (event) {
 *         is TodoEvent.TodoAdded -> Transition(
 *             to = state.copy(todos = state.todos + event.todo),
 *             effects = listOf(TodoEffect.SaveTodo(event.todo))
 *         )
 *         is TodoEvent.TodosLoaded -> Transition(
 *             to = state.copy(todos = event.todos, loading = false)
 *             // No effects
 *         )
 *     }
 * }
 * ```
 *
 * @param to The next state
 * @param effects Side effects to emit (defaults to empty list)
 */
@Immutable
data class Transition<S : Any, F : SideEffect>(
    val to: S,
    val effects: List<F> = emptyList()
)
