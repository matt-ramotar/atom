package dev.mattramotar.atom.runtime

import dev.mattramotar.atom.runtime.store.AtomStore
import kotlin.reflect.KClass

/**
 * Unique identifier for atom instances within an [AtomStore].
 *
 * [AtomKey] combines an atom type with an optional instance key to uniquely identify and
 * retrieve atom instances. This enables:
 * - **Type-based resolution**: Retrieve the singleton instance of an atom type
 * - **Multi-instance management**: Manage multiple instances of the same atom type with different keys
 * - **Reference counting**: Track how many consumers are using each atom instance
 *
 * ## Structure
 *
 * An [AtomKey] consists of:
 * - [type]: The atom's `KClass` (e.g., `TodoAtom::class`)
 * - [instanceKey]: An optional key distinguishing instances of the same type (e.g., `userId`, `todoId`)
 *
 * ## Usage Patterns
 *
 * ### Singleton Atoms
 *
 * Most atoms are singletons - one instance per type:
 *
 * ```kotlin
 * @Composable
 * fun AppScreen() {
 *     val appConfig = atom<AppConfigAtom>()  // key = AtomKey(AppConfigAtom::class, null)
 *     // All calls to atom<AppConfigAtom>() return the same instance
 * }
 * ```
 *
 * ### Multi-Instance Atoms
 *
 * Some atoms require multiple instances distinguished by a key:
 *
 * ```kotlin
 * @Composable
 * fun TodoList(todos: List<TodoId>) {
 *     todos.forEach { todoId ->
 *         val todoAtom = atom<TodoAtom>(key = todoId)  // key = AtomKey(TodoAtom::class, todoId)
 *         // Different todoId → different TodoAtom instance
 *         TodoItem(atom = todoAtom)
 *     }
 * }
 * ```
 *
 * ### Child Atom Synchronization
 *
 * [AtomKey] enables managing a dynamic set of child atoms:
 *
 * ```kotlin
 * class PostListAtom(...) : Atom<PostListState, ...> {
 *     private val children = ChildAtomProvider(scope, stateHandleFactory, registry)
 *
 *     suspend fun syncPosts(posts: List<Post>) {
 *         // Sync creates/retains atoms for posts.map { it.id }, disposes others
 *         children.sync(
 *             type = PostAtom::class,
 *             items = posts.associateBy { it.id }.mapValues { (id, post) ->
 *                 PostAtomParams(postId = id, initialPost = post)
 *             }
 *         )
 *     }
 * }
 * ```
 *
 * ## Key Types
 *
 * The [instanceKey] can be any `Any?`:
 *
 * ### Primitive Keys
 * ```kotlin
 * atom<TodoAtom>(key = 42)        // AtomKey(TodoAtom::class, 42)
 * atom<UserAtom>(key = "alice")   // AtomKey(UserAtom::class, "alice")
 * ```
 *
 * ### Data Class Keys
 * ```kotlin
 * data class TodoKey(val userId: String, val todoId: Int)
 *
 * atom<TodoAtom>(key = TodoKey("alice", 1))  // AtomKey(TodoAtom::class, TodoKey("alice", 1))
 * ```
 *
 * ### Null Key (Singleton)
 * ```kotlin
 * atom<AppConfigAtom>()  // AtomKey(AppConfigAtom::class, null)
 * ```
 *
 * ## Equality and Hashing
 *
 * [AtomKey] is a data class, so equality is based on [type] and [instanceKey]:
 *
 * ```kotlin
 * val key1 = AtomKey(TodoAtom::class, 1)
 * val key2 = AtomKey(TodoAtom::class, 1)
 * val key3 = AtomKey(TodoAtom::class, 2)
 *
 * key1 == key2  // true (same type and instance key)
 * key1 == key3  // false (different instance key)
 * ```
 *
 * ## AtomStore Integration
 *
 * The [AtomStore] uses [AtomKey] for internal storage and reference counting:
 *
 * ```kotlin
 * class AtomStore {
 *     private val map = LinkedHashMap<AtomKey, Managed>()
 *
 *     fun acquire(key: AtomKey, create: () -> Pair<Atom, Job?>): Atom {
 *         // Lookup by key, increment refcount or create new instance
 *     }
 *
 *     fun release(key: AtomKey): Managed? {
 *         // Decrement refcount, return entry if zero
 *     }
 * }
 * ```
 *
 * ## Memory Management
 *
 * [AtomKey] instances are lightweight and short-lived:
 * - Created during `atom<A>(key = ...)` calls
 * - Used for lookup in [AtomStore]
 * - Discarded after the lookup completes
 * - Internally stored in [AtomStore] as map keys (not duplicated per consumer)
 *
 * ## Thread Safety
 *
 * [AtomKey] is immutable and thread-safe. Multiple threads can safely use the same [AtomKey]
 * for concurrent [AtomStore] operations (the store handles locking internally).
 *
 * ## Best Practices
 *
 * - **Use stable keys**: Ensure instance keys remain stable across recompositions in Compose
 * - **Implement equals/hashCode**: Custom key types must correctly implement equality
 * - **Avoid large keys**: Keep instance keys lightweight (primitives or small data classes)
 * - **Document key semantics**: Explain what the instance key represents in atom KDoc
 *
 * @property type The atom's class, used for type-based resolution and factory lookup
 * @property instanceKey An optional key distinguishing multiple instances of the same type.
 *                       `null` indicates a singleton atom (one instance per type).
 *
 * @see dev.mattramotar.atom.runtime.store.AtomStore for atom lifecycle and reference counting
 * @see dev.mattramotar.atom.runtime.child.ChildAtomProvider for managing child atom collections
 * @see dev.mattramotar.atom.runtime.compose.atom for Compose integration
 */
data class AtomKey(
    val type: KClass<out AtomLifecycle>,
    val instanceKey: Any?
)
