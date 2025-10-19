package dev.mattramotar.atom.runtime.annotations

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

/**
 * Qualifies a constructor dependency for name-based disambiguation in dependency injection.
 *
 * When multiple instances of the same type are registered in the DI container, [AtomQualifier]
 * allows the KSP processor to generate the correct resolution logic by name. This is particularly
 * useful for:
 * - Multiple repository instances (e.g., local vs remote)
 * - Environment-specific configurations (e.g., dev vs prod API clients)
 * - Scoped dependencies with different lifecycles
 *
 * ## Usage
 *
 * Apply [AtomQualifier] to constructor parameters that require name-based resolution:
 *
 * ```kotlin
 * @AutoAtom
 * class TodoAtom(
 *     scope: CoroutineScope,
 *     handle: StateHandle<TodoState>,
 *     params: TodoAtomParams,
 *     @AtomQualifier("local") localRepo: TodoRepository,  // Resolves "local" TodoRepository
 *     @AtomQualifier("remote") remoteRepo: TodoRepository, // Resolves "remote" TodoRepository
 *     @AtomQualifier("analytics") logger: Logger           // Resolves "analytics" Logger
 * ) : Atom<TodoState, TodoIntent, TodoEvent, TodoEffect>(scope, handle) {
 *     // Implementation uses both local and remote repositories
 * }
 * ```
 *
 * ## DI Framework Integration
 *
 * The generated code translates qualifiers into framework-specific resolution:
 *
 * ### Metro DI
 * ```kotlin
 * // Generated factory code
 * class TodoAtom_Factory @Inject constructor(
 *     @Named("local") private val localRepo: TodoRepository,
 *     @Named("remote") private val remoteRepo: TodoRepository,
 *     @Named("analytics") private val logger: Logger
 * )
 * ```
 *
 * ### Koin
 * ```kotlin
 * // Generated factory code
 * class TodoAtom_Factory(private val container: AtomContainer) {
 *     fun create(...): TodoAtom {
 *         val localRepo = container.resolve<TodoRepository>(qualifier = "local")
 *         val remoteRepo = container.resolve<TodoRepository>(qualifier = "remote")
 *         val logger = container.resolve<Logger>(qualifier = "analytics")
 *         return TodoAtom(scope, handle, params, localRepo, remoteRepo, logger)
 *     }
 * }
 * ```
 *
 * ### Hilt (future support)
 * ```kotlin
 * // Generated factory code
 * class TodoAtom_Factory @Inject constructor(
 *     @Named("local") private val localRepo: TodoRepository,
 *     @Named("remote") private val remoteRepo: TodoRepository,
 *     @Named("analytics") private val logger: Logger
 * )
 * ```
 *
 * ## Qualifier Registration
 *
 * Ensure dependencies are registered with matching qualifiers:
 *
 * ### Metro DI
 * ```kotlin
 * @Provides
 * @Named("local")
 * @IntoScope(AppScope::class)
 * fun provideLocalRepository(): TodoRepository = LocalTodoRepository()
 *
 * @Provides
 * @Named("remote")
 * @IntoScope(AppScope::class)
 * fun provideRemoteRepository(): TodoRepository = RemoteTodoRepository()
 * ```
 *
 * ### Koin
 * ```kotlin
 * module {
 *     single<TodoRepository>(named("local")) { LocalTodoRepository() }
 *     single<TodoRepository>(named("remote")) { RemoteTodoRepository() }
 * }
 * ```
 *
 * ## Best Practices
 *
 * - **Use semantic names**: Prefer descriptive qualifiers like "local", "remote", "cached"
 *   over generic names like "repo1", "repo2"
 * - **Consistent naming**: Use the same qualifier names across your codebase
 * - **Avoid over-qualification**: Only qualify when truly necessary - most dependencies
 *   don't require qualifiers
 * - **Document qualifiers**: Add KDoc to constructor parameters explaining why the qualifier
 *   is needed and what it resolves to
 *
 * ## Common Patterns
 *
 * ### Repository Pattern
 * ```kotlin
 * @AtomQualifier("local") localSource: DataSource,
 * @AtomQualifier("remote") remoteSource: DataSource
 * ```
 *
 * ### Environment-Specific
 * ```kotlin
 * @AtomQualifier("prod") prodClient: ApiClient,
 * @AtomQualifier("dev") devClient: ApiClient
 * ```
 *
 * ### Scoped Dependencies
 * ```kotlin
 * @AtomQualifier("session") sessionLogger: Logger,
 * @AtomQualifier("global") globalLogger: Logger
 * ```
 *
 * @property name The qualifier name used for DI resolution. Must match the name used when
 *                registering the dependency in the DI container.
 *
 * @see AutoAtom for atom code generation configuration
 */
@Retention(BINARY)
@Target(VALUE_PARAMETER)
annotation class AtomQualifier(val name: String)
