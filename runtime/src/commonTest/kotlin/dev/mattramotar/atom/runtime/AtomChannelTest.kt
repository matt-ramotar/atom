package dev.mattramotar.atom.runtime

import dev.mattramotar.atom.runtime.fsm.Event
import dev.mattramotar.atom.runtime.fsm.Intent
import dev.mattramotar.atom.runtime.fsm.SideEffect
import dev.mattramotar.atom.runtime.fsm.Transition
import dev.mattramotar.atom.runtime.state.InMemoryStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AtomChannelTest {

    @Test
    fun effectsFlowCompletesOnStop() = runTest {
        val atom = TestAtom(
            scope = CoroutineScope(coroutineContext),
            handle = InMemoryStateHandle(TestState())
        )
        atom.onStart()

        val job = launch {
            atom.effects.collect { }
        }

        atom.onStop()
        runCurrent()

        assertTrue(job.isCompleted)
        job.cancel()
    }

    @Test
    fun dispatchIsIgnoredAfterStop() = runTest {
        val atom = TestAtom(
            scope = CoroutineScope(coroutineContext),
            handle = InMemoryStateHandle(TestState())
        )
        atom.onStart()

        atom.emit(listOf(TestEffect(1)))
        runCurrent()
        assertEquals(1, atom.get().count)

        atom.onStop()
        atom.emit(listOf(TestEffect(2)))
        runCurrent()

        assertEquals(1, atom.get().count)
    }

    @Test
    fun dispatchDoesNotLeakToRestartedChannel() = runTest {
        val config = AtomChannelConfig(
            events = AtomChannelConfig.ChannelConfig(
                capacity = Channel.RENDEZVOUS,
                onBufferOverflow = BufferOverflow.SUSPEND
            )
        )
        val atom = TestAtom(
            scope = CoroutineScope(coroutineContext),
            handle = InMemoryStateHandle(TestState()),
            channelConfig = config
        )
        atom.onStart()

        atom.emit(listOf(TestEffect(1)))
        atom.onStop()
        atom.onStart()
        runCurrent()

        assertEquals(0, atom.get().count)
        atom.onStop()
        runCurrent()
    }

    @Test
    fun effectsOverflowDropsOldestWhenConfigured() = runTest {
        val config = AtomChannelConfig(
            effects = AtomChannelConfig.ChannelConfig(
                capacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        )
        val atom = TestAtom(
            scope = CoroutineScope(coroutineContext),
            handle = InMemoryStateHandle(TestState()),
            channelConfig = config
        )
        atom.onStart()

        atom.emit(
            listOf(
                TestEffect(1),
                TestEffect(2),
                TestEffect(3)
            )
        )
        runCurrent()

        val collected = mutableListOf<TestEffect>()
        val job = launch {
            atom.effects.take(1).collect { collected += it }
        }
        runCurrent()

        assertEquals(listOf(TestEffect(3)), collected)
        atom.onStop()
        runCurrent()
        job.cancel()
    }

    @Test
    fun eventOverflowDropsOldestWhenConfigured() = runTest {
        val config = AtomChannelConfig(
            events = AtomChannelConfig.ChannelConfig(
                capacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        )
        val atom = EventOverflowAtom(
            scope = CoroutineScope(coroutineContext),
            handle = InMemoryStateHandle(EventOverflowState()),
            channelConfig = config
        )
        atom.onStart()

        atom.emitId(1)
        atom.emitId(2)
        atom.emitId(3)
        runCurrent()

        val state = atom.get()
        assertEquals(1, state.processed)
        assertEquals(3, state.lastId)
        atom.onStop()
        runCurrent()
    }

    @Test
    fun atomCanRestartAfterStop() = runTest {
        val atom = TestAtom(
            scope = CoroutineScope(coroutineContext),
            handle = InMemoryStateHandle(TestState())
        )
        atom.onStart()
        atom.emit(listOf(TestEffect(1)))
        runCurrent()
        assertEquals(1, atom.get().count)

        atom.onStop()
        runCurrent()
        atom.onStart()
        atom.emit(listOf(TestEffect(2)))
        runCurrent()

        assertEquals(2, atom.get().count)
        atom.onStop()
        runCurrent()
    }

    @Test
    fun bufferedChannelAllowsDropOverflow() = runTest {
        val config = AtomChannelConfig(
            events = AtomChannelConfig.ChannelConfig(
                capacity = Channel.BUFFERED,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        )

        TestAtom(
            scope = CoroutineScope(coroutineContext),
            handle = InMemoryStateHandle(TestState()),
            channelConfig = config
        )
    }

    @Test
    fun invalidEventChannelConfigThrows() = runTest {
        val config = AtomChannelConfig(
            events = AtomChannelConfig.ChannelConfig(
                capacity = Channel.RENDEZVOUS,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        )

        assertFailsWith<IllegalArgumentException> {
            TestAtom(
                scope = CoroutineScope(coroutineContext),
                handle = InMemoryStateHandle(TestState()),
                channelConfig = config
            )
        }
    }

    @Test
    fun invalidEffectChannelConfigThrows() = runTest {
        val config = AtomChannelConfig(
            effects = AtomChannelConfig.ChannelConfig(
                capacity = Channel.RENDEZVOUS,
                onBufferOverflow = BufferOverflow.DROP_LATEST
            )
        )

        assertFailsWith<IllegalArgumentException> {
            TestAtom(
                scope = CoroutineScope(coroutineContext),
                handle = InMemoryStateHandle(TestState()),
                channelConfig = config
            )
        }
    }

    @Test
    fun unlimitedEffectChannelIsRejected() = runTest {
        val config = AtomChannelConfig(
            effects = AtomChannelConfig.ChannelConfig(
                capacity = Channel.UNLIMITED,
                onBufferOverflow = BufferOverflow.SUSPEND
            )
        )

        assertFailsWith<IllegalArgumentException> {
            TestAtom(
                scope = CoroutineScope(coroutineContext),
                handle = InMemoryStateHandle(TestState()),
                channelConfig = config
            )
        }
    }

    private object TestIntent : Intent

    private data class TestState(val count: Int = 0)

    private data class TestEvent(val effects: List<TestEffect>) : Event

    private data class TestEffect(val id: Int) : SideEffect

    private data class EventOverflowState(
        val lastId: Int = 0,
        val processed: Int = 0
    )

    private data class EventOverflowEvent(val id: Int) : Event

    private class TestAtom(
        scope: CoroutineScope,
        handle: InMemoryStateHandle<TestState>,
        channelConfig: AtomChannelConfig = AtomChannelConfig(),
    ) : Atom<TestState, TestIntent, TestEvent, TestEffect>(scope, handle, channelConfig) {
        fun emit(effects: List<TestEffect>) {
            dispatch(TestEvent(effects))
        }

        override fun reduce(
            state: TestState,
            event: TestEvent
        ): Transition<TestState, TestEffect> {
            return Transition(
                to = state.copy(count = state.count + 1),
                effects = event.effects
            )
        }
    }

    private class EventOverflowAtom(
        scope: CoroutineScope,
        handle: InMemoryStateHandle<EventOverflowState>,
        channelConfig: AtomChannelConfig,
    ) : Atom<EventOverflowState, TestIntent, EventOverflowEvent, TestEffect>(scope, handle, channelConfig) {
        fun emitId(id: Int) {
            dispatch(EventOverflowEvent(id))
        }

        override fun reduce(
            state: EventOverflowState,
            event: EventOverflowEvent
        ): Transition<EventOverflowState, TestEffect> {
            return Transition(
                to = state.copy(
                    lastId = event.id,
                    processed = state.processed + 1
                )
            )
        }
    }
}
