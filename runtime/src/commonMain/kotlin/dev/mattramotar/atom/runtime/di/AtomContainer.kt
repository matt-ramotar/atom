package dev.mattramotar.atom.runtime.di

import dev.mattramotar.atom.runtime.annotations.AtomQualifier
import dev.mattramotar.atom.runtime.compose.AtomDI
import dev.mattramotar.atom.runtime.compose.LocalAtomContainer
import kotlin.reflect.KClass

/**
 * Dependency injection abstraction for resolving dependencies in manually managed atom factories.
 *
 * [AtomContainer] provides a minimal, type-safe DI interface for resolving dependencies when
 * using non-Metro dependency injection frameworks (Koin, Hilt) or manual DI. This interface is
 * **not used in Metro mode** - Metro injects typed dependencies directly into factory constructors.
 *
 * ## Metro vs. Non-Metro DI
 *
 * ### Metro Mode (`atom.di="metro"`)
 * Generated factories receive typed dependencies via `@Inject` constructors:
 *
 * ```kotlin
 * // Generated factory (Metro mode)
 * class MyAtom_Factory @Inject constructor(
 *     private val repository: Repository,  // Typed dependency, injected by Metro
 *     @Named("analytics") private val logger: Logger
 * ) : AtomFactory<MyAtom, MyState, MyParams> {
 *     override fun create(scope: CoroutineScope, handle: StateHandle<MyState>, params: MyParams): MyAtom {
 *         return MyAtom(scope, handle, repository, logger, params)
 *     }
 * }
 * ```
 *
 * ### Non-Metro Mode (`atom.di="koin"`, `"hilt"`, or `"manual"`)
 * Generated factories receive an [AtomContainer] to resolve dependencies dynamically:
 *
 * ```kotlin
 * // Generated factory (Koin/Hilt/Manual mode)
 * class MyAtom_Factory(
 *     private val container: AtomContainer
 * ) : AtomFactory<MyAtom, MyState, MyParams> {
 *     override fun create(scope: CoroutineScope, handle: StateHandle<MyState>, params: MyParams): MyAtom {
 *         val repository = container.resolve<Repository>()
 *         val logger = container.resolve<Logger>(qualifier = "analytics")
 *         return MyAtom(scope, handle, repository, logger, params)
 *     }
 * }
 * ```
 *
 * ## Implementation Examples
 *
 * ### Koin Integration
 * ```kotlin
 * class KoinAtomContainer(private val koin: Koin) : AtomContainer {
 *     override fun <T : Any> resolve(type: KClass<T>, qualifier: String?): T {
 *         return if (qualifier != null) {
 *             koin.get(type, named(qualifier))
 *         } else {
 *             koin.get(type)
 *         }
 *     }
 * }
 *
 * // In app initialization
 * startKoin {
 *     modules(
 *         module {
 *             single { TodoRepository() }
 *             single<Logger>(named("analytics")) { AnalyticsLogger() }
 *             single<AtomContainer> { KoinAtomContainer(get()) }
 *         }
 *     )
 * }
 * ```
 *
 * ### Hilt Integration (future support)
 * ```kotlin
 * class HiltAtomContainer @Inject constructor(
 *     private val repository: Provider<Repository>,
 *     @Named("analytics") private val analyticsLogger: Provider<Logger>
 * ) : AtomContainer {
 *     override fun <T : Any> resolve(type: KClass<T>, qualifier: String?): T {
 *         // Map qualifiers to Hilt providers
 *         return when {
 *             type == Repository::class -> repository.get()
 *             type == Logger::class && qualifier == "analytics" -> analyticsLogger.get()
 *             else -> error("Unregistered dependency: ${type.simpleName} (qualifier=$qualifier)")
 *         } as T
 *     }
 * }
 * ```
 *
 * ### Manual DI
 * ```kotlin
 * class ManualAtomContainer(
 *     private val dependencies: Map<Pair<KClass<*>, String?>, Any>
 * ) : AtomContainer {
 *     override fun <T : Any> resolve(type: KClass<T>, qualifier: String?): T {
 *         val key = type to qualifier
 *         @Suppress("UNCHECKED_CAST")
 *         return dependencies[key] as? T
 *             ?: error("Dependency not found: ${type.simpleName} (qualifier=$qualifier)")
 *     }
 * }
 *
 * // In app initialization
 * val container = ManualAtomContainer(
 *     mapOf(
 *         (Repository::class to null) to TodoRepository(),
 *         (Logger::class to "analytics") to AnalyticsLogger()
 *     )
 * )
 * ```
 *
 * ## Compose Integration
 *
 * Provide the container via `LocalAtomContainer`:
 *
 * ```kotlin
 * @Composable
 * fun App() {
 *     val container = remember { KoinAtomContainer(getKoin()) }
 *
 *     AtomDI(container = container) {
 *         // Atoms created here can resolve dependencies from container
 *         AppContent()
 *     }
 * }
 * ```
 *
 * ## Qualifiers
 *
 * Use [AtomQualifier] on constructor parameters to request named dependencies:
 *
 * ```kotlin
 * @AutoAtom
 * class MyAtom(
 *     scope: CoroutineScope,
 *     handle: StateHandle<MyState>,
 *     @AtomQualifier("local") localRepo: Repository,
 *     @AtomQualifier("remote") remoteRepo: Repository
 * ) : Atom<MyState, ...>
 * ```
 *
 * Generated factory code:
 * ```kotlin
 * override fun create(...): MyAtom {
 *     val localRepo = container.resolve<Repository>(qualifier = "local")
 *     val remoteRepo = container.resolve<Repository>(qualifier = "remote")
 *     return MyAtom(scope, handle, localRepo, remoteRepo)
 * }
 * ```
 *
 * ## Error Handling
 *
 * Implementations should throw clear exceptions for missing dependencies:
 *
 * ```kotlin
 * override fun <T : Any> resolve(type: KClass<T>, qualifier: String?): T {
 *     return dependencies[type to qualifier] as? T
 *         ?: throw IllegalStateException(
 *             "Dependency not registered: ${type.simpleName}" +
 *             if (qualifier != null) " (qualifier='$qualifier')" else ""
 *         )
 * }
 * ```
 *
 * ## Thread Safety
 *
 * [AtomContainer] implementations must be thread-safe. Atom factories may resolve dependencies
 * from multiple threads concurrently.
 *
 * @see AtomQualifier for named dependency resolution
 * @see LocalAtomContainer for Compose integration
 * @see AtomDI for providing the container in Compose
 */
interface AtomContainer {
    /**
     * Resolves a dependency by type and optional qualifier.
     *
     * **Thread Safety**: Must be safe to call from multiple threads concurrently.
     *
     * @param T The dependency type to resolve
     * @param type The dependency's `KClass`
     * @param qualifier Optional name for disambiguating multiple instances of the same type
     * @return The resolved dependency instance
     * @throws IllegalStateException if the dependency is not registered
     */
    fun <T : Any> resolve(type: KClass<T>, qualifier: String? = null): T
}
