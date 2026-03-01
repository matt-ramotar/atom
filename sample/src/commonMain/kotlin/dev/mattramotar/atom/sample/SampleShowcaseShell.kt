package dev.mattramotar.atom.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mattramotar.atom.generated.rememberBoardAtom

@Composable
fun SampleShowcaseShell(modifier: Modifier = Modifier) {
    val atom = rememberBoardAtom(
        key = "showcase-board",
        params = SampleBoardParams(boardId = SAMPLE_BOARD_ID)
    )
    val state by atom.state.collectAsState()
    val selectedTask = state.tasks.firstOrNull { it.id == state.selectedTaskId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Atom Showcase Board (Phase 2)",
            style = MaterialTheme.typography.headlineSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShellZone(
                title = "Task List",
                body = "Loaded tasks: ${state.tasks.size}\nVisible tasks: ${state.visibleTasks.size}\nSelected: ${state.selectedTaskId ?: "none"}",
                modifier = Modifier.weight(1f)
            )
            ShellZone(
                title = "Task Detail",
                body = selectedTask?.let { task ->
                    "Task ${task.id}\nTitle: ${task.title}\nCompleted: ${task.completed}\nNotes: ${task.notes}"
                } ?: "No task selected.",
                modifier = Modifier.weight(1f)
            )
            ShellZone(
                title = "Diagnostics",
                body = "Loading: ${state.isLoading}\nFilter: ${state.filter}\nSync generation: ${state.syncGeneration}\nLast event: ${state.lastEvent}",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { atom.intent(BoardIntent.Load) }) {
                Text("Load")
            }
            OutlinedButton(onClick = { atom.intent(BoardIntent.BurstSync) }) {
                Text("Burst Sync")
            }
            OutlinedButton(
                onClick = { atom.intent(BoardIntent.SaveSelected) }
            ) {
                Text("Toggle Selected")
            }
            OutlinedButton(
                onClick = {
                    val nextTitle = selectedTask?.title?.let { title ->
                        if (title.endsWith(" (edited)")) {
                            title.removeSuffix(" (edited)")
                        } else {
                            "$title (edited)"
                        }
                    } ?: return@OutlinedButton
                    atom.intent(BoardIntent.EditSelectedTitle(nextTitle))
                }
            ) {
                Text("Edit Selected")
            }
            OutlinedButton(
                onClick = {
                    val nextFilter = when (state.filter) {
                        SampleTaskFilter.ALL -> SampleTaskFilter.ACTIVE
                        SampleTaskFilter.ACTIVE -> SampleTaskFilter.COMPLETED
                        SampleTaskFilter.COMPLETED -> SampleTaskFilter.ALL
                    }
                    atom.intent(BoardIntent.UpdateFilter(nextFilter))
                }
            ) {
                Text("Cycle Filter")
            }
            OutlinedButton(
                onClick = {
                    val nextTaskId = state.visibleTasks.firstOrNull { it.id != state.selectedTaskId }?.id
                        ?: state.visibleTasks.firstOrNull()?.id
                    atom.intent(BoardIntent.SelectTask(nextTaskId))
                }
            ) {
                Text("Select Next")
            }
        }
    }
}

@Composable
private fun ShellZone(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
