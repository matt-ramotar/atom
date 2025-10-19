package dev.mattramotar.atom.runtime.internal.coroutines

import java.util.concurrent.locks.ReentrantLock

internal actual class Lock {
    private val lock = ReentrantLock()

    actual inline fun <R> withLock(block: () -> R): R {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}