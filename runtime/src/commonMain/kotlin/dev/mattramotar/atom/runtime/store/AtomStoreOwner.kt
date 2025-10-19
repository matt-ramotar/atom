package dev.mattramotar.atom.runtime.store

/**
 * Interface for providing an [AtomStore] instance.
 *
 * [AtomStoreOwner] is implemented by components that manage atom lifecycle, such as:
 * - Compose compositions
 * - Navigation scopes
 * - Activity/Fragment containers (Android)
 * - ViewController containers (iOS)
 *
 * ## Compose Integration
 *
 * Provide an owner via `LocalAtomStoreOwner`:
 *
 * ```kotlin
 * @Composable
 * fun App() {
 *     val owner = remember {
 *         object : AtomStoreOwner {
 *             override val atomStore = AtomStore()
 *         }
 *     }
 *
 *     AtomCompositionLocals(owner = owner) {
 *         AppContent()
 *     }
 * }
 * ```
 *
 * ## Default Owner
 *
 * `LocalAtomStoreOwner` provides a default owner if none is specified:
 *
 * ```kotlin
 * val LocalAtomStoreOwner = staticCompositionLocalOf<AtomStoreOwner> {
 *     object : AtomStoreOwner {
 *         override val atomStore: AtomStore = AtomStore()
 *     }
 * }
 * ```
 *
 * @property atomStore The atom store for managing atom lifecycle
 *
 * @see AtomStore for the store implementation
 * @see dev.mattramotar.atom.runtime.compose.LocalAtomStoreOwner for Compose integration
 */
interface AtomStoreOwner {
    val atomStore: AtomStore
}
