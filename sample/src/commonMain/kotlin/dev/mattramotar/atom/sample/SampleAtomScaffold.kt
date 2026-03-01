package dev.mattramotar.atom.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.mattramotar.atom.generated.GeneratedAtomRegistry
import dev.mattramotar.atom.runtime.compose.AtomCompositionLocals
import dev.mattramotar.atom.runtime.di.AtomContainer
import kotlin.reflect.KClass

private object SampleAtomContainer : AtomContainer {
    private val repository = InMemorySampleTaskRepository()
    private val diagnostics = InMemorySampleDiagnostics()
    private val bindings: Map<Pair<KClass<*>, String?>, Any> = mapOf(
        (SampleTaskRepository::class to null) to repository,
        (InMemorySampleTaskRepository::class to null) to repository,
        (SampleDiagnostics::class to null) to diagnostics,
        (InMemorySampleDiagnostics::class to null) to diagnostics
    )

    override fun <T : Any> resolve(type: KClass<T>, qualifier: String?): T {
        @Suppress("UNCHECKED_CAST")
        return bindings[type to qualifier] as? T
            ?: error(
                "No sample binding is configured for ${type.qualifiedName} " +
                    "(qualifier=$qualifier)."
            )
    }
}

@Composable
fun SampleShowcaseApp() {
    val registry = remember { GeneratedAtomRegistry(SampleAtomContainer) }
    AtomCompositionLocals(factories = registry) {
        SampleShowcaseShell()
    }
}
