package dev.mattramotar.atom.runtime.fsm

/**
 * Builder for constructing [Transition] instances in the reducer DSL.
 *
 * [TransitionBuilder] is used within [TransitionScope.on] blocks:
 *
 * ```kotlin
 * on<TodoEvent.TodoAdded> { state, event ->
 *     transitionTo(state.copy(todos = state.todos + event.todo))
 *     emit(TodoEffect.SaveTodo(event.todo))
 *     emit(TodoEffect.NotifyUser)
 * }
 * ```
 */
class TransitionBuilder<S : Any, F : SideEffect> {
    private var next: S? = null
    private val effects = mutableListOf<F>()

    /**
     * Sets the next state.
     *
     * @param state The new state
     */
    fun transitionTo(state: S) {
        next = state
    }

    /**
     * Adds a side effect to emit.
     *
     * @param effect The side effect
     */
    fun emit(effect: F) {
        effects += effect
    }

    @PublishedApi
    internal fun build(current: S): Transition<S, F> = Transition(next ?: current, effects.toList())
}
