package dev.mattramotar.atom.runtime.factory

import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.serialization.KotlinxStateSerializer
import dev.mattramotar.atom.runtime.serialization.StateSerializer
import dev.mattramotar.atom.runtime.state.StateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Builder function for creating [AtomFactoryEntry] instances with reified type parameters.
 *
 * This inline function simplifies factory creation by inferring type parameters from lambdas
 * and automatically creating the `KClass` instances. It's the primary way generated code creates
 * factory entries.
 *
 * ## Usage in Generated Code
 *
 * The KSP processor generates factory entries using this builder:
 *
 * ```kotlin
 * val TodoAtom_Entry = atomFactory<TodoAtom, TodoState, TodoAtomParams>(
 *     create = { scope, handle, params ->
 *         val repository = container.resolve<TodoRepository>()
 *         TodoAtom(scope, handle, params, repository)
 *     },
 *     initial = { params -> TodoAtom.initial(params) },
 *     serializer = KotlinxStateSerializer(TodoState.serializer())
 * )
 * ```
 *
 * This is equivalent to the more verbose:
 *
 * ```kotlin
 * val TodoAtom_Entry = AtomFactoryEntry(
 *     atomClass = TodoAtom::class,
 *     typedStateClass = TodoState::class,
 *     paramsClass = TodoAtomParams::class,
 *     create = { scope, handle, params ->
 *         val repository = container.resolve<TodoRepository>()
 *         TodoAtom(scope, handle, params, repository)
 *     },
 *     initial = { params -> TodoAtom.initial(params) },
 *     serializer = KotlinxStateSerializer(TodoState.serializer())
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
 * ## Default Serializer
 *
 * The [serializer] parameter defaults to [KotlinxStateSerializer] with an auto-generated
 * `KSerializer<S>`. This works when:
 * - `kotlinx.serialization` is in the classpath
 * - The state type `S` is annotated with `@Serializable`
 *
 * For non-serializable states, explicitly pass `serializer = null`:
 *
 * ```kotlin
 * val TransientAtom_Entry = atomFactory<TransientAtom, TransientState, Unit>(
 *     create = { scope, handle, _ -> TransientAtom(scope, handle) },
 *     initial = { TransientState() },
 *     serializer = null  // No serialization
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
 * val TodoAtom_Entry = atomFactory<TodoAtom, TodoState, TodoAtomParams>(
 *     create = { scope, handle, params -> TodoAtom(scope, handle, params) },
 *     initial = { params -> TodoAtom.initial(params) },
 *     serializer = EncryptedTodoSerializer()
 * )
 * ```
 *
 * ## Manual Usage
 *
 * While primarily used by generated code, you can manually create factories:
 *
 * ```kotlin
 * class ManualTodoAtom(scope: CoroutineScope, handle: StateHandle<TodoState>) :
 *     Atom<TodoState, TodoIntent, TodoEvent, TodoEffect>(scope, handle) {
 *     companion object {
 *         fun initial(): TodoState = TodoState()
 *     }
 * }
 *
 * val ManualTodoAtom_Entry = atomFactory<ManualTodoAtom, TodoState, Unit>(
 *     create = { scope, handle, _ -> ManualTodoAtom(scope, handle) },
 *     initial = { ManualTodoAtom.initial() }
 * )
 * ```
 *
 * ## Thread Safety
 *
 * The created [AtomFactoryEntry] is immutable and thread-safe. The provided lambdas should
 * also be thread-safe (typically stateless or capturing immutable state).
 *
 * @param A The atom type
 * @param S The state type
 * @param P The params type
 * @param create Lambda that creates the atom
 * @param initial Lambda that computes initial state
 * @param serializer State serializer (defaults to kotlinx.serialization-based serializer)
 * @return A new [AtomFactoryEntry] instance
 *
 * @see AtomFactoryEntry for the created type
 * @see KotlinxStateSerializer for the default serializer
 * @see StateSerializer for custom serialization
 */
@OptIn(InternalSerializationApi::class)
inline fun <reified A : AtomLifecycle, reified S : Any, reified P : Any> atomFactory(
    noinline create: (CoroutineScope, StateHandle<S>, P) -> A,
    noinline initial: (P) -> S,
    serializer: StateSerializer<S> = KotlinxStateSerializer(S::class.serializer())
): AtomFactoryEntry<A, S, P> = AtomFactoryEntry(
    A::class,
    S::class,
    P::class,
    create,
    initial,
    serializer
)
