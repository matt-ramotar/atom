package dev.mattramotar.atom.sample

import dev.mattramotar.atom.runtime.AtomLifecycle
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.factory.Atoms
import dev.mattramotar.atom.runtime.state.InMemoryStateHandle
import dev.mattramotar.atom.runtime.state.InMemoryStateHandleFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        runCurrent()
        runCurrent()

        val state = atom.get()
        assertFalse(state.isLoading)
        assertEquals(seed, state.tasks)
        assertEquals(seed, state.visibleTasks)
        assertEquals("task-1", state.selectedTaskId)

        val snapshot = diagnostics.snapshot()
        assertEquals(
            listOf("BoardAtom[main-board]", "BoardTaskChildAtom[task-1]", "BoardTaskChildAtom[task-2]"),
            snapshot.activeAtoms
        )

        atom.onStop()
        atom.onDispose()
        runCurrent()
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
        runCurrent()
        runCurrent()

        atom.intent(BoardIntent.SelectTask("task-2"))
        runCurrent()
        atom.intent(BoardIntent.SaveSelected)
        runCurrent()
        runCurrent()

        val updated = atom.get().tasks.first { it.id == "task-2" }
        assertTrue(updated.completed)
        assertEquals("Saved by BoardAtom", updated.notes)

        atom.onStop()
        atom.onDispose()
        runCurrent()
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
        runCurrent()
        runCurrent()

        assertEquals(
            listOf(
                "BoardAtom[main-board]",
                "BoardTaskChildAtom[task-1]",
                "BoardTaskChildAtom[task-2]",
                "BoardTaskChildAtom[task-3]"
            ),
            diagnostics.snapshot().activeAtoms
        )

        repository.sync(
            boardId = SAMPLE_BOARD_ID,
            tasks = listOf(SampleTask(id = "task-1", title = "one"))
        )
        atom.intent(BoardIntent.Load)
        runCurrent()
        runCurrent()

        assertEquals(
            listOf("BoardAtom[main-board]", "BoardTaskChildAtom[task-1]"),
            diagnostics.snapshot().activeAtoms
        )

        atom.onStop()
        atom.onDispose()
        runCurrent()
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
            registry = childRegistry()
        )
        atom.onStart()
        return Triple(atom, repository, diagnostics)
    }

    private fun childRegistry(): AtomFactoryRegistry {
        val entry = Atoms.factory<BoardTaskChildAtom, BoardTaskChildState, BoardTaskChildParams>(
            create = { scope, handle, _ ->
                BoardTaskChildAtom(scope, handle)
            },
            initial = { params ->
                BoardTaskChildState(taskId = params.taskId)
            }
        )

        return object : AtomFactoryRegistry {
            override fun entryFor(type: KClass<out AtomLifecycle>) =
                if (type == BoardTaskChildAtom::class) entry else null
        }
    }
}
