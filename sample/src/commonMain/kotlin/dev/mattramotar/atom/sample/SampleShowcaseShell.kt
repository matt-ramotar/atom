package dev.mattramotar.atom.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mattramotar.atom.generated.rememberBoardAtom

@Composable
fun SampleShowcaseShell(modifier: Modifier = Modifier) {
    var keyDraft by remember { mutableStateOf("showcase-board") }
    var boardIdDraft by remember { mutableStateOf(SAMPLE_BOARD_ID) }
    var appliedKey by remember { mutableStateOf("showcase-board") }
    var appliedBoardId by remember { mutableStateOf(SAMPLE_BOARD_ID) }
    var burstIterationsDraft by remember { mutableStateOf("6") }
    var pendingIdentityProbe by remember { mutableStateOf<String?>(null) }

    val atom = rememberBoardAtom(
        key = appliedKey,
        params = SampleBoardParams(boardId = appliedBoardId)
    )
    val state by atom.state.collectAsState()
    val selectedTask = state.tasks.firstOrNull { task -> task.id == state.selectedTaskId }
    var titleDraft by remember { mutableStateOf("") }

    val atomInstanceToken = remember(atom) { "h${atom.hashCode().toString(16)}" }
    var recreationCount by remember { mutableStateOf(0) }
    var lastObservedToken by remember { mutableStateOf(atomInstanceToken) }

    LaunchedEffect(atomInstanceToken) {
        if (lastObservedToken != atomInstanceToken) {
            recreationCount += 1
            lastObservedToken = atomInstanceToken
        }
    }

    LaunchedEffect(atomInstanceToken, pendingIdentityProbe) {
        val probe = pendingIdentityProbe ?: return@LaunchedEffect
        atom.intent(BoardIntent.IdentityProbe(probe))
        pendingIdentityProbe = null
    }

    LaunchedEffect(selectedTask?.id, selectedTask?.title) {
        titleDraft = selectedTask?.title.orEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Atom Showcase Board (Phase 3)",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Three-zone showcase with identity and burst interaction lab controls.",
            style = MaterialTheme.typography.bodyMedium
        )

        IdentityLabZone(
            appliedKey = appliedKey,
            appliedBoardId = appliedBoardId,
            keyDraft = keyDraft,
            boardIdDraft = boardIdDraft,
            burstIterationsDraft = burstIterationsDraft,
            atomInstanceToken = atomInstanceToken,
            recreationCount = recreationCount,
            burstRequested = state.burstRequested,
            burstObserved = state.burstObserved,
            burstDropped = state.burstDropped,
            pendingBurstMutations = state.pendingBurstMutations,
            onKeyDraftChanged = { keyDraft = it },
            onBoardIdDraftChanged = { boardIdDraft = it },
            onBurstIterationsDraftChanged = { burstIterationsDraft = it },
            onApplyKey = {
                val normalized = keyDraft.ifBlank { "showcase-board" }
                appliedKey = normalized
                pendingIdentityProbe = "key=$normalized"
            },
            onApplyBoardId = {
                val normalized = boardIdDraft.ifBlank { SAMPLE_BOARD_ID }
                appliedBoardId = normalized
                pendingIdentityProbe = "boardId=$normalized"
            },
            onTriggerBurst = {
                val iterations = burstIterationsDraft.toIntOrNull()?.coerceAtLeast(1) ?: 1
                atom.intent(BoardIntent.TriggerBurst(iterations))
            }
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compactLayout = maxWidth < 1080.dp

            if (compactLayout) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TaskListZone(
                        state = state,
                        onLoad = { atom.intent(BoardIntent.Load) },
                        onSelectTask = { taskId -> atom.intent(BoardIntent.SelectTask(taskId)) },
                        onFilterSelected = { filter -> atom.intent(BoardIntent.UpdateFilter(filter)) },
                        onToggleSelected = { atom.intent(BoardIntent.SaveSelected) },
                        onCycleFilter = {
                            atom.intent(BoardIntent.UpdateFilter(state.filter.next()))
                        },
                        onBurstSync = { atom.intent(BoardIntent.TriggerBurst(3)) }
                    )
                    TaskDetailZone(
                        selectedTask = selectedTask,
                        titleDraft = titleDraft,
                        onTitleDraftChanged = { next -> titleDraft = next },
                        onApplyTitle = {
                            val nextTitle = titleDraft.trim()
                            if (nextTitle.isNotEmpty()) {
                                atom.intent(BoardIntent.EditSelectedTitle(nextTitle))
                            }
                        },
                        onToggleSelected = { atom.intent(BoardIntent.SaveSelected) }
                    )
                    DiagnosticsZone(
                        state = state,
                        onRefresh = { atom.intent(BoardIntent.RefreshDiagnostics) }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TaskListZone(
                        state = state,
                        modifier = Modifier.weight(1f),
                        onLoad = { atom.intent(BoardIntent.Load) },
                        onSelectTask = { taskId -> atom.intent(BoardIntent.SelectTask(taskId)) },
                        onFilterSelected = { filter -> atom.intent(BoardIntent.UpdateFilter(filter)) },
                        onToggleSelected = { atom.intent(BoardIntent.SaveSelected) },
                        onCycleFilter = {
                            atom.intent(BoardIntent.UpdateFilter(state.filter.next()))
                        },
                        onBurstSync = { atom.intent(BoardIntent.TriggerBurst(3)) }
                    )
                    TaskDetailZone(
                        selectedTask = selectedTask,
                        titleDraft = titleDraft,
                        modifier = Modifier.weight(1f),
                        onTitleDraftChanged = { next -> titleDraft = next },
                        onApplyTitle = {
                            val nextTitle = titleDraft.trim()
                            if (nextTitle.isNotEmpty()) {
                                atom.intent(BoardIntent.EditSelectedTitle(nextTitle))
                            }
                        },
                        onToggleSelected = { atom.intent(BoardIntent.SaveSelected) }
                    )
                    DiagnosticsZone(
                        state = state,
                        modifier = Modifier.weight(1f),
                        onRefresh = { atom.intent(BoardIntent.RefreshDiagnostics) }
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityLabZone(
    appliedKey: String,
    appliedBoardId: String,
    keyDraft: String,
    boardIdDraft: String,
    burstIterationsDraft: String,
    atomInstanceToken: String,
    recreationCount: Int,
    burstRequested: Int,
    burstObserved: Int,
    burstDropped: Int,
    pendingBurstMutations: Int,
    onKeyDraftChanged: (String) -> Unit,
    onBoardIdDraftChanged: (String) -> Unit,
    onBurstIterationsDraftChanged: (String) -> Unit,
    onApplyKey: () -> Unit,
    onApplyBoardId: () -> Unit,
    onTriggerBurst: () -> Unit
) {
    ZoneContainer(title = "Identity and Burst Lab") {
        Text(
            text = "Applied key=$appliedKey, params.boardId=$appliedBoardId",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Instance token=$atomInstanceToken, recreations=$recreationCount",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Burst requested=$burstRequested, observed=$burstObserved, dropped=$burstDropped, pending=$pendingBurstMutations",
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = keyDraft,
                onValueChange = onKeyDraftChanged,
                label = { Text("Atom key") }
            )
            OutlinedButton(onClick = onApplyKey) {
                Text("Apply Key")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = boardIdDraft,
                onValueChange = onBoardIdDraftChanged,
                label = { Text("Board param") }
            )
            OutlinedButton(onClick = onApplyBoardId) {
                Text("Apply Params")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = burstIterationsDraft,
                onValueChange = onBurstIterationsDraftChanged,
                label = { Text("Burst iterations") }
            )
            OutlinedButton(onClick = onTriggerBurst) {
                Text("Trigger Burst")
            }
        }
    }
}

@Composable
private fun TaskListZone(
    state: BoardState,
    onLoad: () -> Unit,
    onSelectTask: (String?) -> Unit,
    onFilterSelected: (SampleTaskFilter) -> Unit,
    onToggleSelected: () -> Unit,
    onCycleFilter: () -> Unit,
    onBurstSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZoneContainer(title = "Task List", modifier = modifier) {
        Text(
            text = "Loaded=${state.tasks.size}, Visible=${state.visibleTasks.size}, Selected=${state.selectedTaskId ?: "none"}",
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLoad) { Text("Load") }
            OutlinedButton(onClick = onCycleFilter) { Text("Cycle Filter") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onToggleSelected, enabled = state.selectedTaskId != null) {
                Text("Toggle Selected")
            }
            OutlinedButton(onClick = onBurstSync) { Text("Quick Burst") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterOption(
                selected = state.filter == SampleTaskFilter.ALL,
                label = "All",
                onClick = { onFilterSelected(SampleTaskFilter.ALL) }
            )
            FilterOption(
                selected = state.filter == SampleTaskFilter.ACTIVE,
                label = "Active",
                onClick = { onFilterSelected(SampleTaskFilter.ACTIVE) }
            )
            FilterOption(
                selected = state.filter == SampleTaskFilter.COMPLETED,
                label = "Completed",
                onClick = { onFilterSelected(SampleTaskFilter.COMPLETED) }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.visibleTasks.isEmpty()) {
                Text("No tasks visible for this filter.", style = MaterialTheme.typography.bodyMedium)
            }
            state.visibleTasks.forEach { task ->
                val selected = task.id == state.selectedTaskId
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectTask(task.id) }
                ) {
                    val status = if (task.completed) "done" else "open"
                    val prefix = if (selected) "* " else ""
                    Text("$prefix${task.id}: ${task.title} [$status]")
                }
            }
        }
    }
}

@Composable
private fun TaskDetailZone(
    selectedTask: SampleTask?,
    titleDraft: String,
    onTitleDraftChanged: (String) -> Unit,
    onApplyTitle: () -> Unit,
    onToggleSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZoneContainer(title = "Task Detail", modifier = modifier) {
        if (selectedTask == null) {
            Text("No task selected.", style = MaterialTheme.typography.bodyMedium)
            return@ZoneContainer
        }

        Text("Task: ${selectedTask.id}", style = MaterialTheme.typography.titleSmall)
        Text("Completed: ${selectedTask.completed}", style = MaterialTheme.typography.bodyMedium)
        Text("Notes: ${selectedTask.notes}", style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = titleDraft,
            onValueChange = onTitleDraftChanged,
            label = { Text("Title") }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onApplyTitle, enabled = titleDraft.isNotBlank()) {
                Text("Apply Title")
            }
            OutlinedButton(onClick = onToggleSelected) {
                Text("Toggle Completed")
            }
        }
    }
}

