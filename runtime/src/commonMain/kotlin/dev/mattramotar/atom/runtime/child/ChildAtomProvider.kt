package dev.mattramotar.atom.runtime.child

import dev.mattramotar.atom.runtime.AtomKey
import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.state.StateHandleFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

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
 * @see dev.mattramotar.atom.runtime.Atom for parent atom integration
 */
open class ChildAtomProvider(
    private val parentScope: CoroutineScope,
    private val stateHandleFactory: StateHandleFactory,
    private val registry: AtomFactoryRegistry,
) {
    private data class Entry(
        val lifecycle: AtomLifecycle,
        val scope: CoroutineScope,
        val startSignal: CompletableDeferred<Unit> = CompletableDeferred(),
        val lifecycleMutex: Mutex = Mutex(),
        var started: Boolean = false,
        var starting: Boolean = false,
        var disposed: Boolean = false
    )

    private val children = mutableMapOf<AtomKey, Entry>()
    private val mutex = Mutex()

    suspend fun <A : AtomLifecycle, P : Any> getOrCreate(
        type: KClass<A>,
        id: Any,
        params: P
    ): A {
        val key = AtomKey(type, id)
        var created = false
        val entry = mutex.withLock {
            val existing = children[key]
            if (existing != null) {
                existing
            } else {
                val newEntry = createEntry(type, key, params)
                children[key] = newEntry
                created = true
                newEntry
            }
        }

        if (created) {
            startEntry(key, entry)
        }

        entry.startSignal.await()

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
        val toDispose = mutableListOf<Entry>()

        mutex.withLock {
            val activeKeys = items.keys.map { AtomKey(type, it) }.toSet()
            val iterator = children.entries.iterator()
            while (iterator.hasNext()) {
                val (key, entry) = iterator.next()
                if (key.type == type && key !in activeKeys) {
                    iterator.remove()
                    toDispose += entry
                }
            }
            items.forEach { (id, params) ->
                val key = AtomKey(type, id)
                if (children[key] == null) {
                    toCreate += Triple(type, key, params)
                }
            }
        }

        // Dispose outside lock
        for (entry in toDispose) {
            disposeEntry(entry)
        }

        // Create outside lock, then install under lock
        toCreate.forEach { (t, key, params) ->
            val newEntry = createEntry(t, key, params)
            val installed = mutex.withLock {
                if (children.containsKey(key)) {
                    false
                } else {
                    children[key] = newEntry
                    true
                }
            }

            if (installed) {
                startEntry(key, newEntry)
            } else {
                // Lost race - cleanup without lifecycle calls (atom never started)
                newEntry.scope.coroutineContext[Job]?.cancel()
                if (!newEntry.startSignal.isCompleted) {
                    newEntry.startSignal.completeExceptionally(
                        CancellationException("Child atom was superseded during sync.")
                    )
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

    suspend fun <A : AtomLifecycle> get(type: KClass<A>, id: Any): A? {
        val entry = mutex.withLock {
            children[AtomKey(type, id)]
        } ?: return null

        entry.startSignal.await()

        @Suppress("UNCHECKED_CAST")
        return entry.lifecycle as A
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

    private suspend fun disposeEntry(entry: Entry) {
        var shouldAwaitStart = false
        var shouldDispose = false
        var cancelNow = false

        entry.lifecycleMutex.withLock {
            if (entry.disposed) return
            entry.disposed = true
            when {
                entry.started -> {
                    shouldDispose = true
                    cancelNow = true
                }
                entry.starting -> {
                    shouldAwaitStart = true
                }
                else -> {
                    cancelNow = true
                    if (!entry.startSignal.isCompleted) {
                        entry.startSignal.completeExceptionally(
                            CancellationException("Child atom disposed before start.")
                        )
                    }
                }
            }
        }

        if (cancelNow) {
            entry.scope.coroutineContext[Job]?.cancel()
        }

        if (shouldAwaitStart) {
            val failure = runCatching { entry.startSignal.await() }.exceptionOrNull()
            if (failure != null) {
                return
            }
            entry.scope.coroutineContext[Job]?.cancel()
            shouldDispose = true
        }

        if (shouldDispose) {
            runCatching { entry.lifecycle.onStop() }
            runCatching { entry.lifecycle.onDispose() }
        }
    }

    suspend fun clear() {
        val toDispose: List<Entry> = mutex.withLock {
            val values = children.values.toList()
            children.clear()
            values
        }
        for (entry in toDispose) {
            disposeEntry(entry)
        }
    }

    private suspend fun startEntry(key: AtomKey, entry: Entry) {
        if (entry.startSignal.isCompleted) return
        var shouldStart = false

        entry.lifecycleMutex.withLock {
            if (entry.disposed) {
                if (!entry.startSignal.isCompleted) {
                    entry.startSignal.completeExceptionally(
                        CancellationException("Child atom disposed before start.")
                    )
                }
                return
            }
            if (entry.started || entry.starting) return
            entry.starting = true
            shouldStart = true
        }

        if (!shouldStart) {
            entry.startSignal.await()
            return
        }

        val failure = runCatching { entry.lifecycle.onStart() }.exceptionOrNull()

        entry.lifecycleMutex.withLock {
            entry.starting = false
            if (failure == null) {
                entry.started = true
                if (!entry.startSignal.isCompleted) {
                    entry.startSignal.complete(Unit)
                }
            } else if (!entry.startSignal.isCompleted) {
                entry.disposed = true
                entry.startSignal.completeExceptionally(failure as Throwable)
            }
        }

        if (failure != null) {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (children[key] === entry) {
                        children.remove(key)
                    }
                }
                entry.scope.coroutineContext[Job]?.cancel()
                runCatching { entry.lifecycle.onStop() }
                runCatching { entry.lifecycle.onDispose() }
            }
            throw failure as Throwable
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
