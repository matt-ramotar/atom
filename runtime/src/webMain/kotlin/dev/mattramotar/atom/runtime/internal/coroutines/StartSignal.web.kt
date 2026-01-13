package dev.mattramotar.atom.runtime.internal.coroutines

internal actual class StartSignal {
    private var state = STATE_NEW
    private var failure: Throwable? = null
    private var ownerSet = false

    actual fun markStarting() {
        ownerSet = true
        state = STATE_STARTING
    }

    actual fun completeSuccess() {
        state = STATE_SUCCESS
        ownerSet = false
    }

    actual fun completeFailure(t: Throwable) {
        failure = t
        state = STATE_FAILURE
        ownerSet = false
    }

    actual fun await() {
        when (state) {
            STATE_SUCCESS -> return
            STATE_FAILURE -> throw requireNotNull(failure)
            else -> {
                if (ownerSet) return
                error("StartSignal.await() cannot block on JS; this indicates a lifecycle reentrancy bug.")
            }
        }
    }
}

private const val STATE_NEW = 0
private const val STATE_STARTING = 1
private const val STATE_SUCCESS = 2
private const val STATE_FAILURE = 3