@Composable
private fun DiagnosticsZone(
    state: BoardState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZoneContainer(title = "Diagnostics", modifier = modifier) {
        OutlinedButton(onClick = onRefresh) {
            Text("Refresh Diagnostics")
        }

        Text(
            text = "Board=${state.boardId}, Loading=${state.isLoading}, Filter=${state.filter}, Sync=${state.syncGeneration}, Last=${state.lastEvent}",
            style = MaterialTheme.typography.bodySmall
        )

        val snapshot = state.diagnostics
        Text("Active Atoms", style = MaterialTheme.typography.titleSmall)
        Text(
            snapshot.activeAtoms.joinToString(separator = "\n").ifBlank { "(none)" },
            style = MaterialTheme.typography.bodySmall
        )

        DiagnosticsRecords(title = "Recent Events", records = snapshot.events)
        DiagnosticsRecords(title = "Recent Effects", records = snapshot.effects)
        DiagnosticsRecords(title = "Latest States", records = snapshot.states)
    }
}

@Composable
private fun DiagnosticsRecords(
    title: String,
    records: List<SampleDiagnosticsRecord>
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    val display = records.takeLast(6).asReversed()
    if (display.isEmpty()) {
        Text("(empty)", style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        display.forEach { record ->
            Text("${record.atom}: ${record.value}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ZoneContainer(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun FilterOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick) {
        val prefix = if (selected) "* " else ""
        Text("$prefix$label")
    }
}

private fun SampleTaskFilter.next(): SampleTaskFilter {
    return when (this) {
        SampleTaskFilter.ALL -> SampleTaskFilter.ACTIVE
        SampleTaskFilter.ACTIVE -> SampleTaskFilter.COMPLETED
        SampleTaskFilter.COMPLETED -> SampleTaskFilter.ALL
    }
}
