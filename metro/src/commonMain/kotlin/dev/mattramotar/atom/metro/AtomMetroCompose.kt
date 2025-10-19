package dev.mattramotar.atom.metro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.mattramotar.atom.runtime.compose.AtomCompositionLocals
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.state.InMemoryStateHandleFactory
import dev.mattramotar.atom.runtime.state.StateHandleFactory

/**
 * Install Metro-backed AtomCompositionLocals.
 *
 * Usage (assuming your Metro graph exposes AtomFactoryRegistry):
 *   AtomMetroComposition(graph.atomFactoryRegistry) { /* app content */ }
 */
@Composable
public fun AtomMetroComposition(
    registry: AtomFactoryRegistry,
    stateHandles: StateHandleFactory = InMemoryStateHandleFactory,
    content: @Composable () -> Unit
) {
    val factories = remember(registry) { registry }
    AtomCompositionLocals(
        factories = factories,
        stateHandles = stateHandles,
        content = content
    )
}
