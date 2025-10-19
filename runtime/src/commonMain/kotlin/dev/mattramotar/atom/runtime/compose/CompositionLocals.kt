package dev.mattramotar.atom.runtime.compose

import androidx.compose.runtime.staticCompositionLocalOf
import dev.mattramotar.atom.runtime.di.AtomContainer
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.factory.EmptyAtomFactoryRegistry
import dev.mattramotar.atom.runtime.state.InMemoryStateHandleFactory
import dev.mattramotar.atom.runtime.state.StateHandleFactory
import dev.mattramotar.atom.runtime.store.AtomStore
import dev.mattramotar.atom.runtime.store.AtomStoreOwner

/**
 * CompositionLocal for providing [AtomStoreOwner] in Compose.
 *
 * Defaults to a fresh [AtomStore] instance.
 */
val LocalAtomStoreOwner = staticCompositionLocalOf<AtomStoreOwner> {
    object : AtomStoreOwner {
        override val atomStore: AtomStore = AtomStore()
    }
}

/**
 * CompositionLocal for providing [StateHandleFactory] in Compose.
 *
 * Defaults to [InMemoryStateHandleFactory] (no persistence).
 */
val LocalStateHandleFactory = staticCompositionLocalOf<StateHandleFactory> {
    InMemoryStateHandleFactory
}

/**
 * CompositionLocal for providing [AtomFactoryRegistry] in Compose.
 *
 * Defaults to [EmptyAtomFactoryRegistry] (resolves no factories).
 */
val LocalAtomFactories = staticCompositionLocalOf<AtomFactoryRegistry> {
    EmptyAtomFactoryRegistry
}

/**
 * CompositionLocal for providing [AtomContainer] in Compose (non-Metro DI only).
 *
 * Throws an error if accessed without being explicitly provided.
 */
val LocalAtomContainer = staticCompositionLocalOf<AtomContainer> {
    error("No AtomContainer provided. Wrap your composition with AtomDI { ... }")
}
