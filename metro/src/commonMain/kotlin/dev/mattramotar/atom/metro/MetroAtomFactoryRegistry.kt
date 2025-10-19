package dev.mattramotar.atom.metro

import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.factory.AnyAtomFactoryEntry
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.zacsweers.metro.Provider
import kotlin.reflect.KClass

/**
 * Metro-backed registry that looks up contributed Atom factory entries by KClass.
 *
 * Factories are provided lazily via [Provider] to avoid eager graph instantiation.
 */
public class MetroAtomFactoryRegistry(
    private val entries: Map<KClass<out AtomLifecycle>, Provider<AnyAtomFactoryEntry<out AtomLifecycle>>>
) : AtomFactoryRegistry {

    override fun entryFor(type: KClass<out AtomLifecycle>): AnyAtomFactoryEntry<out AtomLifecycle>? {
        val provider = entries[type] ?: return null
        // Lazily instantiate the entry
        return provider()
    }
}
