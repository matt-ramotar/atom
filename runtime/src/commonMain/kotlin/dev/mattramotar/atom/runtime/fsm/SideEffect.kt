package dev.mattramotar.atom.runtime.fsm

/**
 * Side effect emitted by the reducer for async handling.
 *
 * [SideEffect] represents operations that should occur as a result of state transitions but don't
 * directly modify state (e.g., network requests, database writes, analytics events).
 *
 * ## Reducer Purity
 *
 * Reducers must be pure functions. Side effects allow the reducer to remain pure while still
 * triggering async operations:
 *
 * ```kotlin
 * override fun reduce(state: TodoState, event: TodoEvent): Transition<TodoState, TodoEffect> {
 *     return when (event) {
 *         is TodoEvent.RefreshRequested -> Transition(
 *             to = state.copy(loading = true),
 *             effects = listOf(TodoEffect.LoadTodos)  // Async operation deferred to handler
 *         )
 *     }
 * }
 * ```
 *
 * ## Effect Handling
 *
 * Handle effects in [dev.mattramotar.atom.runtime.AtomLifecycle.onStart]:
 *
 * ```kotlin
 * override fun onStart() {
 *     scope.launch {
 *         effects.collect { effect ->
 *             when (effect) {
 *                 TodoEffect.LoadTodos -> {
 *                     val todos = repository.loadTodos()
 *                     dispatch(TodoEvent.TodosLoaded(todos))
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @property effectName Human-readable name for tracing
 * @property effectType Categorization for metrics and filtering
 */
interface SideEffect {
    val effectName: String
        get() = this::class.simpleName ?: "UnknownEffect"

    val effectType: EffectType
        get() = EffectType.COMPUTE
}
