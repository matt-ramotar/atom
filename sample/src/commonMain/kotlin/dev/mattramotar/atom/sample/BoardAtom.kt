package dev.mattramotar.atom.sample

import dev.mattramotar.atom.runtime.Atom
import dev.mattramotar.atom.runtime.annotations.AutoAtom
import dev.mattramotar.atom.runtime.annotations.InitialState
import dev.mattramotar.atom.runtime.child.ChildAtomProvider
import dev.mattramotar.atom.runtime.factory.AtomFactoryRegistry
import dev.mattramotar.atom.runtime.fsm.Event
import dev.mattramotar.atom.runtime.fsm.Intent
import dev.mattramotar.atom.runtime.fsm.SideEffect
import dev.mattramotar.atom.runtime.fsm.Transition
import dev.mattramotar.atom.runtime.state.StateHandle
import dev.mattramotar.atom.runtime.state.StateHandleFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class BoardState(
    val boardId: String,
    val isLoading: Boolean,
    val filter: SampleTaskFilter,
    val tasks: List<SampleTask>,
    val visibleTasks: List<SampleTask>,
    val selectedTaskId: String?,
    val syncGeneration: Int,
    val lastEvent: String,
    val diagnostics: SampleDiagnosticsSnapshot
)

@Serializable
sealed interface BoardIntent : Intent {
    @Serializable
    data object Load : BoardIntent

    @Serializable
    data class SelectTask(val taskId: String?) : BoardIntent

    @Serializable
    data class UpdateFilter(val filter: SampleTaskFilter) : BoardIntent

    @Serializable
    data object SaveSelected : BoardIntent

    @Serializable
    data class EditSelectedTitle(val title: String) : BoardIntent

    @Serializable
    data object BurstSync : BoardIntent

    @Serializable
    data object RefreshDiagnostics : BoardIntent
}

@Serializable
sealed interface BoardEvent : Event {
    @Serializable
    data object LoadRequested : BoardEvent

    @Serializable
    data class TasksLoaded(val tasks: List<SampleTask>) : BoardEvent

    @Serializable
    data class TaskSelected(val taskId: String?) : BoardEvent

    @Serializable
    data class FilterUpdated(val filter: SampleTaskFilter) : BoardEvent

    @Serializable
    data class TaskMutationRequested(val mutation: TaskIntent) : BoardEvent

    @Serializable
    data class TaskSaved(val task: SampleTask) : BoardEvent

    @Serializable
    data object BurstRequested : BoardEvent

    @Serializable
    data object DiagnosticsRefreshRequested : BoardEvent

    @Serializable
    data class DiagnosticsUpdated(val snapshot: SampleDiagnosticsSnapshot) : BoardEvent
}

@Serializable
sealed interface BoardEffect : SideEffect {
    @Serializable
    data object LoadTasks : BoardEffect

    @Serializable
    data class MutateTask(
        val taskId: String,
        val mutation: TaskIntent
    ) : BoardEffect

    @Serializable
    data class SyncChildren(val tasks: List<SampleTask>) : BoardEffect

    @Serializable
    data class LogDiagnostics(val message: String) : BoardEffect

    @Serializable
    data object RefreshDiagnostics : BoardEffect
}

