package dev.mattramotar.atom.runtime.internal.coroutines

import java.util.concurrent.locks.ReentrantLock

internal actual class StartSignal {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private var state = STATE_NEW
    private var failure: Throwable? = null
    private var owner: Thread? = null

    actual fun markStarting() {
        lock.lock()
        try {
            owner = Thread.currentThread()
            state = STATE_STARTING
        } finally {
            lock.unlock()
        }
    }

    actual fun completeSuccess() {
        lock.lock()
        try {
            state = STATE_SUCCESS
            owner = null
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    actual fun completeFailure(t: Throwable) {
        lock.lock()
        try {
            failure = t
            state = STATE_FAILURE
            owner = null
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    actual fun await() {
        lock.lock()
        try {
            val current = Thread.currentThread()
            while (true) {
                when (state) {
                    STATE_SUCCESS -> return
                    STATE_FAILURE -> throw requireNotNull(failure)
                    else -> {
                        if (owner === current && owner != null) return
                        condition.await()
                    }
                }
            }
        } finally {
            lock.unlock()
        }
    }
}

private const val STATE_NEW = 0
private const val STATE_STARTING = 1
private const val STATE_SUCCESS = 2
private const val STATE_FAILURE = 3
