package dev.mattramotar.atom.runtime.serialization

/**
 * Interface for serializing and deserializing atom state.
 *
 * [StateSerializer] enables state persistence for process death recovery, supporting:
 * - **Serialization**: Converting state to a string representation
 * - **Deserialization**: Reconstructing state from a string
 * - **Custom formats**: JSON, Protocol Buffers, custom binary formats, etc.
 *
 * ## Implementations
 *
 * - [KotlinxStateSerializer]: kotlinx.serialization-based JSON serialization
 * - Custom serializers: Encryption, compression, binary formats, etc.
 *
 * ## Usage
 *
 * Serializers are provided via `@AutoAtom(serializer = ...)`:
 *
 * ```kotlin
 * @AutoAtom(serializer = KotlinxStateSerializer::class)
 * class TodoAtom(...) : Atom<TodoState, ...>
 * ```
 *
 * Or auto-detected via [dev.mattramotar.atom.runtime.annotations.AutoSerializer]:
 *
 * ```kotlin
 * @Serializable
 * data class TodoState(...)
 *
 * @AutoAtom  // AutoSerializer detects @Serializable
 * class TodoAtom(...) : Atom<TodoState, ...>
 * ```
 *
 * ## Thread Safety
 *
 * Implementations must be thread-safe. Serialization may occur concurrently.
 *
 * @param S The state type
 *
 * @see KotlinxStateSerializer for kotlinx.serialization implementation
 */
interface StateSerializer<S : Any> {
    /**
     * Serializes state to a string.
     *
     * @param value The state to serialize
     * @return String representation of the state
     */
    fun serialize(value: S): String

    /**
     * Deserializes state from a string.
     *
     * @param text String representation of the state
     * @return The deserialized state
     */
    fun deserialize(text: String): S
}
