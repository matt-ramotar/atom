package dev.mattramotar.atom.runtime.store

import dev.mattramotar.atom.runtime.AtomKey
import dev.mattramotar.atom.runtime.AtomLifecycle
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AtomStoreTest {

    @Test
    fun acquireSharesInstanceAndReleasesOnLastRef() {
        val store = AtomStore()
        val key = AtomKey(TestAtom::class, "id")
        var creates = 0

        val first = store.acquire(key) {
            creates += 1
            TestAtom() to Job()
        }
        val second = store.acquire(key) {
            creates += 1
            TestAtom() to Job()
        }

        assertSame(first, second)
        assertEquals(1, creates)
        assertEquals(1, first.starts)

        val stillHeld = store.release(key)
        assertNull(stillHeld)

        val managed = store.release(key)
        requireNotNull(managed)
        managed.job?.cancel()
        managed.lifecycle.onStop()
        managed.lifecycle.onDispose()

        assertSame(first, managed.lifecycle)
        assertEquals(1, first.stops)
        assertEquals(1, first.disposes)
    }

    @Test
    fun onStartFailureRemovesEntryAndCancelsJob() {
        val store = AtomStore()
        val key = AtomKey(FailingAtom::class, "id")
        val atom = FailingAtom()
        val job = Job()

        assertFailsWith<IllegalStateException> {
            store.acquire(key) { atom to job }
        }

        assertTrue(job.isCancelled)
        assertEquals(1, atom.starts)
        assertEquals(1, atom.stops)
        assertEquals(1, atom.disposes)
        assertNull(store.release(key))

        val recovered = store.acquire(AtomKey(TestAtom::class, "id")) {
            TestAtom() to Job()
        }
        assertEquals(1, recovered.starts)
    }

    @Test
    fun onStartFailureDoesNotRetainEntryForSameKey() {
        val store = AtomStore()
        val key = AtomKey(FailOnceAtom::class, "id")
        val state = FailOnceState()
        var creates = 0
        val firstJob = Job()
        lateinit var first: FailOnceAtom

        assertFailsWith<IllegalStateException> {
            store.acquire(key) {
                creates += 1
                first = FailOnceAtom(state)
                first to firstJob
            }
        }

        assertTrue(firstJob.isCancelled)
        assertEquals(1, creates)
        assertEquals(1, first.starts)
        assertEquals(1, first.stops)
        assertEquals(1, first.disposes)
        assertNull(store.release(key))

        val secondJob = Job()
        lateinit var second: FailOnceAtom
        val atom = store.acquire(key) {
            creates += 1
            second = FailOnceAtom(state)
            second to secondJob
        }

        assertSame(second, atom)
        assertEquals(2, creates)
        assertTrue(first !== second)
        assertEquals(1, second.starts)
        assertTrue(secondJob.isActive)

        val managed = store.release(key)
        requireNotNull(managed)
        managed.job?.cancel()
        managed.lifecycle.onStop()
        managed.lifecycle.onDispose()
    }

    private class TestAtom : AtomLifecycle {
        var starts = 0
        var stops = 0
        var disposes = 0

        override fun onStart() {
            starts += 1
        }

        override fun onStop() {
            stops += 1
        }

        override fun onDispose() {
            disposes += 1
        }
    }

    private class FailingAtom : AtomLifecycle {
        var starts = 0
        var stops = 0
        var disposes = 0

        override fun onStart() {
            starts += 1
            throw IllegalStateException("boom")
        }

        override fun onStop() {
            stops += 1
        }

        override fun onDispose() {
            disposes += 1
        }
    }

    private class FailOnceState {
        var shouldFail = true
    }

    private class FailOnceAtom(
        private val state: FailOnceState
    ) : AtomLifecycle {
        var starts = 0
        var stops = 0
        var disposes = 0

        override fun onStart() {
            starts += 1
            if (state.shouldFail) {
                state.shouldFail = false
                throw IllegalStateException("boom")
            }
        }

        override fun onStop() {
            stops += 1
        }

        override fun onDispose() {
            disposes += 1
        }
    }
}
