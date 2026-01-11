package dev.mattramotar.atom.runtime.factory

import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.serialization.KotlinxStateSerializer
import dev.mattramotar.atom.runtime.serialization.StateSerializer
import dev.mattramotar.atom.runtime.state.StateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

/**
 * Factory methods for creating [AtomFactoryEntry] instances.
 *
 * [Atoms] provides builder functions that simplify factory creation by inferring type parameters
 * from lambdas and automatically creating `KClass` instances. This is the primary API for both
 * generated code and manual factory creation.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * val TodoAtom_Entry = Atoms.factory<TodoAtom, TodoState, TodoAtomParams>(
 *     create = { scope, handle, params ->
 *         val repository = container.resolve<TodoRepository>()
 *         TodoAtom(scope, handle, params, repository)
 *     },
 *     initial = { params -> TodoAtom.initial(params) }
 * )
 * ```
 *
 * ## Type Inference
 *
 * The reified type parameters `A`, `S`, and `P` are inferred from the lambda signatures:
 * - `A` from `create`'s return type
 * - `S` from `handle`'s type parameter and `initial`'s return type
 * - `P` from `params` and `initial`'s parameter type
 *
 * ## Serializer Handling
 *
 * The [factory] function has two overloads:
 *
 * ### Auto-Detection (no serializer parameter)
 *
 * When you omit the `serializer` parameter, automatic detection is used:
 * - If `S` is annotated with `@Serializable`, uses [KotlinxStateSerializer]
 * - If `S` is NOT serializable, falls back to `null` (in-memory only)
 *
 * ```kotlin
 * // Auto-detects serializer for @Serializable states
 * val TodoAtom_Entry = Atoms.factory<TodoAtom, TodoState, Unit>(
 *     create = { scope, handle, _ -> TodoAtom(scope, handle) },
 *     initial = { TodoState() }
 * )
 *
 * // Works with non-serializable states too (falls back to null)
 * val TransientAtom_Entry = Atoms.factory<TransientAtom, TransientState, Unit>(
 *     create = { scope, handle, _ -> TransientAtom(scope, handle) },
 *     initial = { TransientState() }
 * )
 * ```
 *
 * ### Explicit Serializer (with serializer parameter)
 *
 * When you provide the `serializer` parameter, that exact value is used:
 *
 * ```kotlin
 * // Explicitly disable serialization (even for @Serializable states)
 * val Entry = Atoms.factory<MyAtom, MySerializableState, Unit>(
 *     create = { scope, handle, _ -> MyAtom(scope, handle) },
 *     initial = { MySerializableState() },
 *     serializer = null
 * )
 * ```
 *
 * ## Custom Serializers
 *
 * For custom serialization logic, provide a custom [StateSerializer]:
 *
 * ```kotlin
 * class EncryptedTodoSerializer : StateSerializer<TodoState> {
 *     override fun serialize(value: TodoState): String = encrypt(Json.encodeToString(value))
 *     override fun deserialize(text: String): TodoState = Json.decodeFromString(decrypt(text))
 * }
 *
 * val TodoAtom_Entry = Atoms.factory<TodoAtom, TodoState, TodoAtomParams>(
 *     create = { scope, handle, params -> TodoAtom(scope, handle, params) },
 *     initial = { params -> TodoAtom.initial(params) },
 *     serializer = EncryptedTodoSerializer()
 * )
 * ```
 *
 * ## Thread Safety
 *
 * The created [AtomFactoryEntry] instances are immutable and thread-safe. The provided lambdas
 * should also be thread-safe (typically stateless or capturing immutable state).
 *
 * @see AtomFactoryEntry for the created type
 * @see KotlinxStateSerializer for the default serializer
 * @see StateSerializer for custom serialization
 */
object Atoms {

    /**
     * Creates an [AtomFactoryEntry] with automatic serializer detection.
     *
     * If state type `S` is annotated with `@Serializable`, uses [KotlinxStateSerializer].
     * Otherwise, falls back to `null` (in-memory only state).
     *
     * @param A The atom type
     * @param S The state type
     * @param P The params type
     * @param create Lambda that creates the atom instance
     * @param initial Lambda that computes initial state from params
     * @return A new [AtomFactoryEntry] instance
     */
    @OptIn(InternalSerializationApi::class)
    inline fun <reified A : AtomLifecycle, reified S : Any, reified P : Any> factory(
        noinline create: (CoroutineScope, StateHandle<S>, P) -> A,
        noinline initial: (P) -> S
    ): AtomFactoryEntry<A, S, P> {
        val serializer = runCatching {
            KotlinxStateSerializer(S::class.serializer())
        }.getOrNull()

        return AtomFactoryEntry(
            A::class,
            S::class,
            P::class,
            create,
            initial,
            serializer
        )
    }

    /**
     * Creates an [AtomFactoryEntry] with an explicit serializer.
     *
     * Use this overload to provide a custom [StateSerializer] or to explicitly
     * disable serialization by passing `null`.
     *
     * @param A The atom type
     * @param S The state type
     * @param P The params type
     * @param create Lambda that creates the atom instance
     * @param initial Lambda that computes initial state from params
     * @param serializer The serializer to use, or `null` to disable serialization
     * @return A new [AtomFactoryEntry] instance
     */
    inline fun <reified A : AtomLifecycle, reified S : Any, reified P : Any> factory(
        noinline create: (CoroutineScope, StateHandle<S>, P) -> A,
        noinline initial: (P) -> S,
        serializer: StateSerializer<S>?
    ): AtomFactoryEntry<A, S, P> = AtomFactoryEntry(
        A::class,
        S::class,
        P::class,
        create,
        initial,
        serializer
    )
}
