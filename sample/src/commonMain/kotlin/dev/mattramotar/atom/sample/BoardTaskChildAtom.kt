package dev.mattramotar.atom.sample

import dev.mattramotar.atom.runtime.Atom
import dev.mattramotar.atom.runtime.annotations.AutoAtom
import dev.mattramotar.atom.runtime.annotations.InitialState
import dev.mattramotar.atom.runtime.fsm.Event
import dev.mattramotar.atom.runtime.fsm.Intent
import dev.mattramotar.atom.runtime.fsm.SideEffect
import dev.mattramotar.atom.runtime.state.StateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable

@Serializable
data class BoardTaskChildState(
    val taskId: String
)

@Serializable
data object BoardTaskChildIntent : Intent

@Serializable
data object BoardTaskChildEvent : Event

@Serializable
data object BoardTaskChildEffect : SideEffect

@Serializable
data class BoardTaskChildParams(
    val taskId: String
)

@AutoAtom
class BoardTaskChildAtom(
    scope: CoroutineScope,
    handle: StateHandle<BoardTaskChildState>
) : Atom<BoardTaskChildState, BoardTaskChildIntent, BoardTaskChildEvent, BoardTaskChildEffect>(scope, handle) {
    companion object {
        @InitialState
        fun initial(params: BoardTaskChildParams): BoardTaskChildState {
            return BoardTaskChildState(taskId = params.taskId)
        }
    }
}
