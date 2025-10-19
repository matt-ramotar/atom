package dev.mattramotar.atom.runtime.annotations

/**
 * Marker object indicating automatic serializer detection for atom state persistence.
 *
 * When specified as the [AutoAtom.serializer] parameter (which is the default), the KSP processor
 * attempts to automatically detect and configure state serialization based on the available
 * serialization libraries and state type annotations.
 *
 * ## Detection Strategy
 *
 * The processor performs the following checks in order:
 *
 * 1. **kotlinx.serialization**: If the state type is annotated with `@Serializable`, generates
 *    code using `KotlinxStateSerializer` with the auto-generated `KSerializer`
 *
 * 2. **No serialization**: If no serialization library is detected, the generated factory
 *    uses `null` for the serializer, resulting in in-memory-only state (lost on process death)
 *
 * ## kotlinx.serialization (Recommended)
 *
 * The most common use case is with kotlinx.serialization:
 *
 * ```kotlin
 * @Serializable
 * data class TodoState(
 *     val todos: List<Todo>,
 *     val loading: Boolean,
 *     val error: String? = null
 * )
 *
 * @AutoAtom  // serializer = AutoSerializer::class is the default
 * class TodoAtom(
 *     scope: CoroutineScope,
 *     handle: StateHandle<TodoState>,
 *     params: TodoAtomParams
 * ) : Atom<TodoState, TodoIntent, TodoEvent, TodoEffect>(scope, handle) {
 *     // Generated factory automatically uses KotlinxStateSerializer<TodoState>
 * }
 * ```
 *
 * Generated code:
 * ```kotlin
 * class TodoAtom_Entry : AtomFactoryEntry<TodoAtom, TodoState, TodoAtomParams>(
 *     atomClass = TodoAtom::class,
 *     stateClass = TodoState::class,
 *     paramsClass = TodoAtomParams::class,
 *     create = { scope, handle, params -> TodoAtom(scope, handle, params) },
 *     initial = { params -> TodoAtom.initial(params) },
 *     serializer = KotlinxStateSerializer(TodoState.serializer())  // Auto-detected
 * )
 * ```
 *
 * ## No Serialization
 *
 * For atoms that don't require state persistence (e.g., transient UI state):
 *
 * ```kotlin
 * data class TransientState(val isVisible: Boolean)
 *
 * @AutoAtom  // AutoSerializer detects no serialization support
 * class TransientAtom(...) : Atom<TransientState, ...> {
 *     // State is lost on process death - suitable for ephemeral UI state
 * }
 * ```
 *
 * Generated code:
 * ```kotlin
 * class TransientAtom_Entry : AtomFactoryEntry<TransientAtom, TransientState, Unit>(
 *     // ...
 *     serializer = null  // No serialization configured
 * )
 * ```
 *
 * ## Explicit Serialization
 *
 * For custom serialization requirements, specify an explicit serializer instead of [AutoSerializer]:
 *
 * ```kotlin
 * // Custom serializer with encryption
 * class EncryptedTodoSerializer : StateSerializer<TodoState> {
 *     override fun serialize(value: TodoState): String {
 *         val json = Json.encodeToString(value)
 *         return encrypt(json)
 *     }
 *
 *     override fun deserialize(text: String): TodoState {
 *         val json = decrypt(text)
 *         return Json.decodeFromString(json)
 *     }
 * }
 *
 * @AutoAtom(serializer = EncryptedTodoSerializer::class)
 * class SecureTodoAtom(...) : Atom<TodoState, ...> {
 *     // Uses custom encryption serializer
 * }
 * ```
 *
 * ## Limitations
 *
 * [AutoSerializer] detection has the following limitations:
 * - Only detects kotlinx.serialization (no Gson, Moshi, etc. auto-detection yet)
 * - Requires `@Serializable` annotation on the state type (doesn't infer from type hierarchy)
 * - Cannot auto-configure custom JSON settings (use explicit serializer for custom `Json` instances)
 *
 * ## Thread Safety
 *
 * [AutoSerializer] is a stateless marker object - no thread safety concerns.
 *
 * @see AutoAtom.serializer for configuring state serialization
 * @see dev.mattramotar.atom.runtime.serialization.StateSerializer for custom serialization
 * @see dev.mattramotar.atom.runtime.serialization.KotlinxStateSerializer for kotlinx.serialization implementation
 */
object AutoSerializer
