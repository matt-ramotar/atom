package dev.mattramotar.atom.runtime.di

/**
 * Resolves a dependency by reified type parameter.
 *
 * This is a convenience extension that allows calling `resolve<T>()` instead of
 * `resolve(T::class)`, leveraging Kotlin's reified type parameters for cleaner syntax.
 *
 * ## Usage
 *
 * ```kotlin
 * val container: AtomContainer = ...
 *
 * // With reified extension
 * val repository = container.resolve<TodoRepository>()
 * val logger = container.resolve<Logger>(qualifier = "analytics")
 *
 * // Without reified extension (more verbose)
 * val repository = container.resolve(TodoRepository::class)
 * val logger = container.resolve(Logger::class, qualifier = "analytics")
 * ```
 *
 * ## Type Inference
 *
 * The compiler infers `T` from the call site's expected type:
 *
 * ```kotlin
 * val repo: TodoRepository = container.resolve()  // T inferred as TodoRepository
 * val logger: Logger = container.resolve("analytics")  // T inferred as Logger
 * ```
 *
 * ## Generated Code
 *
 * The KSP processor uses this extension in generated factory code:
 *
 * ```kotlin
 * class MyAtom_Factory(private val container: AtomContainer) {
 *     override fun create(...): MyAtom {
 *         val repository = container.resolve<Repository>()  // Uses this extension
 *         val logger = container.resolve<Logger>("analytics")
 *         return MyAtom(scope, handle, repository, logger)
 *     }
 * }
 * ```
 *
 * @param T The dependency type to resolve (inferred from call site)
 * @param qualifier Optional name for disambiguating multiple instances of the same type
 * @return The resolved dependency instance
 * @throws IllegalStateException if the dependency is not registered
 *
 * @see AtomContainer.resolve for the underlying resolution method
 */
inline fun <reified T : Any> AtomContainer.resolve(qualifier: String? = null): T =
    resolve(T::class, qualifier)
