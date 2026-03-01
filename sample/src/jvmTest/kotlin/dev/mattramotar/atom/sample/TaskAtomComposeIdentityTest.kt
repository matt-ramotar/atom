package dev.mattramotar.atom.sample

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
import dev.mattramotar.atom.runtime.compose.AtomCompositionLocals
import dev.mattramotar.atom.runtime.compose.atom
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.factory.Atoms
import dev.mattramotar.atom.runtime.store.AtomStore
import dev.mattramotar.atom.runtime.store.AtomStoreOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalComposeApi::class)
class TaskAtomComposeIdentityTest {

    @Test
    fun taskAtomRecreatesWhenParamsChangeWithSameKey() = runTest {
        val repository = InMemorySampleTaskRepository(
            initialData = mapOf(
                SAMPLE_BOARD_ID to listOf(
                    SampleTask(id = "task-1", title = "Draft", completed = false)
                )
            )
        )
        val diagnostics = InMemorySampleDiagnostics()
        val registry = testRegistry(repository = repository, diagnostics = diagnostics)
        val owner = testOwner()
        val observedAtom = mutableStateOf<TaskAtom?>(null)
        val paramsState = mutableStateOf(
            SampleTaskParams(
                task = SampleTask(id = "task-1", title = "Draft", completed = false),
                boardId = SAMPLE_BOARD_ID,
                revision = 0
            )
        )

        runComposition(
            content = {
                AtomCompositionLocals(factories = registry, owner = owner) {
                    val atom = atom<TaskAtom>(key = "task-1", params = paramsState.value)
                    SideEffect { observedAtom.value = atom }
                }
            }
        ) { frameClock ->
            frameClock.advance()

            val first = requireNotNull(observedAtom.value)
            paramsState.value = paramsState.value.copy(
                task = paramsState.value.task.copy(title = "Draft (edited)"),
                revision = 1
            )
            Snapshot.sendApplyNotifications()
            frameClock.advance()

            val second = requireNotNull(observedAtom.value)
            assertNotSame(first, second)
            assertEquals("Draft (edited)", second.get().task.title)
        }
    }

    @Test
    fun taskAtomRecreatesWhenKeyChangesWithSameParams() = runTest {
        val repository = InMemorySampleTaskRepository(
            initialData = mapOf(
                SAMPLE_BOARD_ID to listOf(
                    SampleTask(id = "task-1", title = "Draft", completed = false)
                )
            )
        )
        val diagnostics = InMemorySampleDiagnostics()
        val registry = testRegistry(repository = repository, diagnostics = diagnostics)
        val owner = testOwner()
        val observedAtom = mutableStateOf<TaskAtom?>(null)
        val keyState = mutableStateOf("task-1")
        val params = SampleTaskParams(
            task = SampleTask(id = "task-1", title = "Draft", completed = false),
            boardId = SAMPLE_BOARD_ID,
            revision = 0
        )

        runComposition(
            content = {
                AtomCompositionLocals(factories = registry, owner = owner) {
                    val atom = atom<TaskAtom>(key = keyState.value, params = params)
                    SideEffect { observedAtom.value = atom }
                }
            }
        ) { frameClock ->
            frameClock.advance()

            val first = requireNotNull(observedAtom.value)
            keyState.value = "task-1-alt"
            Snapshot.sendApplyNotifications()
            frameClock.advance()

            val second = requireNotNull(observedAtom.value)
            assertNotSame(first, second)
            assertEquals("Draft", second.get().task.title)
        }
    }

    @Test
    fun taskAtomKeepsSameInstanceWhenKeyAndParamsAreStable() = runTest {
        val repository = InMemorySampleTaskRepository(
            initialData = mapOf(
                SAMPLE_BOARD_ID to listOf(
                    SampleTask(id = "task-1", title = "Draft", completed = false)
                )
            )
        )
        val diagnostics = InMemorySampleDiagnostics()
        val registry = testRegistry(repository = repository, diagnostics = diagnostics)
        val owner = testOwner()
        val observedAtom = mutableStateOf<TaskAtom?>(null)
        val observedTick = mutableStateOf(0)
        val recomposeTick = mutableStateOf(0)
        val params = SampleTaskParams(
            task = SampleTask(id = "task-1", title = "Draft", completed = false),
            boardId = SAMPLE_BOARD_ID,
            revision = 0
        )

        runComposition(
            content = {
                AtomCompositionLocals(factories = registry, owner = owner) {
                    val atom = atom<TaskAtom>(key = "task-1", params = params)
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
            assertEquals(0, observedTick.value)

            recomposeTick.value = 1
            Snapshot.sendApplyNotifications()
            frameClock.advance()

            val second = requireNotNull(observedAtom.value)
            assertSame(first, second)
            assertEquals(1, observedTick.value)
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

    private fun testOwner(): AtomStoreOwner = object : AtomStoreOwner {
        override val atomStore = AtomStore()
    }

    private fun testRegistry(
        repository: SampleTaskRepository,
        diagnostics: SampleDiagnostics
    ): AtomFactoryRegistry {
        val entry = Atoms.factory<TaskAtom, TaskState, SampleTaskParams>(
            create = { scope, handle, _ ->
                TaskAtom(
                    scope = scope,
                    handle = handle,
                    repository = repository,
                    diagnostics = diagnostics
                )
            }
        ) { params ->
            TaskAtom.initial(params)
        }

        return object : AtomFactoryRegistry {
            override fun entryFor(type: KClass<out AtomLifecycle>) =
                if (type == TaskAtom::class) entry else null
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
