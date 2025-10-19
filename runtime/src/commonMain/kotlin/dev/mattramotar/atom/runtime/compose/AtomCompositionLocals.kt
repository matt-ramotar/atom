package dev.mattramotar.atom.runtime.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.state.InMemoryStateHandleFactory
import dev.mattramotar.atom.runtime.state.StateHandleFactory
import dev.mattramotar.atom.runtime.store.AtomStore
import dev.mattramotar.atom.runtime.store.AtomStoreOwner

/**
 * Provides atom composition locals for a Compose subtree.
 *
 * This is the primary way to configure atom infrastructure in Compose:
 *
 * ```kotlin
 * @Composable
 * fun App() {
 *     val registry = remember { GeneratedAtomFactoryRegistry }
 *
 *     AtomCompositionLocals(factories = registry) {
 *         AppContent()
 *     }
 * }
 * ```
 *
 * @param factories The atom factory registry (required)
 * @param owner The atom store owner (defaults to a new owner with fresh store)
 * @param stateHandles The state handle factory (defaults to in-memory)
 * @param content The content to wrap
 */
@Composable
fun AtomCompositionLocals(
    factories: AtomFactoryRegistry,
    owner: AtomStoreOwner = remember {
        object : AtomStoreOwner {
            override val atomStore = AtomStore()
        }
    },
    stateHandles: StateHandleFactory = InMemoryStateHandleFactory,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAtomStoreOwner provides owner,
        LocalAtomFactories provides factories,
        LocalStateHandleFactory provides stateHandles,
        content = content
    )
}
