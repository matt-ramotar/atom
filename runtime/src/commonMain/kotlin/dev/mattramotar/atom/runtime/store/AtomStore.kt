package dev.mattramotar.atom.runtime.store

import dev.mattramotar.atom.runtime.AtomKey
import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.internal.coroutines.Lock
import kotlinx.coroutines.Job

/**
 * Reference-counted storage for atom instances.
 *
 * [AtomStore] manages atom lifecycle via reference counting:
 * - **Acquire**: Increment ref count, create if new
 * - **Release**: Decrement ref count, dispose if zero
 * - **Sharing**: Multiple consumers share the same instance
 *
 * ## Reference Counting
 *
 * ```
 * acquire(key) → refs: 0→1 → onStart() called, atom returned
 * acquire(key) → refs: 1→2 → same atom returned
 * release(key) → refs: 2→1 → nothing happens
 * release(key) → refs: 1→0 → onStop(), onDispose() called, atom removed
 * ```
 *
 * ## Thread Safety
 *
 * All operations are synchronized via [Lock]. Safe to call from multiple threads concurrently.
 *
 * @see AtomStoreOwner for ownership interface
 * @see dev.mattramotar.atom.runtime.compose.LocalAtomStoreOwner for Compose integration
 */
class AtomStore {
    /**
     * Managed atom entry with lifecycle and reference count.
     *
     * @property lifecycle The atom instance
     * @property job The atom's coroutine job (cancelled on dispose)
     * @property refs Current reference count
     */
    data class Managed(val lifecycle: AtomLifecycle, val job: Job?, var refs: Int)

    private val map = LinkedHashMap<AtomKey, Managed>()
    private val lock = Lock()

    /**
     * Acquires an atom, creating it if necessary.
     *
     * @param key The atom key
     * @param create Lambda that creates the atom (called only if atom doesn't exist)
     * @return The atom instance (existing or newly created)
     */
    @Suppress("UNCHECKED_CAST")
    fun <A : AtomLifecycle> acquire(
        key: AtomKey,
        create: () -> Pair<A, Job?>
    ): A {
        // Fast path: check cache without creating
        lock.withLock {
            val existing = map[key]
            if (existing != null) {
                existing.refs++
                @Suppress("UNCHECKED_CAST")
                return existing.lifecycle as A
            }
        }

        // Slow path: create outside lock
        val (atom, job) = create()

        // Install with double-check
        lock.withLock {
            val existing = map[key]
            if (existing != null) {
                // Lost race - use existing
                existing.refs++
                // Dispose our extra
                job?.cancel()
                runCatching { atom.onDispose() }
                @Suppress("UNCHECKED_CAST")
                return existing.lifecycle as A
            }

            // We won - install ours
            map[key] = Managed(atom, job, refs = 1)
            @Suppress("UNCHECKED_CAST")
            return atom
        }
    }

    /**
     * Releases an atom, disposing it if ref count reaches zero.
     *
     * @param key The atom key
     * @return The managed entry if disposed, or null if still referenced
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
     * Clears all atoms, disposing them regardless of ref count.
     *
     * @return List of all managed entries (for disposal)
     */
    fun clear(): List<Managed> = lock.withLock {
        val values = map.values.toList()
        map.clear()
        values
    }
}
