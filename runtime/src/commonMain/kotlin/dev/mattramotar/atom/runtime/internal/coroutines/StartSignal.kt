package dev.mattramotar.atom.runtime.internal.coroutines

internal expect class StartSignal() {
    fun markStarting()
    fun completeSuccess()
    fun completeFailure(t: Throwable)
    fun await()
}
