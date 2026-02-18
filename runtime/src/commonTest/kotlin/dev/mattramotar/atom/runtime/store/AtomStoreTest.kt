package dev.mattramotar.atom.runtime.store

import dev.mattramotar.atom.runtime.AtomKey
import dev.mattramotar.atom.runtime.AtomLifecycle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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

    @Test
    fun concurrentAcquireAndReleaseKeepsSingleSharedInstance() = runTest {
        val store = AtomStore()
        val key = AtomKey(TestAtom::class, "id")
        val workers = 32
        val created = Channel<CreatedTestAtom>(Channel.UNLIMITED)
        val acquired = Channel<TestAtom>(Channel.UNLIMITED)
        val acquireStart = CompletableDeferred<Unit>()

        val acquireJobs = List(workers) {
            launch(Dispatchers.Default) {
                acquireStart.await()
                val atom = store.acquire(key) {
                    val candidate = TestAtom()
                    val job = Job()
                    check(created.trySend(CreatedTestAtom(candidate, job)).isSuccess)
                    candidate to job
                }
                check(acquired.trySend(atom).isSuccess)
            }
        }

        acquireStart.complete(Unit)
        acquireJobs.joinAll()
        created.close()
        acquired.close()

        val acquiredAtoms = drainChannel(acquired)
        val createdEntries = drainChannel(created)
        assertEquals(workers, acquiredAtoms.size)
        assertTrue(createdEntries.isNotEmpty())

        val winner = acquiredAtoms.first()
        acquiredAtoms.forEach { assertSame(winner, it) }
        assertEquals(1, winner.starts)

        val winnerEntry = createdEntries.firstOrNull { it.atom === winner }
        requireNotNull(winnerEntry)
        assertTrue(winnerEntry.job.isActive)
        createdEntries
            .filterNot { it.atom === winner }
            .forEach { assertTrue(it.job.isCancelled) }

        val released = Channel<AtomStore.Managed?>(Channel.UNLIMITED)
        val releaseStart = CompletableDeferred<Unit>()
        val releaseJobs = List(workers) {
            launch(Dispatchers.Default) {
                releaseStart.await()
                check(released.trySend(store.release(key)).isSuccess)
            }
        }

        releaseStart.complete(Unit)
        releaseJobs.joinAll()
        released.close()

        val releaseResults = drainChannel(released)
        val terminal = releaseResults.filterNotNull()
        assertEquals(1, terminal.size)

        val managed = terminal.single()
        assertSame(winner, managed.lifecycle)
        managed.job?.cancel()
        managed.lifecycle.onStop()
        managed.lifecycle.onDispose()

        assertEquals(1, winner.stops)
        assertEquals(1, winner.disposes)
        assertNull(store.release(key))
    }

    @Test
    fun concurrentFailingAcquireCancelsAllCandidatesAndCleansStore() = runTest {
        val store = AtomStore()
        val key = AtomKey(FailingAtom::class, "id")
        val workers = 24
        val created = Channel<CreatedFailingAtom>(Channel.UNLIMITED)
        val attempts = Channel<Result<FailingAtom>>(Channel.UNLIMITED)
        val start = CompletableDeferred<Unit>()

        val jobs = List(workers) {
            launch(Dispatchers.Default) {
                start.await()
                val outcome = runCatching {
                    store.acquire(key) {
                        val atom = FailingAtom()
                        val job = Job()
                        check(created.trySend(CreatedFailingAtom(atom, job)).isSuccess)
                        atom to job
                    }
                }
                check(attempts.trySend(outcome).isSuccess)
            }
        }

        start.complete(Unit)
        jobs.joinAll()
        created.close()
        attempts.close()

        val outcomes = drainChannel(attempts)
        assertEquals(workers, outcomes.size)
        outcomes.forEach { outcome ->
            assertTrue(outcome.isFailure)
            assertTrue(outcome.exceptionOrNull() is IllegalStateException)
        }

        val createdEntries = drainChannel(created)
        assertTrue(createdEntries.isNotEmpty())
        createdEntries.forEach { entry ->
            assertTrue(entry.job.isCancelled)
            if (entry.atom.starts == 0) {
                assertEquals(0, entry.atom.stops)
                assertEquals(0, entry.atom.disposes)
            } else {
                assertEquals(1, entry.atom.starts)
                assertEquals(1, entry.atom.stops)
                assertEquals(1, entry.atom.disposes)
            }
        }

        assertNull(store.release(key))
    }

    private data class CreatedTestAtom(
        val atom: TestAtom,
        val job: Job
    )

    private data class CreatedFailingAtom(
        val atom: FailingAtom,
        val job: Job
    )

    private fun <T> drainChannel(channel: Channel<T>): List<T> {
        val values = mutableListOf<T>()
        while (true) {
            val next = channel.tryReceive()
            if (next.isFailure) {
                break
            }
            values += next.getOrThrow()
        }
        return values
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
