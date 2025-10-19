package dev.mattramotar.atom.runtime.annotations

/**
 * Marker object indicating that the Metro DI scope should be inferred from convention plugin configuration.
 *
 * [InferredScope] is used as the default value for [AutoAtom.scope], allowing the Atom convention
 * plugin's configured `metroScope` to determine the DI scope without requiring explicit annotation
 * parameters. This is only relevant when using Metro DI (`atom.di=metro`).
 *
 * ## How Scope Inference Works
 *
 * The convention plugin (typically applied in `build.gradle.kts`) configures a default Metro scope:
 *
 * ```kotlin
 * // build.gradle.kts
 * atom {
 *     di = "metro"
 *     metroScope = "com.example.di.AppScope"  // Default scope for all atoms
 * }
 * ```
 *
 * Atoms without an explicit scope use this configured default:
 *
 * ```kotlin
 * @AutoAtom  // scope = InferredScope::class (default)
 * class TodoAtom(...) : Atom<TodoState, ...> {
 *     // Generated code contributes to AppScope (from convention plugin config)
 * }
 * ```
 *
 * Generated Metro module:
 * ```kotlin
 * @Provides
 * @IntoScope(AppScope::class)  // Uses inferred scope from plugin config
 * fun provideTodoAtom_Factory(...): TodoAtom_Factory = TodoAtom_Factory(...)
 * ```
 *
 * ## When to Override with Explicit Scope
 *
 * While the inferred scope is suitable for most atoms, explicit scopes are necessary for:
 *
 * ### Scoped Lifecycle Management
 * ```kotlin
 * // App-wide configuration (survives entire app lifecycle)
 * @AutoAtom(AppScope::class)
 * class AppConfigAtom(...) : Atom<AppConfigState, ...>
 *
 * // Session-scoped (disposed on logout)
 * @AutoAtom(LoggedInScope::class)
 * class UserProfileAtom(...) : Atom<UserProfileState, ...>
 *
 * // Feature-scoped (disposed when leaving feature)
 * @AutoAtom(CheckoutScope::class)
 * class ShoppingCartAtom(...) : Atom<CartState, ...>
 * ```
 *
 * ### Mixed Scopes in Same Module
 * ```kotlin
 * // Different atoms in the same module targeting different scopes
 *
 * @AutoAtom(AppScope::class)  // Global
 * class ThemeAtom(...) : Atom<ThemeState, ...>
 *
 * @AutoAtom(ActiveScope::class)  // Session-specific
 * class NotificationAtom(...) : Atom<NotificationState, ...>
 * ```
 *
 * ## Non-Metro DI Frameworks
 *
 * [InferredScope] is ignored when `atom.di != "metro"`:
 *
 * ```kotlin
 * // build.gradle.kts
 * atom {
 *     di = "koin"  // InferredScope has no effect
 * }
 * ```
 *
 * For Koin, Hilt, or manual DI, scope management is handled by the DI framework itself,
 * not via [AutoAtom.scope].
 *
 * ## Validation
 *
 * The KSP processor validates that:
 * - The inferred scope class exists and is accessible
 * - The scope is a valid Metro scope (when `atom.di=metro`)
 *
 * Validation failures produce compile-time errors with guidance on fixing the configuration.
 *
 * ## Convention Over Configuration
 *
 * [InferredScope] embodies the "convention over configuration" philosophy:
 * - **Default case**: Most atoms use the module's default scope (no annotation parameter needed)
 * - **Exceptional case**: Atoms requiring specific scopes explicitly declare them
 *
 * This reduces boilerplate for the common case while preserving flexibility.
 *
 * ## Implementation Details
 *
 * [InferredScope] is a stateless marker object used only at compile time. The KSP processor
 * detects `InferredScope::class` and substitutes the convention plugin's configured `metroScope`.
 * At runtime, [InferredScope] is never instantiated or referenced.
 *
 * ## Thread Safety
 *
 * [InferredScope] is a stateless marker object - no thread safety concerns.
 *
 * @see AutoAtom.scope for Metro DI scope configuration
 * @see InferredType for type inference
 */
object InferredScope
