package dev.mattramotar.atom.runtime.child

import dev.mattramotar.atom.runtime.Atom
import dev.mattramotar.atom.runtime.AtomKey
import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.state.StateHandleFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass
import dev.mattramotar.atom.runtime.child.ChildAtomProvider
/**
 * Manages a dynamic collection of child atoms.
 *
 * [ChildAtomProvider] enables parent atoms to manage child atom lifecycles:
 *
 * ```kotlin
 * class PostListAtom(...) : Atom<PostListState, ...> {
 *     private val children = ChildAtomProvider(scope, stateHandleFactory, registry)
 *
 *     suspend fun syncPosts(posts: List<Post>) {
 *         children.sync(
 *             type = PostAtom::class,
 *             items = posts.associateBy { it.id }.mapValues {
 *                 PostAtomParams(postId = it.key)
 *             }
 *         )
 *     }
 * }
 * ```
 *
 * ## Synchronization
 *
 * [sync] creates atoms for new items and disposes atoms for removed items.
 *
 * @see Atom for parent atom integration
 */
open class ChildAtomProvider(
    private val parentScope: CoroutineScope,
    private val stateHandleFactory: StateHandleFactory,
    private val registry: AtomFactoryRegistry,
) {
    private data class Entry(
        val lifecycle: AtomLifecycle,
        val scope: CoroutineScope
    )

    private val children = mutableMapOf<AtomKey, Entry>()
    private val mutex = Mutex()

    suspend fun <A : AtomLifecycle, P : Any> getOrCreate(
        type: KClass<A>,
        id: Any,
        params: P
    ): A = mutex.withLock {
        val key = AtomKey(type, id)

        val entry = children.getOrPut(key) {
            val newEntry = createEntry(type, key, params)
            // Start only after successful installation
            runCatching { newEntry.lifecycle.onStart() }
            newEntry
        }
        @Suppress("UNCHECKED_CAST")
        return entry.lifecycle as A
    }

    suspend fun <A : AtomLifecycle> getOrCreate(type: KClass<A>, id: Any): A =
        getOrCreate(type, id, Unit)

    suspend fun <A : AtomLifecycle, P : Any> sync(
        type: KClass<A>,
        items: Map<Any, P>
    ) {
        val toCreate = mutableListOf<Triple<KClass<A>, AtomKey, P>>()
        val toDispose = mutableListOf<AtomKey>()

        mutex.withLock {
            val activeKeys = items.keys.map { AtomKey(type, it) }.toSet()
            children.keys.filter { it.type == type && it !in activeKeys }.forEach { toDispose += it }
            items.forEach { (id, params) ->
                val key = AtomKey(type, id)
                if (children[key] == null) {
                    toCreate += Triple(type, key, params)
                }
            }
        }

        // Dispose outside lock
        toDispose.forEach { key -> disposeUnsafe(key) }

        // Create outside lock, then install under lock
        toCreate.forEach { (t, key, params) ->
            val entry = registry.entryFor(t) ?: error("No factory for ${t.simpleName}!")
            val childScope = requireChildScope()
            @Suppress("UNCHECKED_CAST") val stateClass = entry.stateClass as KClass<Any>
            val state = stateHandleFactory.create(key, stateClass, { entry.initialAny(params) }, entry.serializerAny)
            @Suppress("UNCHECKED_CAST") val atom = entry.createAny(childScope, state, params)

            mutex.withLock {
                if (!children.contains(key)) {
                    children[key] = Entry(atom, childScope)
                    // Start only after successful installation
                    runCatching { atom.onStart() }
                } else {
                    // Lost race - cleanup without lifecycle calls (atom never started)
                    childScope.coroutineContext[Job]?.cancel()
                }
            }
        }
    }

    suspend fun <A : AtomLifecycle> sync(
        type: KClass<A>,
        ids: Collection<Any>
    ) {
        sync(type, ids.associateWith { Unit })
    }

    suspend fun <A : AtomLifecycle> get(type: KClass<A>, id: Any): A? = mutex.withLock {
        @Suppress("UNCHECKED_CAST")
        return children[AtomKey(type, id)]?.lifecycle as? A
    }

    private fun <A : AtomLifecycle, P : Any> createEntry(
        type: KClass<A>,
        key: AtomKey,
        params: P
    ): Entry {
        val entry = registry.entryFor(type)
            ?: error("No factory for ${type.simpleName}")

        require(
            entry.paramsClass.isInstance(params) ||
                    (params == Unit && entry.paramsClass == Unit::class)
        ) {
            "Expected ${entry.paramsClass.simpleName} but got ${params::class.simpleName}"
        }

        val childScope = requireChildScope()

        @Suppress("UNCHECKED_CAST")
        val stateClass = entry.stateClass as KClass<Any>
        val state = stateHandleFactory.create(
            key = key,
            stateClass = stateClass,
            initial = { entry.initialAny(params) },
            serializer = entry.serializerAny
        )

        @Suppress("UNCHECKED_CAST")
        val atom = entry.createAny(childScope, state, params) as A

        // Don't call onStart() here - caller handles it after installation

        return Entry(atom, childScope)
    }

    private fun disposeUnsafe(key: AtomKey) {
        children.remove(key)?.let { entry ->
            runCatching { entry.lifecycle.onStop() }
            runCatching { entry.lifecycle.onDispose() }
            entry.scope.coroutineContext[Job]?.cancel()
        }
    }

    suspend fun clear() {
        val toDispose: List<Entry> = mutex.withLock {
            val values = children.values.toList()
            children.clear()
            values
        }
        toDispose.forEach { entry ->
            runCatching { entry.lifecycle.onStop() }
            runCatching { entry.lifecycle.onDispose() }
            entry.scope.coroutineContext[Job]?.cancel()
        }
    }

    /**
     * Creates a child scope tied to the parent's [Job].
     *
     * Throwing is correct here because a [CoroutineScope] without a [Job] is a programming error.
     * Except for [kotlinx.coroutines.GlobalScope], every [CoroutineScope] should have a [Job].
     * If the [parentScope]'s coroutine context does not have a [Job], the caller constructed [ChildAtomProvider] with an invalid scope.
     * This is an invariant violation at the API boundary, not a recoverable runtime condition.
     *
     * @throws [IllegalStateException] if the [parentScope]'s coroutine context does not have a [Job].
     */
    private fun requireChildScope(): CoroutineScope {
        val parentJob = checkNotNull(parentScope.coroutineContext[Job]) { "No Job in parent scope!" }
        return CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentJob))
    }
}
