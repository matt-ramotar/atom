package dev.mattramotar.atom.runtime.store

import dev.mattramotar.atom.runtime.AtomKey
import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.internal.coroutines.Lock
import dev.mattramotar.atom.runtime.internal.coroutines.StartSignal
import kotlinx.coroutines.Job

/**
 * Reference-counted storage for atom instances.
 *
 * [AtomStore] manages atom lifecycle via reference counting:
 * - **Acquire**: Increment ref count, create if new
 * - **Release**: Decrement ref count, return the entry when zero
 * - **Sharing**: Multiple consumers share the same instance
 *
 * ## Reference Counting
 *
 * ```
 * acquire(key) → refs: 0→1 → onStart() called, atom returned
 * acquire(key) → refs: 1→2 → same atom returned
 * release(key) → refs: 2→1 → nothing happens
 * release(key) → refs: 1→0 → entry returned for disposal, atom removed
 * ```
 *
 * ## Thread Safety
 *
 * All operations are synchronized via [Lock]. Safe to call from multiple threads concurrently.
 * Lifecycle callbacks are invoked outside the internal lock.
 *
 * @see AtomStoreOwner for ownership interface
 * @see dev.mattramotar.atom.runtime.compose.LocalAtomStoreOwner for Compose integration
 */
class AtomStore {
    /**
     * Managed atom entry with lifecycle and reference count.
     *
     * @property lifecycle The atom instance
     * @property job The atom's coroutine job (to be cancelled by the owner on dispose)
     * @property refs Current reference count
     */
    data class Managed(
        val lifecycle: AtomLifecycle,
        val job: Job?,
        var refs: Int
    ) {
        internal val startSignal = StartSignal()
    }

    private val map = LinkedHashMap<AtomKey, Managed>()
    private val lock = Lock()

    /**
     * Acquires an atom, creating it if necessary.
     *
     * **Lifecycle Guarantee**: [AtomLifecycle.onStart] is called only after successful installation.
     * If [AtomLifecycle.onStart] throws, the entry is removed and the exception is rethrown.
     *
     * @param key The atom key
     * @param create Lambda that creates the atom (called only if atom doesn't exist).
     *               Should NOT call [AtomLifecycle.onStart] - the store handles that.
     * @return The atom instance (existing or newly created)
     */
    @Suppress("UNCHECKED_CAST")
    fun <A : AtomLifecycle> acquire(
        key: AtomKey,
        create: () -> Pair<A, Job?>
    ): A {
        // Fast path: check cache without creating
        var existing: Managed? = null
        lock.withLock {
            val cached = map[key]
            if (cached != null) {
                cached.refs++
                existing = cached
            }
        }
        if (existing != null) {
            awaitStart(key, existing)
            @Suppress("UNCHECKED_CAST")
            return existing.lifecycle as A
        }

        // Slow path: create outside lock
        val (atom, job) = create()
        val managed = Managed(atom, job, refs = 1).apply {
            startSignal.markStarting()
        }
        var isWinner = false
        var winner: Managed? = null

        lock.withLock {
            val cached = map[key]
            if (cached != null) {
                cached.refs++
                winner = cached
            } else {
                map[key] = managed
                winner = managed
                isWinner = true
            }
        }

        if (!isWinner) {
            job?.cancel()
            val existingWinner = requireNotNull(winner)
            awaitStart(key, existingWinner)
            @Suppress("UNCHECKED_CAST")
            return existingWinner.lifecycle as A
        }

        try {
            atom.onStart()
            managed.startSignal.completeSuccess()
        } catch (t: Throwable) {
            managed.startSignal.completeFailure(t)
            lock.withLock {
                if (map[key] === managed) {
                    map.remove(key)
                }
            }
            job?.cancel()
            runCatching { atom.onStop() }
            runCatching { atom.onDispose() }
            throw t
        }

        return atom
    }

    private fun awaitStart(key: AtomKey, managed: Managed) {
        try {
            managed.startSignal.await()
        } catch (t: Throwable) {
            lock.withLock {
                if (map[key] === managed) {
                    managed.refs--
                    if (managed.refs <= 0) {
                        map.remove(key)
                    }
                }
            }
            throw t
        }
    }

    /**
     * Releases an atom, returning it when ref count reaches zero.
     *
     * @param key The atom key
     * @return The managed entry if ref count reached zero, or null if still referenced
     *
     * Callers are responsible for cancelling the job and invoking lifecycle callbacks on the
     * returned entry.
     */
    fun release(key: AtomKey): Managed? = lock.withLock {
        val managed = map[key] ?: return null
        if (--managed.refs == 0) {
            map.remove(key)
            managed
        } else {
            null
        }
    }

    /**
     * Clears all atoms, returning them for disposal regardless of ref count.
     *
     * @return List of all managed entries (for disposal)
     */
    fun clear(): List<Managed> = lock.withLock {
        val values = map.values.toList()
        map.clear()
        values
    }
}
