package dev.mattramotar.atom.sample

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SampleTaskRepository {
    suspend fun load(boardId: String): List<SampleTask>

    suspend fun save(boardId: String, task: SampleTask): List<SampleTask>

    suspend fun sync(boardId: String, tasks: List<SampleTask>): List<SampleTask>

    suspend fun reset(boardId: String): List<SampleTask>
}

class InMemorySampleTaskRepository(
    initialData: Map<String, List<SampleTask>> = mapOf(SAMPLE_BOARD_ID to sampleSeedTasks())
) : SampleTaskRepository {
    private val mutex = Mutex()
    private val seedByBoard: Map<String, LinkedHashMap<String, SampleTask>> =
        initialData.mapValues { (_, tasks) ->
            tasks.toDeterministicMap()
        }
    private val tasksByBoard: MutableMap<String, LinkedHashMap<String, SampleTask>> =
        seedByBoard.mapValuesTo(mutableMapOf()) { (_, tasks) ->
            LinkedHashMap(tasks)
        }

    override suspend fun load(boardId: String): List<SampleTask> = mutex.withLock {
        snapshot(boardId)
    }

    override suspend fun save(boardId: String, task: SampleTask): List<SampleTask> = mutex.withLock {
        val board = boardFor(boardId)
        val existing = board[task.id]
        if (existing != task) {
            board[task.id] = task
        }
        board.values.toList()
    }

    override suspend fun sync(boardId: String, tasks: List<SampleTask>): List<SampleTask> = mutex.withLock {
        val next = tasks.toDeterministicMap()
        tasksByBoard[boardId] = next
        next.values.toList()
    }

    override suspend fun reset(boardId: String): List<SampleTask> = mutex.withLock {
        val reset = seedByBoard[boardId]?.let(::LinkedHashMap) ?: linkedMapOf()
        tasksByBoard[boardId] = reset
        reset.values.toList()
    }

    private fun snapshot(boardId: String): List<SampleTask> = boardFor(boardId).values.toList()

    private fun boardFor(boardId: String): LinkedHashMap<String, SampleTask> =
        tasksByBoard.getOrPut(boardId) { linkedMapOf() }

    private fun List<SampleTask>.toDeterministicMap(): LinkedHashMap<String, SampleTask> {
        val map = linkedMapOf<String, SampleTask>()
        for (task in this) {
            if (!map.containsKey(task.id)) {
                map[task.id] = task
            }
        }
        return map
    }
}
