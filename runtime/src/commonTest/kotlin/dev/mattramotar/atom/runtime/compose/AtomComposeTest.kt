package dev.mattramotar.atom.runtime.compose

import androidx.compose.runtime.Applier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.factory.Atoms
import dev.mattramotar.atom.runtime.store.AtomStore
import dev.mattramotar.atom.runtime.store.AtomStoreOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalComposeApi::class)
class AtomComposeTest {

    @Test
    fun atomRecreatesAndDisposesWhenParamsChange() = runTest {
        val created = mutableListOf<TestAtom>()
        val paramsState = mutableStateOf(TestParams("one"))
        val observedAtom = mutableStateOf<TestAtom?>(null)
        val registry = testRegistry(created)
        val owner = testOwner()

        runComposition(
            content = {
                AtomCompositionLocals(factories = registry, owner = owner) {
                    val atom = atom<TestAtom>(key = "id", params = paramsState.value)
                    SideEffect { observedAtom.value = atom }
                }
            }
        ) { frameClock ->
            frameClock.advance()

            val first = requireNotNull(observedAtom.value)
            assertEquals("one", first.params.value)
            assertEquals(1, first.starts)
            assertEquals(0, first.stops)
            assertEquals(0, first.disposes)
            assertEquals(1, created.size)

            paramsState.value = TestParams("two")
            Snapshot.sendApplyNotifications()
            frameClock.advance()

            val second = requireNotNull(observedAtom.value)
            assertNotSame(first, second)
            assertEquals("two", second.params.value)
            assertEquals(2, created.size)
            assertEquals(1, first.starts)
            assertEquals(1, first.stops)
            assertEquals(1, first.disposes)
            assertEquals(1, second.starts)
            assertEquals(0, second.stops)
            assertEquals(0, second.disposes)
        }
    }

    @Test
    fun atomKeepsInstanceWhenParamsAreStable() = runTest {
        val created = mutableListOf<TestAtom>()
        val paramsState = mutableStateOf(TestParams("stable"))
        val recomposeTick = mutableStateOf(0)
        val observedAtom = mutableStateOf<TestAtom?>(null)
        val observedTick = mutableStateOf(0)
        val registry = testRegistry(created)
        val owner = testOwner()

        runComposition(
            content = {
                AtomCompositionLocals(factories = registry, owner = owner) {
                    val atom = atom<TestAtom>(key = "id", params = paramsState.value)
                    val tick = recomposeTick.value
                    SideEffect {
                        observedAtom.value = atom
                        observedTick.value = tick
                    }
                }
            }
        ) { frameClock ->
            frameClock.advance()

            val first = requireNotNull(observedAtom.value)
            assertEquals(1, created.size)
            assertEquals(0, observedTick.value)

            recomposeTick.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.advance()

            val second = requireNotNull(observedAtom.value)
            assertSame(first, second)
            assertEquals(1, created.size)
            assertEquals(1, observedTick.value)
            assertEquals(1, first.starts)
            assertEquals(0, first.stops)
            assertEquals(0, first.disposes)
        }
    }

    private suspend fun TestScope.runComposition(
        content: @Composable () -> Unit,
        block: suspend (FrameClockDriver) -> Unit
    ) {
        val clock = BroadcastFrameClock()
        val frameClock = FrameClockDriver(this, clock)
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(UnitApplier, recomposer)
        val job = launch(coroutineContext + clock) {
            recomposer.runRecomposeAndApplyChanges()
        }

        composition.setContent(content)

        try {
            block(frameClock)
        } finally {
            composition.dispose()
            Snapshot.sendApplyNotifications()
            frameClock.advance()
            recomposer.close()
            job.join()
        }
    }

    private class FrameClockDriver(
        private val scope: TestScope,
        private val clock: BroadcastFrameClock
    ) {
        private var frameTime = 0L

        suspend fun advance() {
            frameTime += 1
            clock.sendFrame(frameTime)
            scope.runCurrent()
        }
    }

    private fun testRegistry(created: MutableList<TestAtom>): AtomFactoryRegistry {
        val entry = Atoms.factory<TestAtom, TestState, TestParams>(
            create = { _, _, params ->
                TestAtom(params).also { created.add(it) }
            },
            initial = { params -> TestState(params.value) }
        )

        return object : AtomFactoryRegistry {
            override fun entryFor(type: KClass<out AtomLifecycle>) =
                if (type == TestAtom::class) entry else null
        }
    }

    private fun testOwner(): AtomStoreOwner = object : AtomStoreOwner {
        override val atomStore = AtomStore()
    }

    private data class TestParams(val value: String)

    private data class TestState(val value: String)

    private class TestAtom(val params: TestParams) : AtomLifecycle {
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

    private object UnitApplier : Applier<Unit> {
        override val current: Unit = Unit

        override fun down(node: Unit) = Unit

        override fun up() = Unit

        override fun insertTopDown(index: Int, instance: Unit) = Unit

        override fun insertBottomUp(index: Int, instance: Unit) = Unit

        override fun remove(index: Int, count: Int) = Unit

        override fun move(from: Int, to: Int, count: Int) = Unit

        override fun clear() = Unit
    }

}
