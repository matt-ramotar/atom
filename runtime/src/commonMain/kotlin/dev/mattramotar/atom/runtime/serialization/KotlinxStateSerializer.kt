package dev.mattramotar.atom.runtime.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * kotlinx.serialization-based implementation of [StateSerializer].
 *
 * [KotlinxStateSerializer] uses kotlinx.serialization for JSON-based state persistence:
 * - **Type-safe**: Leverages compile-time generated serializers
 * - **Configurable**: Customize JSON format via [Json] parameter
 * - **Multiplatform**: Works on all Kotlin Multiplatform targets
 *
 * ## Usage
 *
 * Auto-detected when state is `@Serializable`:
 *
 * ```kotlin
 * @Serializable
 * data class TodoState(...)
 *
 * @AutoAtom  // KotlinxStateSerializer auto-configured
 * class TodoAtom(...) : Atom<TodoState, ...>
 * ```
 *
 * Or explicitly specified:
 *
 * ```kotlin
 * @AutoAtom(serializer = KotlinxStateSerializer::class)
 * class TodoAtom(...) : Atom<TodoState, ...>
 * ```
 *
 * ## Custom JSON Configuration
 *
 * Provide a custom [Json] instance:
 *
 * ```kotlin
 * val customSerializer = KotlinxStateSerializer(
 *     serializer = TodoState.serializer(),
 *     json = Json {
 *         prettyPrint = true
 *         ignoreUnknownKeys = true
 *         encodeDefaults = false
 *     }
 * )
 * ```
 *
 * ## Default JSON Settings
 *
 * [DefaultJson] provides sensible defaults:
 * - `ignoreUnknownKeys = true`: Forward compatibility
 * - `encodeDefaults = true`: Explicit state representation
 *
 * @param serializer The kotlinx.serialization `KSerializer<S>`
 * @param json The JSON instance (defaults to [DefaultJson])
 *
 * @see StateSerializer for the interface
 */
class KotlinxStateSerializer<S : Any>(
    private val serializer: KSerializer<S>,
    private val json: Json = DefaultJson
) : StateSerializer<S> {
    override fun serialize(value: S): String = json.encodeToString(serializer, value)
    override fun deserialize(text: String): S = json.decodeFromString(serializer, text)

    companion object {
        /**
         * Default JSON configuration for state serialization.
         */
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
