package dev.mattramotar.atom.runtime.factory

import dev.mattramotar.atom.runtime.AtomLifecycle
import kotlin.reflect.KClass

/**
 * Registry for looking up atom factories by atom type.
 *
 * [AtomFactoryRegistry] provides a type-to-factory mapping that enables runtime atom resolution.
 * The KSP processor generates registry implementations that aggregate all `@AutoAtom`-annotated
 * atoms in a module, allowing the atom runtime to dynamically create atoms based on their `KClass`.
 *
 * ## Purpose
 *
 * [AtomFactoryRegistry] solves the problem of runtime atom creation:
 * - **Compile time**: We know the exact atom type (e.g., `TodoAtom`)
 * - **Runtime**: We only have a `KClass<out AtomLifecycle>` from Compose `remember` keys
 *
 * The registry bridges this gap by maintaining a `KClass → Factory` mapping.
 *
 * ## Generated Implementation
 *
 * For a module with multiple atoms:
 *
 * ```kotlin
 * @AutoAtom
 * class TodoAtom(...) : Atom<TodoState, ...>
 *
 * @AutoAtom
 * class UserAtom(...) : Atom<UserState, ...>
 * ```
 *
 * The KSP processor generates:
 *
 * ```kotlin
 * object GeneratedAtomFactoryRegistry : AtomFactoryRegistry {
 *     private val factories = mapOf<KClass<out AtomLifecycle>, AnyAtomFactoryEntry<out AtomLifecycle>>(
 *         TodoAtom::class to TodoAtom_Entry,
 *         UserAtom::class to UserAtom_Entry
 *     )
 *
 *     override fun entryFor(type: KClass<out AtomLifecycle>): AnyAtomFactoryEntry<out AtomLifecycle>? {
 *         return factories[type]
 *     }
 * }
 * ```
 *
 * ## Usage in Compose
 *
 * Provide the registry via `LocalAtomFactories`:
 *
 * ```kotlin
 * @Composable
 * fun App() {
 *     val registry = remember { GeneratedAtomFactoryRegistry }
 *
 *     AtomCompositionLocals(factories = registry) {
 *         // Atoms created here are resolved via the registry
 *         TodoScreen()
 *     }
 * }
 * ```
 *
 * ## Usage in AtomStore
 *
 * The [AtomStore] uses the registry to resolve factories:
 *
 * ```kotlin
 * @Composable
 * inline fun <reified A : AtomLifecycle> atom(key: Any? = null, params: Any = Unit): A {
 *     val type = A::class
 *     val registry = LocalAtomFactories.current
 *
 *     val entry = registry.entryFor(type)
 *         ?: error("No AtomFactoryEntry for ${type.simpleName}. Add @AutoAtom annotation.")
 *
 *     // Use entry to create atom...
 * }
 * ```
 *
 * ## Multi-Module Setup
 *
 * Each module generates its own registry. Compose these into a single registry:
 *
 * ```kotlin
 * class CompositeAtomFactoryRegistry(
 *     private val registries: List<AtomFactoryRegistry>
 * ) : AtomFactoryRegistry {
 *     override fun entryFor(type: KClass<out AtomLifecycle>): AnyAtomFactoryEntry<out AtomLifecycle>? {
 *         for (registry in registries) {
 *             registry.entryFor(type)?.let { return it }
 *         }
 *         return null
 *     }
 * }
 *
 * // In app initialization
 * val appRegistry = CompositeAtomFactoryRegistry(
 *     listOf(
 *         FeatureAAtomFactoryRegistry,
 *         FeatureBAtomFactoryRegistry,
 *         CoreAtomFactoryRegistry
 *     )
 * )
 * ```
 *
 * ## Empty Registry
 *
 * [EmptyAtomFactoryRegistry] is used as a default when no factories are available:
 *
 * ```kotlin
 * val LocalAtomFactories = staticCompositionLocalOf<AtomFactoryRegistry> {
 *     EmptyAtomFactoryRegistry  // Returns null for all lookups
 * }
 * ```
 *
 * ## Error Handling
 *
 * When [entryFor] returns `null`, the atom runtime produces a helpful error:
 *
 * ```kotlin
 * val entry = registry.entryFor(TodoAtom::class)
 *     ?: error("No AtomFactoryEntry for TodoAtom. Ensure TodoAtom is annotated with @AutoAtom.")
 * ```
 *
 * ## Thread Safety
 *
 * Registry implementations must be thread-safe. The generated implementation uses an immutable
 * `Map`, making it inherently thread-safe.
 *
 * ## Debugging
 *
 * To debug registry issues, check:
 * 1. The atom is annotated with `@AutoAtom`
 * 2. The KSP processor ran successfully (check build output)
 * 3. The generated registry is included in the Compose composition
 *
 * @see AnyAtomFactoryEntry for the factory entry type
 * @see EmptyAtomFactoryRegistry for the default empty registry
 * @see dev.mattramotar.atom.runtime.compose.LocalAtomFactories for Compose integration
 */
interface AtomFactoryRegistry {
    /**
     * Resolves the factory entry for the given atom type.
     *
     * @param type The atom's `KClass`
     * @return The factory entry, or `null` if no factory is registered for this type
     */
    fun entryFor(type: KClass<out AtomLifecycle>): AnyAtomFactoryEntry<out AtomLifecycle>?
}
