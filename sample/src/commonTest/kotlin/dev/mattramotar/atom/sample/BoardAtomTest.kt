package dev.mattramotar.atom.sample

import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.factory.Atoms
import dev.mattramotar.atom.runtime.state.InMemoryStateHandle
import dev.mattramotar.atom.runtime.state.InMemoryStateHandleFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BoardAtomTest {

    @Test
    fun loadIntentTransitionsFromLoadingToLoadedState() = runTest {
        val seed = listOf(
            SampleTask(id = "task-1", title = "one"),
            SampleTask(id = "task-2", title = "two")
        )
        val (atom, _, diagnostics) = createBoardAtom(
            scope = CoroutineScope(coroutineContext),
            seed = seed
        )

        atom.intent(BoardIntent.Load)
        drainScheduler()

        val state = atom.get()
        assertFalse(state.isLoading)
        assertEquals(seed, state.tasks)
        assertEquals(seed, state.visibleTasks)
        assertEquals("task-1", state.selectedTaskId)

        val snapshot = diagnostics.snapshot()
        assertEquals(
            listOf("BoardAtom[main-board]", "TaskAtom[task-1]", "TaskAtom[task-2]"),
            snapshot.activeAtoms
        )
        assertEquals(snapshot.activeAtoms, state.diagnostics.activeAtoms)
        assertTrue(state.diagnostics.events.isNotEmpty())
        assertTrue(state.diagnostics.effects.isNotEmpty())

        atom.onStop()
        atom.onDispose()
        drainScheduler()
    }

    @Test
    fun saveSelectedAppliesDeterministicTaskMutation() = runTest {
        val seed = listOf(
            SampleTask(id = "task-1", title = "one", completed = false),
            SampleTask(id = "task-2", title = "two", completed = false)
        )
        val (atom, _, _) = createBoardAtom(
            scope = CoroutineScope(coroutineContext),
            seed = seed
        )

        atom.intent(BoardIntent.Load)
        drainScheduler()

        atom.intent(BoardIntent.SelectTask("task-2"))
        drainScheduler()
        atom.intent(BoardIntent.SaveSelected)
        drainScheduler()

        val updated = atom.get().tasks.first { it.id == "task-2" }
        assertTrue(updated.completed)
        assertEquals("two", updated.title)

        atom.onStop()
        atom.onDispose()
        drainScheduler()
    }

    @Test
    fun childSyncTracksAddAndRemoveAcrossLoads() = runTest {
        val seed = listOf(
            SampleTask(id = "task-1", title = "one"),
            SampleTask(id = "task-2", title = "two"),
            SampleTask(id = "task-3", title = "three")
        )
        val (atom, repository, diagnostics) = createBoardAtom(
            scope = CoroutineScope(coroutineContext),
            seed = seed
        )

        atom.intent(BoardIntent.Load)
        drainScheduler()

        assertEquals(
            listOf(
                "BoardAtom[main-board]",
                "TaskAtom[task-1]",
                "TaskAtom[task-2]",
                "TaskAtom[task-3]"
            ),
            diagnostics.snapshot().activeAtoms
        )

        repository.sync(
            boardId = SAMPLE_BOARD_ID,
            tasks = listOf(SampleTask(id = "task-1", title = "one"))
        )
        atom.intent(BoardIntent.Load)
        drainScheduler()

        assertEquals(
            listOf("BoardAtom[main-board]", "TaskAtom[task-1]"),
            diagnostics.snapshot().activeAtoms
        )

        atom.onStop()
        atom.onDispose()
        drainScheduler()
    }

    @Test
    fun diagnosticsRefreshIntentUpdatesBoardStateSnapshot() = runTest {
        val seed = listOf(SampleTask(id = "task-1", title = "one"))
        val (atom, _, _) = createBoardAtom(
            scope = CoroutineScope(coroutineContext),
            seed = seed
        )

        atom.intent(BoardIntent.Load)
        drainScheduler()
        atom.intent(BoardIntent.RefreshDiagnostics)
        drainScheduler()

        val state = atom.get()
        assertTrue(state.diagnostics.activeAtoms.contains("BoardAtom[main-board]"))
        assertTrue(state.diagnostics.states.any { it.atom == "BoardAtom" })

        atom.onStop()
        atom.onDispose()
        drainScheduler()
    }

    private fun createBoardAtom(
        scope: CoroutineScope,
        seed: List<SampleTask>
    ): Triple<BoardAtom, SampleTaskRepository, SampleDiagnostics> {
        val repository = InMemorySampleTaskRepository(
            initialData = mapOf(SAMPLE_BOARD_ID to seed)
        )
        val diagnostics = InMemorySampleDiagnostics()
        val atom = BoardAtom(
            scope = scope,
            handle = InMemoryStateHandle(BoardAtom.initial(SampleBoardParams())),
            repository = repository,
            diagnostics = diagnostics,
            stateHandleFactory = InMemoryStateHandleFactory,
            registry = childRegistry(
                repository = repository,
                diagnostics = diagnostics
            )
        )
        atom.onStart()
        return Triple(atom, repository, diagnostics)
    }

    private fun childRegistry(
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
            },
            initial = { params ->
                TaskState(
                    boardId = params.boardId,
                    task = params.task,
                    revision = params.revision
                )
            }
        )

        return object : AtomFactoryRegistry {
            override fun entryFor(type: KClass<out AtomLifecycle>) =
                if (type == TaskAtom::class) entry else null
        }
    }

    private fun TestScope.drainScheduler() {
        repeat(6) {
            runCurrent()
        }
    }
}
