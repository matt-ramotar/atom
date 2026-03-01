package dev.mattramotar.atom.sample

import kotlinx.serialization.Serializable

const val SAMPLE_BOARD_ID: String = "main-board"

@Serializable
data class SampleTask(
    val id: String,
    val title: String,
    val completed: Boolean = false,
    val notes: String = ""
)

@Serializable
enum class SampleTaskFilter {
    ALL,
    ACTIVE,
    COMPLETED
}

@Serializable
data class SampleBoardParams(
    val boardId: String = SAMPLE_BOARD_ID
)

@Serializable
data class SampleTaskParams(
    val taskId: String,
    val boardId: String = SAMPLE_BOARD_ID,
    val revision: Int = 0
)

fun SampleTask.matches(filter: SampleTaskFilter): Boolean = when (filter) {
    SampleTaskFilter.ALL -> true
    SampleTaskFilter.ACTIVE -> !completed
    SampleTaskFilter.COMPLETED -> completed
}

fun sampleSeedTasks(): List<SampleTask> = listOf(
    SampleTask(
        id = "task-1",
        title = "Draft BoardAtom state model",
        completed = true,
        notes = "Seed task completed in Phase 1 scaffold."
    ),
    SampleTask(
        id = "task-2",
        title = "Wire TaskAtom keyed instances",
        completed = false,
        notes = "Demonstrates key and params identity behavior."
    ),
    SampleTask(
        id = "task-3",
        title = "Expose diagnostics stream",
        completed = false,
        notes = "Used by diagnostics panel to inspect transitions."
    )
)
