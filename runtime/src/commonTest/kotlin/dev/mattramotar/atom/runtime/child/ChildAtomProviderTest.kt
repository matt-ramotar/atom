package dev.mattramotar.atom.runtime.child

import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.factory.Atoms
import dev.mattramotar.atom.runtime.state.InMemoryStateHandleFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChildAtomProviderTest {

    @Test
    fun getOrCreateStartsOnceAndReturnsSameInstance() = runTest {
        val log = CallLog()
        val provider = ChildAtomProvider(
            parentScope = CoroutineScope(coroutineContext),
            stateHandleFactory = InMemoryStateHandleFactory,
            registry = testRegistry(log)
        )

        val first = provider.getOrCreate(TestChildAtom::class, "id", TestParams("id"))
        val second = provider.getOrCreate(TestChildAtom::class, "id", TestParams("id"))

        assertSame(first, second)
        assertEquals(listOf("id"), log.starts)
        provider.clear()
    }

    @Test
    fun clearCancelsBeforeStopAndDispose() = runTest {
        val log = CallLog()
        val provider = ChildAtomProvider(
            parentScope = CoroutineScope(coroutineContext),
            stateHandleFactory = InMemoryStateHandleFactory,
            registry = testRegistry(log)
        )

        provider.getOrCreate(TestChildAtom::class, "id", TestParams("id"))
        provider.clear()

        assertEquals(listOf("id"), log.stops)
        assertEquals(listOf("id"), log.disposes)
        assertEquals(listOf(false), log.stopJobActive)
        assertEquals(listOf(false), log.disposeJobActive)
    }

    @Test
    fun syncDisposesRemovedChildren() = runTest {
        val log = CallLog()
        val provider = ChildAtomProvider(
            parentScope = CoroutineScope(coroutineContext),
            stateHandleFactory = InMemoryStateHandleFactory,
            registry = testRegistry(log)
        )

        provider.sync(
            TestChildAtom::class,
            mapOf("a" to TestParams("a"), "b" to TestParams("b"))
        )

        assertEquals(setOf("a", "b"), log.starts.toSet())

        provider.sync(
            TestChildAtom::class,
            mapOf("a" to TestParams("a"))
        )

        assertEquals(listOf("b"), log.stops)
        assertEquals(listOf("b"), log.disposes)
        assertTrue(log.stopJobActive.all { !it })
        assertTrue(log.disposeJobActive.all { !it })
        provider.clear()
    }

    @Test
    fun concurrentSyncGetOrCreateAndClearIsSafe() = runTest {
        val provider = ChildAtomProvider(
            parentScope = CoroutineScope(coroutineContext),
            stateHandleFactory = InMemoryStateHandleFactory,
            registry = testRegistryNoLog()
        )
        val errors = mutableListOf<Throwable>()
        val errorMutex = Mutex()

        suspend fun recordError(t: Throwable) {
            if (t is CancellationException) return
            errorMutex.withLock {
                errors += t
            }
        }

        supervisorScope {
            repeat(30) { index ->
                val id = "id-${index % 3}"
                launch(Dispatchers.Default) {
                    try {
                        provider.getOrCreate(NoopChildAtom::class, id, TestParams(id))
                    } catch (t: Throwable) {
                        recordError(t)
                    }
                }
                launch(Dispatchers.Default) {
                    try {
                        provider.sync(
                            NoopChildAtom::class,
                            mapOf(id to TestParams(id))
                        )
                    } catch (t: Throwable) {
                        recordError(t)
                    }
                }
                if (index % 5 == 0) {
                    launch(Dispatchers.Default) {
                        try {
                            provider.clear()
                        } catch (t: Throwable) {
                            recordError(t)
                        }
                    }
                }
            }
        }

        provider.clear()
        assertTrue(errors.isEmpty(), "Unexpected errors: $errors")
    }

    private data class TestParams(val id: String)

    private data class TestState(val value: Int = 0)

    private class CallLog {
        val starts = mutableListOf<String>()
        val stops = mutableListOf<String>()
        val disposes = mutableListOf<String>()
        val stopJobActive = mutableListOf<Boolean>()
        val disposeJobActive = mutableListOf<Boolean>()
    }

    private class TestChildAtom(
        private val id: String,
        private val scope: CoroutineScope,
        private val log: CallLog
    ) : AtomLifecycle {
        override fun onStart() {
            log.starts += id
        }

        override fun onStop() {
            val active = scope.coroutineContext[Job]?.isActive == true
            log.stopJobActive += active
            log.stops += id
        }

        override fun onDispose() {
            val active = scope.coroutineContext[Job]?.isActive == true
            log.disposeJobActive += active
            log.disposes += id
        }
    }

    private class NoopChildAtom : AtomLifecycle

    private fun testRegistry(log: CallLog): AtomFactoryRegistry {
        val entry = Atoms.factory<TestChildAtom, TestState, TestParams>(
            create = { scope, _, params ->
                TestChildAtom(params.id, scope, log)
            },
            initial = { TestState() }
        )

        return object : AtomFactoryRegistry {
            override fun entryFor(type: KClass<out AtomLifecycle>) =
                if (type == TestChildAtom::class) entry else null
        }
    }

    private fun testRegistryNoLog(): AtomFactoryRegistry {
        val entry = Atoms.factory<NoopChildAtom, TestState, TestParams>(
            create = { _, _, _ ->
                NoopChildAtom()
            },
            initial = { TestState() }
        )

        return object : AtomFactoryRegistry {
            override fun entryFor(type: KClass<out AtomLifecycle>) =
                if (type == NoopChildAtom::class) entry else null
        }
    }
}
