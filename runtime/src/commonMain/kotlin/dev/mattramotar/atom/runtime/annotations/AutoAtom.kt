package dev.mattramotar.atom.runtime.annotations

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Marks an Atom class as a target for code generation.
 *
 * The KSP processor generates compile-time infrastructure for atoms annotated with [AutoAtom]:
 * - Factory class for creating atom instances with dependency injection
 * - Type-erased registry entry for runtime atom resolution
 * - Compose helper functions for seamless UI integration (when enabled)
 * - DI module contributions for Metro or other frameworks (when configured)
 *
 * ## Architecture
 *
 * [AutoAtom] enables a declarative, annotation-driven approach to atom creation:
 * - **Type inference**: State, intent, event, and effect types are automatically inferred from
 *   the `Atom<S, I, E, F>` supertype, reducing boilerplate
 * - **Params extraction**: Parameter types are inferred from the `@InitialState` function signature
 * - **Validation**: Compile-time verification ensures declared types match actual implementation
 * - **Scoping**: Metro DI scopes can be specified for modular, lifecycle-aware atom management
 *
 * ## Minimal Usage (Recommended)
 *
 * The simplest form relies entirely on type inference:
 *
 * ```kotlin
 * @AutoAtom  // All types inferred from Atom<S, I, E, F> supertype
 * class TodoAtom(
 *     scope: CoroutineScope,
 *     handle: StateHandle<TodoState>,
 *     params: TodoAtomParams
 * ) : Atom<TodoState, TodoIntent, TodoEvent, TodoEffect>(scope, handle) {
 *     companion object {
 *         @InitialState
 *         fun initial(params: TodoAtomParams): TodoState = TodoState(
 *             todo = params.initialTodo,
 *             loading = false
 *         )
 *     }
 *
 *     override fun reduce(state: TodoState, event: TodoEvent): Transition<TodoState, TodoEffect> {
 *         // FSM reducer implementation
 *     }
 * }
 * ```
 *
 * ## Metro Scoping (Most Common in Production)
 *
 * Specify Metro DI scopes to control atom lifecycle and visibility:
 *
 * ```kotlin
 * // Scoped to active session - disposed when user logs out
 * @AutoAtom(ActiveScope::class)
 * class PostListAtom(...) : Atom<PostListState, ...>
 *
 * // Scoped to logged-in user - survives navigation
 * @AutoAtom(LoggedInScope::class)
 * class UserProfileAtom(...) : Atom<UserProfileState, ...>
 *
 * // App-wide singleton - survives entire app lifecycle
 * @AutoAtom(AppScope::class)
 * class AppConfigAtom(...) : Atom<AppConfigState, ...>
 * ```
 *
 * ## Explicit Types (Optional)
 *
 * Explicitly declare types when inference is ambiguous or for documentation clarity:
 *
 * ```kotlin
 * @AutoAtom(
 *     state = TodoState::class,
 *     params = TodoAtomParams::class
 * )
 * class TodoAtom(...) : Atom<TodoState, ...>
 * ```
 *
 * Explicit types are validated against the class signature at compile time, catching mismatches early.
 *
 * ## State Persistence
 *
 * Enable automatic state serialization for process death recovery:
 *
 * ```kotlin
 * @AutoAtom(serializer = KotlinxStateSerializer::class)
 * class TodoAtom(...) : Atom<TodoState, ...>  // TodoState must be @Serializable
 * ```
 *
 * ## Generated Code
 *
 * For an atom `FooAtom`, the processor generates:
 * - `FooAtom_Factory`: Factory with DI-injected dependencies
 * - `FooAtom_Entry`: Type-erased registry entry for runtime resolution
 * - `rememberFooAtom()`: Compose function for UI integration (when enabled)
 * - Metro module contributions (when `atom.di=metro`)
 *
 * @param scope The Metro DI scope for this atom (Metro mode only). Defaults to the convention
 *              plugin's configured `metroScope` (typically `AppScope`). Different atoms in the
 *              same module can target different scopes. This is the most commonly specified
 *              parameter - use positional syntax: `@AutoAtom(ActiveScope::class)`.
 *
 * @param state The state type `S`. If not specified (default: [InferredType]), inferred from
 *              the `Atom<S, I, E, F>` supertype. When specified explicitly, must match the
 *              actual `S` type parameter - mismatches are caught at compile time.
 *
 * @param params The params type `P`. If not specified (default: [InferredType]), inferred from
 *               the `@InitialState` function signature (`fun initial(params: P): S`). Use
 *               `Unit::class` for atoms that don't require parameters. Explicitly specifying
 *               params validates the `@InitialState` function signature.
 *
 * @param serializer The state serializer for persistence and process death recovery. Defaults to
 *                   [AutoSerializer], which auto-detects kotlinx.serialization support. Specify
 *                   a custom `StateSerializer<S>` implementation for advanced serialization needs
 *                   (e.g., custom JSON formats, encryption, compression).
 *
 * @see InitialState for the companion function that provides initial state
 * @see AtomQualifier for disambiguating injected dependencies by name
 */
@Retention(BINARY)
@Target(CLASS)
annotation class AutoAtom(
    val scope: KClass<*> = InferredScope::class,
    val state: KClass<*> = InferredType::class,
    val params: KClass<*> = InferredType::class,
    val serializer: KClass<*> = AutoSerializer::class
)
