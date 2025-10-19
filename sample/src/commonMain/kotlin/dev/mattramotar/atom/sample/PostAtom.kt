package dev.mattramotar.atom.sample

import dev.mattramotar.atom.runtime.Atom
import dev.mattramotar.atom.runtime.annotations.AutoAtom
import dev.mattramotar.atom.runtime.annotations.InitialState
import dev.mattramotar.atom.runtime.fsm.Event
import dev.mattramotar.atom.runtime.fsm.Intent
import dev.mattramotar.atom.runtime.fsm.SideEffect
import dev.mattramotar.atom.runtime.fsm.Transition
import dev.mattramotar.atom.runtime.state.StateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable

@Serializable
data class PostState(
    val id: String
)

@Serializable
data object PostIntent : Intent

@Serializable
data object PostEvent : Event

@Serializable
data object PostEffect : SideEffect

@Serializable
data class PostParams(
    val id: String
)

@AutoAtom
class PostAtom(
    scope: CoroutineScope,
    handle: StateHandle<PostState>
) : Atom<PostState, PostIntent, PostEvent, PostEffect>(scope, handle) {
    companion object {
        @InitialState
        fun initial(params: PostParams): PostState = PostState(params.id)
    }

    override fun reduce(state: PostState, event: PostEvent): Transition<PostState, PostEffect> {
        return super.reduce(state, event)
    }
}

