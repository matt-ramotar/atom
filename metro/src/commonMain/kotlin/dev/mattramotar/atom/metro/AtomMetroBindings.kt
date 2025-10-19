package dev.mattramotar.atom.metro

import dev.zacsweers.metro.BindingContainer

/**
 * Binding container that exposes Metro-backed Atom infrastructure.
 *
 * Include this container along with the KSP-generated bindings in your graph:
 *
 *   @DependencyGraph(
 *       bindingContainers = [
 *           AtomMetroBindings::class,
 *           AtomMetroGeneratedBindings::class  // KSP-generated
 *       ]
 *   )
 *   interface AppGraph { ... }
 *
 * ## Implementation: Aggregator Provider Pattern
 *
 * To avoid Metro FIR issues with wildcard types and complex variance in @Provides
 * parameters, the KSP processor generates `AtomMetroGeneratedBindings` which uses
 * an aggregator provider pattern:
 *
 * - Each Atom gets a `X_Factory @Inject constructor(...)` with typed dependencies
 *   (no AtomContainer injection)
 * - Each Atom gets a `X_MetroEntry @Inject constructor(factory: X_Factory)` that
 *   contributes to the Metro graph via @ContributesIntoMap
 * - `AtomMetroGeneratedBindings` provides a `@Provides` function that takes each
 *   MetroEntry as individual typed parameters (no Map parameters with wildcards)
 *   and returns `AtomFactoryRegistry`
 *
 * This approach completely avoids the Metro FIR bugs with:
 * 1. Star projections: `Map<KClass<*>, Provider<*>>` (causes IllegalStateException)
 * 2. Complex variance: `Map<KClass<out T>, Provider<V<out T>>>` (causes MissingBinding)
 *
 * ## Usage
 *
 * The KSP processor automatically generates `AtomMetroGeneratedBindings` when
 * `atom.di=metro` is configured. Simply include it in your bindingContainers
 * as shown above.
 */
@BindingContainer
public object AtomMetroBindings {
    // This container is intentionally empty. All Atom-related bindings are generated
    // by KSP in AtomMetroGeneratedBindings to avoid Metro FIR wildcard/variance issues.
}
