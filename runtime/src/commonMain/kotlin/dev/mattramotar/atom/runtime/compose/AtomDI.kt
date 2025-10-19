package dev.mattramotar.atom.runtime.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.mattramotar.atom.runtime.di.AtomContainer

/**
 * Provides [AtomContainer] for a Compose subtree (non-Metro DI only).
 *
 * Use this when `atom.di != "metro"` to provide dependency resolution:
 *
 * ```kotlin
 * @Composable
 * fun App() {
 *     val container = remember { KoinAtomContainer(getKoin()) }
 *
 *     AtomDI(container = container) {
 *         AppContent()
 *     }
 * }
 * ```
 *
 * @param container The DI container
 * @param content The content to wrap
 */
@Composable
fun AtomDI(container: AtomContainer, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAtomContainer provides container, content = content)
}
