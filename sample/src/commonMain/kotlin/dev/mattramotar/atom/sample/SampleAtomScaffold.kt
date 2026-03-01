package dev.mattramotar.atom.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.mattramotar.atom.generated.GeneratedAtomRegistry
import dev.mattramotar.atom.runtime.compose.AtomCompositionLocals
import dev.mattramotar.atom.runtime.di.AtomContainer
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.state.InMemoryStateHandleFactory
import dev.mattramotar.atom.runtime.state.StateHandleFactory
import kotlin.reflect.KClass

private class SampleAtomContainer(
    private val stateHandles: StateHandleFactory,
) : AtomContainer {
    private val repository = InMemorySampleTaskRepository()
    private val diagnostics = InMemorySampleDiagnostics()
    var registry: AtomFactoryRegistry? = null

    override fun <T : Any> resolve(type: KClass<T>, qualifier: String?): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            type == SampleTaskRepository::class || type == InMemorySampleTaskRepository::class ->
                repository as T

            type == SampleDiagnostics::class || type == InMemorySampleDiagnostics::class ->
                diagnostics as T

            type == StateHandleFactory::class -> stateHandles as T
            type == AtomFactoryRegistry::class -> {
                val value = registry
                    ?: error("Sample AtomFactoryRegistry is not initialized yet.")
                value as T
            }

            else -> error(
                "No sample binding is configured for the requested type " +
                    "(qualifier=$qualifier)."
            )
        }
    }
}

@Composable
fun SampleShowcaseApp() {
    val stateHandles = remember { InMemoryStateHandleFactory }
    val container = remember { SampleAtomContainer(stateHandles = stateHandles) }
    val registry = remember { GeneratedAtomRegistry(container) }
    container.registry = registry
    AtomCompositionLocals(
        factories = registry,
        stateHandles = stateHandles
    ) {
        SampleShowcaseShell()
    }
}
