package dev.mattramotar.atom.sample

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleTaskRepositoryTest {

    @Test
    fun loadReturnsStableOrderingAcrossCalls() = runTest {
        val seed = listOf(
            SampleTask(id = "task-3", title = "third"),
            SampleTask(id = "task-1", title = "first"),
            SampleTask(id = "task-2", title = "second")
        )
        val repository = InMemorySampleTaskRepository(
            initialData = mapOf("board-a" to seed)
        )

        val first = repository.load("board-a")
        val second = repository.load("board-a")

        assertEquals(seed, first)
        assertEquals(first, second)
    }

    @Test
    fun saveIsIdempotentForEquivalentTaskValues() = runTest {
        val original = SampleTask(id = "task-1", title = "Draft API contract")
        val renamed = original.copy(title = "Finalize API contract")
        val repository = InMemorySampleTaskRepository(
            initialData = mapOf("board-a" to listOf(original))
        )

        val firstSave = repository.save("board-a", renamed)
        val secondSave = repository.save("board-a", renamed)

        assertEquals(firstSave, secondSave)
        assertEquals(1, secondSave.size)
        assertEquals(renamed, secondSave.single())
    }

    @Test
    fun syncProducesDeterministicUniqueSnapshot() = runTest {
        val repository = InMemorySampleTaskRepository()
        val synced = repository.sync(
            boardId = "board-b",
            tasks = listOf(
                SampleTask(id = "task-2", title = "Two"),
                SampleTask(id = "task-1", title = "One"),
                SampleTask(id = "task-2", title = "Two (duplicate)")
            )
        )

        assertEquals(
            listOf(
                SampleTask(id = "task-2", title = "Two"),
                SampleTask(id = "task-1", title = "One")
            ),
            synced
        )
        assertTrue(repository.load("board-b").containsAll(synced))
    }
}