@AutoAtom
class BoardAtom(
    scope: CoroutineScope,
    handle: StateHandle<BoardState>,
    private val repository: SampleTaskRepository,
    private val diagnostics: SampleDiagnostics,
    stateHandleFactory: StateHandleFactory,
    registry: AtomFactoryRegistry
) : Atom<BoardState, BoardIntent, BoardEvent, BoardEffect>(scope, handle) {
    companion object {
        @InitialState
        fun initial(params: SampleBoardParams): BoardState {
            return BoardState(
                boardId = params.boardId,
                isLoading = false,
                filter = SampleTaskFilter.ALL,
                tasks = emptyList(),
                visibleTasks = emptyList(),
                selectedTaskId = null,
                syncGeneration = 0,
                lastEvent = "idle",
                diagnostics = emptySampleDiagnosticsSnapshot()
            )
        }
    }

    private val childProvider = ChildAtomProvider(
        parentScope = scope,
        stateHandleFactory = stateHandleFactory,
        registry = registry
    )
    private var effectsJob: Job? = null

    override fun onStart() {
        super.onStart()
        effectsJob?.cancel()
        effectsJob = scope.launch {
            effects.collect { effect ->
                handleEffect(effect)
            }
        }
    }

    override fun intent(intent: BoardIntent) {
        when (intent) {
            BoardIntent.Load -> dispatch(BoardEvent.LoadRequested)
            is BoardIntent.SelectTask -> dispatch(BoardEvent.TaskSelected(intent.taskId))
            is BoardIntent.UpdateFilter -> dispatch(BoardEvent.FilterUpdated(intent.filter))
            BoardIntent.SaveSelected -> dispatch(
                BoardEvent.TaskMutationRequested(TaskIntent.ToggleCompleted)
            )

            is BoardIntent.EditSelectedTitle -> dispatch(
                BoardEvent.TaskMutationRequested(TaskIntent.EditTitle(intent.title))
            )

            BoardIntent.BurstSync -> dispatch(BoardEvent.BurstRequested)
            BoardIntent.RefreshDiagnostics -> dispatch(BoardEvent.DiagnosticsRefreshRequested)
        }
    }

    override fun reduce(state: BoardState, event: BoardEvent): Transition<BoardState, BoardEffect> {
        return when (event) {
            BoardEvent.LoadRequested -> Transition(
                to = state.copy(
                    isLoading = true,
                    lastEvent = "load_requested"
                ),
                effects = listOf(
                    BoardEffect.LogDiagnostics("load_requested"),
                    BoardEffect.LoadTasks,
                    BoardEffect.RefreshDiagnostics
                )
            )

            is BoardEvent.TasksLoaded -> {
                val visible = event.tasks.filterFor(state.filter)
                val selectedTaskId = state.selectedTaskId
                    ?.takeIf { selected -> event.tasks.any { it.id == selected } }
                    ?: visible.firstOrNull()?.id
                Transition(
                    to = state.copy(
                        isLoading = false,
                        tasks = event.tasks,
                        visibleTasks = visible,
                        selectedTaskId = selectedTaskId,
                        lastEvent = "tasks_loaded"
                    ),
                    effects = listOf(
                        BoardEffect.SyncChildren(event.tasks),
                        BoardEffect.LogDiagnostics("tasks_loaded:${event.tasks.size}"),
                        BoardEffect.RefreshDiagnostics
                    )
                )
            }

            is BoardEvent.TaskSelected -> Transition(
                to = state.copy(
                    selectedTaskId = event.taskId,
                    lastEvent = "task_selected"
                ),
                effects = listOf(
                    BoardEffect.LogDiagnostics("task_selected:${event.taskId ?: "none"}"),
                    BoardEffect.RefreshDiagnostics
                )
            )

            is BoardEvent.FilterUpdated -> {
                val visible = state.tasks.filterFor(event.filter)
                Transition(
                    to = state.copy(
                        filter = event.filter,
                        visibleTasks = visible,
                        selectedTaskId = state.selectedTaskId
                            ?.takeIf { selected -> visible.any { it.id == selected } }
                            ?: visible.firstOrNull()?.id,
                        lastEvent = "filter_updated"
                    ),
                    effects = listOf(
                        BoardEffect.LogDiagnostics("filter_updated:${event.filter.name}"),
                        BoardEffect.RefreshDiagnostics
                    )
                )
            }

            is BoardEvent.TaskMutationRequested -> {
                val selectedTaskId = state.selectedTaskId
                if (selectedTaskId == null || state.tasks.none { task -> task.id == selectedTaskId }) {
                    Transition(
                        to = state.copy(lastEvent = "mutation_skipped"),
                        effects = listOf(
                            BoardEffect.LogDiagnostics("mutation_skipped:no_selection"),
                            BoardEffect.RefreshDiagnostics
                        )
                    )
                } else {
                    Transition(
                        to = state.copy(lastEvent = "task_mutation_requested"),
                        effects = listOf(
                            BoardEffect.MutateTask(
                                taskId = selectedTaskId,
                                mutation = event.mutation
                            )
                        )
                    )
                }
            }

            is BoardEvent.TaskSaved -> {
                val updatedTasks = state.tasks.updateTask(event.task)
                val visible = updatedTasks.filterFor(state.filter)
                Transition(
                    to = state.copy(
                        tasks = updatedTasks,
                        visibleTasks = visible,
                        lastEvent = "task_saved"
                    ),
                    effects = listOf(
                        BoardEffect.LogDiagnostics("task_saved:${event.task.id}"),
                        BoardEffect.RefreshDiagnostics
                    )
                )
            }

            BoardEvent.BurstRequested -> Transition(
                to = state.copy(
                    syncGeneration = state.syncGeneration + 1,
                    lastEvent = "burst_requested"
                ),
                effects = listOf(
                    BoardEffect.SyncChildren(state.tasks),
                    BoardEffect.LogDiagnostics("burst_sync:${state.syncGeneration + 1}"),
                    BoardEffect.RefreshDiagnostics
                )
            )

            BoardEvent.DiagnosticsRefreshRequested -> Transition(
                to = state,
                effects = listOf(BoardEffect.RefreshDiagnostics)
            )

            is BoardEvent.DiagnosticsUpdated -> Transition(
                to = state.copy(diagnostics = event.snapshot)
            )
        }
    }

    override fun onStopInternal() {
        effectsJob?.cancel()
        effectsJob = null
    }

    override fun onDisposeInternal() {
        effectsJob?.cancel()
        effectsJob = null
        scope.launch(NonCancellable) {
            childProvider.clear()
            diagnostics.clear()
        }
    }

    private suspend fun handleEffect(effect: BoardEffect) {
        when (effect) {
            BoardEffect.LoadTasks -> {
                val loaded = repository.load(get().boardId)
                dispatch(BoardEvent.TasksLoaded(loaded))
            }

            is BoardEffect.MutateTask -> {
                val snapshot = get()
                val selected = snapshot.tasks.firstOrNull { task -> task.id == effect.taskId }
                    ?: return
                val taskAtom = childProvider.getOrCreate(
                    type = TaskAtom::class,
                    id = effect.taskId,
                    params = SampleTaskParams(
                        task = selected,
                        boardId = snapshot.boardId,
                        revision = snapshot.syncGeneration
                    )
                )
                val updated = taskAtom.submit(effect.mutation)
                dispatch(BoardEvent.TaskSaved(updated))
            }

            is BoardEffect.SyncChildren -> {
                val boardState = get()
                val childParams: Map<Any, SampleTaskParams> = effect.tasks.associate { task ->
                    task.id as Any to SampleTaskParams(
                        task = task,
                        boardId = boardState.boardId,
                        revision = boardState.syncGeneration
                    )
                }
                childProvider.sync(TaskAtom::class, childParams)
                diagnostics.setActiveAtoms(
                    setOf("BoardAtom[${boardState.boardId}]") + effect.tasks.map { task ->
                        "TaskAtom[${task.id}]"
                    }
                )
            }

            is BoardEffect.LogDiagnostics -> {
                diagnostics.recordEvent(atom = "BoardAtom", value = get().lastEvent)
                diagnostics.recordEffect(atom = "BoardAtom", value = effect.message)
                diagnostics.recordState(
                    atom = "BoardAtom",
                    value = "tasks=${get().tasks.size}, visible=${get().visibleTasks.size}, selected=${get().selectedTaskId ?: "none"}"
                )
            }

            BoardEffect.RefreshDiagnostics -> {
                val snapshot = diagnostics.snapshot()
                dispatch(BoardEvent.DiagnosticsUpdated(snapshot))
            }
        }
    }

    private fun List<SampleTask>.filterFor(taskFilter: SampleTaskFilter): List<SampleTask> {
        return filter { task -> task.matches(taskFilter) }
    }

    private fun List<SampleTask>.updateTask(updated: SampleTask): List<SampleTask> {
        return map { existing ->
            if (existing.id == updated.id) updated else existing
        }
    }
}
