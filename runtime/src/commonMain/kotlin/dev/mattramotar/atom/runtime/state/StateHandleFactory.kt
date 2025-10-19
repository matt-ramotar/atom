package dev.mattramotar.atom.runtime.state

import dev.mattramotar.atom.runtime.AtomKey
import dev.mattramotar.atom.runtime.serialization.StateSerializer
import kotlin.reflect.KClass

/**
 * Factory for creating [StateHandle] instances.
 *
 * [StateHandleFactory] abstracts state handle creation, enabling different storage strategies:
 * - [InMemoryStateHandleFactory]: In-memory state (lost on process death)
 * - Platform-specific factories: SavedStateHandle (Android), UserDefaults (iOS), etc.
 *
 * ## Usage
 *
 * Factories are provided via `LocalStateHandleFactory`:
 *
 * ```kotlin
 * @Composable
 * fun App() {
 *     val factory = remember { InMemoryStateHandleFactory }
 *
 *     AtomCompositionLocals(stateHandles = factory) {
 *         AppContent()
 *     }
 * }
 * ```
 *
 * ## Persistence
 *
 * When a [StateSerializer] is provided, the factory should:
 * 1. Check for saved state (from previous process)
 * 2. Deserialize if found, or call [initial] if not
 * 3. Serialize state on every [StateHandle.set] call
 *
 * @see StateHandle for the state container interface
 * @see InMemoryStateHandleFactory for the default in-memory implementation
 */
interface StateHandleFactory {
    fun <S : Any> create(
        key: AtomKey,
        stateClass: KClass<S>,
        initial: () -> S,
        serializer: StateSerializer<S>?
    ): StateHandle<S>
}
