package dev.mattramotar.atom.sample

import dev.mattramotar.atom.runtime.state.InMemoryStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskAtomTest {

    @Test
    fun keyedTaskAtomsMutateIndependently() = runTest {
        val taskOne = SampleTask(id = "task-1", title = "One", completed = false)
        val taskTwo = SampleTask(id = "task-2", title = "Two", completed = false)
        val repository = InMemorySampleTaskRepository(
            initialData = mapOf(SAMPLE_BOARD_ID to listOf(taskOne, taskTwo))
        )
        val diagnostics = InMemorySampleDiagnostics()
        val firstAtom = TaskAtom(
            scope = CoroutineScope(coroutineContext),
            handle = InMemoryStateHandle(
                TaskAtom.initial(
                    SampleTaskParams(
                        task = taskOne,
                        boardId = SAMPLE_BOARD_ID
                    )
                )
            ),
            repository = repository,
            diagnostics = diagnostics
        )
        val secondAtom = TaskAtom(
            scope = CoroutineScope(coroutineContext),
            handle = InMemoryStateHandle(
                TaskAtom.initial(
                    SampleTaskParams(
                        task = taskTwo,
                        boardId = SAMPLE_BOARD_ID
                    )
                )
            ),
            repository = repository,
            diagnostics = diagnostics
        )

        firstAtom.onStart()
        secondAtom.onStart()
        firstAtom.submit(TaskIntent.ToggleCompleted)

        assertTrue(firstAtom.get().task.completed)
        assertFalse(secondAtom.get().task.completed)

        val snapshot = repository.load(SAMPLE_BOARD_ID).associateBy { it.id }
        assertTrue(requireNotNull(snapshot["task-1"]).completed)
        assertFalse(requireNotNull(snapshot["task-2"]).completed)

        firstAtom.onStop()
        firstAtom.onDispose()
        secondAtom.onStop()
        secondAtom.onDispose()
    }

    @Test
    fun editTitlePersistsAndUpdatesTaskState() = runTest {
        val task = SampleTask(id = "task-1", title = "Draft", completed = false)
        val repository = InMemorySampleTaskRepository(
            initialData = mapOf(SAMPLE_BOARD_ID to listOf(task))
        )
        val diagnostics = InMemorySampleDiagnostics()
        val atom = TaskAtom(
            scope = CoroutineScope(coroutineContext),
            handle = InMemoryStateHandle(
                TaskAtom.initial(
                    SampleTaskParams(
                        task = task,
                        boardId = SAMPLE_BOARD_ID
                    )
                )
            ),
            repository = repository,
            diagnostics = diagnostics
        )

        atom.onStart()
        val updated = atom.submit(TaskIntent.EditTitle("Published"))

        assertEquals("Published", updated.title)
        assertEquals("Published", atom.get().task.title)
        assertEquals("Published", repository.load(SAMPLE_BOARD_ID).single().title)

        atom.onStop()
        atom.onDispose()
    }
}
