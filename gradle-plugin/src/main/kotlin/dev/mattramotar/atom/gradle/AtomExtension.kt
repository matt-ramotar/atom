package dev.mattramotar.atom.gradle

import org.gradle.api.provider.Property

/**
 * Extension for configuring the Atom state management framework.
 *
 * **Sample Usage**:
 *
 * ```kotlin
 * plugins {
 *     id("dev.mattramotar.atom")
 * }
 *
 * atom {
 *     di = DI.METRO
 *     compose = true
 *     strict = false
 *     scope = "com.uber.android.scopes.ActiveScope"
 *     injectAnnotation = "dev.zacsweers.metro.Inject"
 * }
 * ```
 *
 * @property version Override the Atom library version. Defaults to the same version as the plugin.
 * @property di DI framework integration. Defaults to [DI.MANUAL].
 * @property compose Whether to generate `remember*Atom()` composable functions. Defaults to `true`.
 * @property strict Whether to enable build cache workarounds for Metro layered annotation processing. Defaults to `false`.
 * @property scope DI scope class. Defaults to `dev.zacsweers.metro.AppScope` if [DI.METRO].
 * @property injectAnnotation Inject annotation for factory constructors. Defaults to `dev.zacsweers.metro.Inject` if [DI.METRO].
 */
abstract class AtomExtension {

    /**
     * Atom library version to use.
     */
    abstract val version: Property<String>

    /**
     * DI framework integration. Defaults to [DI.MANUAL].
     */
    abstract val di: Property<DI>

    /**
     * Whether to generate `remember*Atom()` composable functions. Defaults to `true`.
     */
    abstract val compose: Property<Boolean>

    /**
     * Whether to enable build cache workarounds for Metro layered annotation processing. Defaults to `false`.
     */
    abstract val strict: Property<Boolean>

    /**
     * DI scope class. Defaults to `dev.zacsweers.metro.AppScope` if [DI.METRO].
     */
    abstract val scope: Property<String>

    /**
     * Inject annotation for factory constructors. Defaults to `dev.zacsweers.metro.Inject` if [DI.METRO].
     */
    abstract val injectAnnotation: Property<String>

    init {
        di.convention(DI.MANUAL)
        compose.convention(true)
        strict.convention(false)
        scope.convention("dev.zacsweers.metro.AppScope")
        injectAnnotation.convention("dev.zacsweers.metro.Inject")
    }
}
