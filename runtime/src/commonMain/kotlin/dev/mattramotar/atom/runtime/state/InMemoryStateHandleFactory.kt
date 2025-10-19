package dev.mattramotar.atom.runtime.state

import dev.mattramotar.atom.runtime.AtomKey
import dev.mattramotar.atom.runtime.serialization.StateSerializer
import kotlin.reflect.KClass

/**
 * Factory for creating [InMemoryStateHandle] instances.
 *
 * [InMemoryStateHandleFactory] creates in-memory state handles, ignoring:
 * - The [StateSerializer] (no persistence)
 * - The [AtomKey] (no state restoration)
 *
 * Every call to [create] invokes [initial] to compute a fresh state.
 *
 * ## Default Factory
 *
 * [InMemoryStateHandleFactory] is the default for `LocalStateHandleFactory`:
 *
 * ```kotlin
 * val LocalStateHandleFactory = staticCompositionLocalOf<StateHandleFactory> {
 *     InMemoryStateHandleFactory
 * }
 * ```
 *
 * ## Singleton
 *
 * [InMemoryStateHandleFactory] is a stateless singleton `object`.
 *
 * @see InMemoryStateHandle for the created handle type
 * @see StateHandleFactory for the factory interface
 */
object InMemoryStateHandleFactory : StateHandleFactory {
    override fun <S : Any> create(
        key: AtomKey,
        stateClass: KClass<S>,
        initial: () -> S,
        serializer: StateSerializer<S>?
    ): StateHandle<S> = InMemoryStateHandle(initial())
}
