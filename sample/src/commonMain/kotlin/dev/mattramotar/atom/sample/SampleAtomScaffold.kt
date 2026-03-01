package dev.mattramotar.atom.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.mattramotar.atom.generated.GeneratedAtomRegistry
import dev.mattramotar.atom.runtime.compose.AtomCompositionLocals
import dev.mattramotar.atom.runtime.di.AtomContainer
import kotlin.reflect.KClass

private object SampleAtomContainer : AtomContainer {
    override fun <T : Any> resolve(type: KClass<T>, qualifier: String?): T {
        error("No sample bindings are configured (qualifier=$qualifier).")
    }
}

@Composable
fun SampleShowcaseApp() {
    val registry = remember { GeneratedAtomRegistry(SampleAtomContainer) }
    AtomCompositionLocals(factories = registry) {
        SampleShowcaseShell()
    }
}
