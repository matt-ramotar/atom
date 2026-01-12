package dev.mattramotar.atom.runtime.fsm

/**
 * DSL scope for declarative reducer definition.
 *
 * [TransitionScope] provides a builder API for defining reducers without explicit `when` expressions:
 *
 * ```kotlin
 * private val reducer = TransitionScope<TodoState, TodoEvent, TodoEffect>().apply {
 *     on<TodoEvent.TodoAdded> { state, event ->
 *         transitionTo(state.copy(todos = state.todos + event.todo))
 *         emit(TodoEffect.SaveTodo(event.todo))
 *     }
 * }
 *
 * override fun reduce(state: TodoState, event: TodoEvent) = reducer.apply(state, event)
 * ```
 *
 * ## Guards
 *
 * Conditionally handle events:
 *
 * ```kotlin
 * on<TodoEvent.TodoToggled>(
 *     guard = { state, event -> state.todos.any { it.id == event.id } }
 * ) { state, event ->
 *     transitionTo(state.copy(todos = state.todos.map {
 *         if (it.id == event.id) it.copy(completed = !it.completed) else it
 *     }))
 * }
 * ```
 *
 * Guard evaluation stops at the first matching handler. If the guard fails, no other handlers
 * are considered and the state is left unchanged.
 */
class TransitionScope<S : Any, E : Event, F : SideEffect> {
    @PublishedApi
    internal data class Handler<S : Any, F : SideEffect>(
        val predicate: (Event) -> Boolean,
        val block: (S, Event) -> Transition<S, F>
    )

    @PublishedApi
    internal val handlers = mutableListOf<Handler<S, F>>()

    inline fun <reified E : Event> on(
        noinline guard: (S, E) -> Boolean = { _, _ -> true },
        noinline block: TransitionBuilder<S, F>.(S, E) -> Unit
    ) {
        handlers += Handler(
            predicate = { it is E },
            block = { s, e ->
                val ev = e as E
                if (!guard(s, ev)) Transition(s)
                else TransitionBuilder<S, F>().apply { block(s, ev) }.build(s)
            }
        )
    }

    fun apply(state: S, event: E): Transition<S, F> {
        val handler = handlers.firstOrNull { it.predicate(event) } ?: return Transition(state)
        return handler.block(state, event)
    }
}
