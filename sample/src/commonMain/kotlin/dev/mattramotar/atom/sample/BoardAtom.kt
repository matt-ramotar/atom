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
    val lastEvent: String
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
    data object BurstSync : BoardIntent
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
    data object SaveRequested : BoardEvent

    @Serializable
    data class TaskSaved(val task: SampleTask) : BoardEvent

    @Serializable
    data object BurstRequested : BoardEvent
}

@Serializable
sealed interface BoardEffect : SideEffect {
    @Serializable
    data object LoadTasks : BoardEffect

    @Serializable
    data class SaveTask(val task: SampleTask) : BoardEffect

    @Serializable
    data class SyncChildren(val taskIds: List<String>) : BoardEffect

    @Serializable
    data class LogDiagnostics(val message: String) : BoardEffect
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
                lastEvent = "idle"
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
            BoardIntent.SaveSelected -> dispatch(BoardEvent.SaveRequested)
            BoardIntent.BurstSync -> dispatch(BoardEvent.BurstRequested)
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
                    BoardEffect.LoadTasks
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
                        BoardEffect.SyncChildren(event.tasks.map { it.id }),
                        BoardEffect.LogDiagnostics("tasks_loaded:${event.tasks.size}")
                    )
                )
            }

            is BoardEvent.TaskSelected -> Transition(
                to = state.copy(
                    selectedTaskId = event.taskId,
                    lastEvent = "task_selected"
                ),
                effects = listOf(BoardEffect.LogDiagnostics("task_selected:${event.taskId ?: "none"}"))
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
                    effects = listOf(BoardEffect.LogDiagnostics("filter_updated:${event.filter.name}"))
                )
            }

            BoardEvent.SaveRequested -> {
                val selected = state.tasks.firstOrNull { it.id == state.selectedTaskId }
                if (selected == null) {
                    Transition(
                        to = state.copy(lastEvent = "save_skipped"),
                        effects = listOf(BoardEffect.LogDiagnostics("save_skipped:no_selection"))
                    )
                } else {
                    Transition(
                        to = state.copy(lastEvent = "save_requested"),
                        effects = listOf(BoardEffect.SaveTask(selected))
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
                    effects = listOf(BoardEffect.LogDiagnostics("task_saved:${event.task.id}"))
                )
            }

            BoardEvent.BurstRequested -> Transition(
                to = state.copy(
                    syncGeneration = state.syncGeneration + 1,
                    lastEvent = "burst_requested"
                ),
                effects = listOf(
                    BoardEffect.SyncChildren(state.tasks.map { it.id }),
                    BoardEffect.LogDiagnostics("burst_sync:${state.syncGeneration + 1}")
                )
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

            is BoardEffect.SaveTask -> {
                val task = effect.task.copy(
                    notes = "Saved by BoardAtom",
                    completed = !effect.task.completed
                )
                repository.save(get().boardId, task)
                dispatch(BoardEvent.TaskSaved(task))
            }

            is BoardEffect.SyncChildren -> {
                val childParams: Map<Any, BoardTaskChildParams> = effect.taskIds.associate { id ->
                    id as Any to BoardTaskChildParams(taskId = id)
                }
                childProvider.sync(BoardTaskChildAtom::class, childParams)
                diagnostics.setActiveAtoms(
                    setOf("BoardAtom[${get().boardId}]") + effect.taskIds.map { id ->
                        "BoardTaskChildAtom[$id]"
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
