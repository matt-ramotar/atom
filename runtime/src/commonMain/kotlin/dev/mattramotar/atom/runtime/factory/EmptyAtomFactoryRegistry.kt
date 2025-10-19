package dev.mattramotar.atom.runtime.factory

import dev.mattramotar.atom.runtime.AtomLifecycle
import kotlin.reflect.KClass

/**
 * Empty implementation of [AtomFactoryRegistry] that resolves no factories.
 *
 * [EmptyAtomFactoryRegistry] serves as a safe default for scenarios where no atom factories
 * are available, such as:
 * - Testing environments without generated factories
 * - Library modules that define atoms but don't instantiate them
 * - Fallback when the real registry fails to load
 *
 * ## Behavior
 *
 * [entryFor] always returns `null`, indicating no factory is registered for any atom type.
 * This causes the atom runtime to produce a clear error message when attempting to create an atom.
 *
 * ## Usage as Default
 *
 * [EmptyAtomFactoryRegistry] is the default value for `LocalAtomFactories`:
 *
 * ```kotlin
 * val LocalAtomFactories = staticCompositionLocalOf<AtomFactoryRegistry> {
 *     EmptyAtomFactoryRegistry  // Default: no factories available
 * }
 * ```
 *
 * Applications must explicitly provide a real registry:
 *
 * ```kotlin
 * @Composable
 * fun App() {
 *     AtomCompositionLocals(factories = GeneratedAtomFactoryRegistry) {
 *         // Atoms can now be created
 *         AppContent()
 *     }
 * }
 * ```
 *
 * ## Error Messages
 *
 * When used, the atom runtime produces helpful errors:
 *
 * ```kotlin
 * val entry = registry.entryFor(TodoAtom::class)  // Returns null
 *     ?: error("No AtomFactoryEntry for TodoAtom. Ensure TodoAtom is annotated with @AutoAtom.")
 * // Error: No AtomFactoryEntry for TodoAtom. Ensure TodoAtom is annotated with @AutoAtom.
 * ```
 *
 * ## Testing
 *
 * In tests, use [EmptyAtomFactoryRegistry] when atoms are not needed:
 *
 * ```kotlin
 * @Test
 * fun `test without atoms`() = runTest {
 *     composeTestRule.setContent {
 *         AtomCompositionLocals(factories = EmptyAtomFactoryRegistry) {
 *             // Components that don't use atoms
 *             StatelessComponent()
 *         }
 *     }
 * }
 * ```
 *
 * Or provide a mock registry for specific atoms:
 *
 * ```kotlin
 * val mockRegistry = object : AtomFactoryRegistry {
 *     override fun entryFor(type: KClass<out AtomLifecycle>) = when (type) {
 *         TodoAtom::class -> MockTodoAtom_Entry
 *         else -> null
 *     }
 * }
 * ```
 *
 * ## Singleton Pattern
 *
 * [EmptyAtomFactoryRegistry] is a singleton `object`, ensuring all usages share the same instance:
 *
 * ```kotlin
 * object EmptyAtomFactoryRegistry : AtomFactoryRegistry {
 *     override fun entryFor(...) = null
 * }
 * ```
 *
 * ## Thread Safety
 *
 * [EmptyAtomFactoryRegistry] is stateless and thread-safe. Multiple threads can safely call
 * [entryFor] concurrently.
 *
 * @see AtomFactoryRegistry for the registry interface
 * @see dev.mattramotar.atom.runtime.compose.LocalAtomFactories for Compose integration
 */
object EmptyAtomFactoryRegistry : AtomFactoryRegistry {
    /**
     * Always returns `null`, indicating no factories are registered.
     *
     * @param type The atom type to look up (ignored)
     * @return Always `null`
     */
    override fun entryFor(type: KClass<out AtomLifecycle>): AnyAtomFactoryEntry<out AtomLifecycle>? = null
}
