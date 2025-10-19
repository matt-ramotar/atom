package dev.mattramotar.atom.runtime.internal.coroutines

internal expect class Lock() {
    inline fun <R> withLock(block: () -> R): R
}