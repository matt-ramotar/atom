package dev.mattramotar.atom.runtime.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory implementation of [StateHandle].
 *
 * [InMemoryStateHandle] stores state in a [MutableStateFlow], providing:
 * - **No persistence**: State is lost on process death
 * - **Reactive updates**: State changes emit via [flow]
 * - **Thread safety**: All operations are thread-safe via `StateFlow`
 * - **Simplicity**: Suitable for ephemeral UI state and testing
 *
 * ## When to Use
 *
 * Use [InMemoryStateHandle] for:
 * - Transient UI state (e.g., current tab index, dialog visibility)
 * - Derived state that can be recomputed
 * - Testing (no persistence complexity)
 * - Development (iterate faster without persistence overhead)
 *
 * Use persistent handles (e.g., SavedStateHandle) for:
 * - User data that must survive process death
 * - Form inputs mid-completion
 * - Navigation state
 *
 * ## Thread Safety
 *
 * All operations delegate to [MutableStateFlow], which is thread-safe:
 * - [get]: Reads current value atomically
 * - [set]: Updates value atomically
 * - [update]: Performs atomic compare-and-set
 *
 * @param initial The initial state value
 *
 * @see StateHandle for the interface
 * @see InMemoryStateHandleFactory for factory creation
 */
class InMemoryStateHandle<S : Any>(initial: S) : StateHandle<S> {
    private val _state = MutableStateFlow(initial)
    override val flow: StateFlow<S> = _state.asStateFlow()

    override fun get(): S = _state.value

    override fun set(value: S) {
        _state.value = value
    }

    override fun update(transform: (S) -> S): S {
        lateinit var result: S
        _state.update { current ->
            transform(current).also { result = it }
        }
        return result
    }
}
