package dev.mattramotar.atom.sample

import dev.mattramotar.atom.runtime.Atom
import dev.mattramotar.atom.runtime.annotations.AutoAtom
import dev.mattramotar.atom.runtime.annotations.InitialState
import dev.mattramotar.atom.runtime.fsm.Event
import dev.mattramotar.atom.runtime.fsm.Intent
import dev.mattramotar.atom.runtime.fsm.SideEffect
import dev.mattramotar.atom.runtime.fsm.Transition
import dev.mattramotar.atom.runtime.state.StateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class TaskState(
    val boardId: String,
    val task: SampleTask,
    val revision: Int,
    val lastEvent: String = "idle"
)

@Serializable
sealed interface TaskIntent : Intent {
    @Serializable
    data class EditTitle(val title: String) : TaskIntent

    @Serializable
    data object ToggleCompleted : TaskIntent
}

@Serializable
sealed interface TaskEvent : Event {
    @Serializable
    data class TitleEdited(val title: String) : TaskEvent

    @Serializable
    data object CompletionToggled : TaskEvent

    @Serializable
    data class TaskPersisted(val task: SampleTask) : TaskEvent
}

@Serializable
sealed interface TaskEffect : SideEffect {
    @Serializable
    data class PersistTask(val task: SampleTask) : TaskEffect

    @Serializable
    data class LogDiagnostics(val message: String) : TaskEffect
}

@AutoAtom
class TaskAtom(
    scope: CoroutineScope,
    handle: StateHandle<TaskState>,
    private val repository: SampleTaskRepository,
    private val diagnostics: SampleDiagnostics,
) : Atom<TaskState, TaskIntent, TaskEvent, TaskEffect>(scope, handle) {
    companion object {
        @InitialState
        fun initial(params: SampleTaskParams): TaskState {
            return TaskState(
                boardId = params.boardId,
                task = params.task,
                revision = params.revision
            )
        }
    }

    private var effectsJob: Job? = null
    private val mutationMutex = Mutex()
    private var pendingMutation: CompletableDeferred<SampleTask>? = null

    override fun onStart() {
        super.onStart()
        effectsJob?.cancel()
        effectsJob = scope.launch {
            effects.collect { effect ->
                handleEffect(effect)
            }
        }
    }

    override fun intent(intent: TaskIntent) {
        when (intent) {
            is TaskIntent.EditTitle -> dispatch(TaskEvent.TitleEdited(intent.title))
            TaskIntent.ToggleCompleted -> dispatch(TaskEvent.CompletionToggled)
        }
    }

    override fun reduce(state: TaskState, event: TaskEvent): Transition<TaskState, TaskEffect> {
        return when (event) {
            is TaskEvent.TitleEdited -> {
                val updated = state.task.copy(title = event.title)
                Transition(
                    to = state.copy(task = updated, lastEvent = "title_edited"),
                    effects = listOf(
                        TaskEffect.PersistTask(updated),
                        TaskEffect.LogDiagnostics("title_edited:${updated.id}")
                    )
                )
            }

            TaskEvent.CompletionToggled -> {
                val updated = state.task.copy(completed = !state.task.completed)
                Transition(
                    to = state.copy(task = updated, lastEvent = "completion_toggled"),
                    effects = listOf(
                        TaskEffect.PersistTask(updated),
                        TaskEffect.LogDiagnostics("completion_toggled:${updated.id}")
                    )
                )
            }

            is TaskEvent.TaskPersisted -> Transition(
                to = state.copy(task = event.task, lastEvent = "task_persisted")
            )
        }
    }

    override fun onStopInternal() {
        effectsJob?.cancel()
        effectsJob = null
        pendingMutation?.completeExceptionally(
            CancellationException("TaskAtom stopped before mutation completed.")
        )
        pendingMutation = null
    }

    suspend fun submit(intent: TaskIntent): SampleTask {
        val deferred = CompletableDeferred<SampleTask>()
        mutationMutex.withLock {
            pendingMutation?.completeExceptionally(
                CancellationException("Superseded by a newer task mutation.")
            )
            pendingMutation = deferred
            this.intent(intent)
        }
        return deferred.await()
    }

    private suspend fun handleEffect(effect: TaskEffect) {
        when (effect) {
            is TaskEffect.PersistTask -> {
                repository.save(boardId = get().boardId, task = effect.task)
                diagnostics.recordState(
                    atom = "TaskAtom[${effect.task.id}]",
                    value = "completed=${effect.task.completed}, title=${effect.task.title}"
                )
                dispatch(TaskEvent.TaskPersisted(effect.task))
                pendingMutation?.complete(effect.task)
                pendingMutation = null
            }

            is TaskEffect.LogDiagnostics -> {
                diagnostics.recordEvent(atom = "TaskAtom[${get().task.id}]", value = get().lastEvent)
                diagnostics.recordEffect(atom = "TaskAtom[${get().task.id}]", value = effect.message)
            }
        }
    }
}
