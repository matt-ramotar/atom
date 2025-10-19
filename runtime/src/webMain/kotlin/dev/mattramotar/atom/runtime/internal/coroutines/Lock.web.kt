package dev.mattramotar.atom.runtime.internal.coroutines

internal actual class Lock {
    actual inline fun <R> withLock(block: () -> R): R {
        // Single-threaded, no locking needed.
        return block()
    }
}