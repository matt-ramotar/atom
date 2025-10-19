package dev.mattramotar.atom.runtime.internal.coroutines

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal actual class Lock {
    private val lock = SynchronizedObject()

    actual inline fun <R> withLock(block: () -> R): R {
        return synchronized(lock, block)
    }
}