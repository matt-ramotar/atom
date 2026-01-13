package dev.mattramotar.atom.runtime.internal.coroutines

import kotlinx.atomicfu.atomic
import platform.posix.pthread_self
import platform.posix.usleep

internal actual class StartSignal {
    private val state = atomic(STATE_NEW)
    private val failure = atomic<Throwable?>(null)
    private val owner = atomic<Any?>(null)

    actual fun markStarting() {
        owner.value = pthread_self()
        state.value = STATE_STARTING
    }

    actual fun completeSuccess() {
        state.value = STATE_SUCCESS
        owner.value = null
    }

    actual fun completeFailure(t: Throwable) {
        failure.value = t
        state.value = STATE_FAILURE
        owner.value = null
    }

    actual fun await() {
        val token = pthread_self()
        while (true) {
            when (state.value) {
                STATE_SUCCESS -> return
                STATE_FAILURE -> throw requireNotNull(failure.value)
                else -> {
                    if (owner.value == token && owner.value != null) return
                    usleep(1_000u)
                }
            }
        }
    }
}

private const val STATE_NEW = 0
private const val STATE_STARTING = 1
private const val STATE_SUCCESS = 2
private const val STATE_FAILURE = 3
