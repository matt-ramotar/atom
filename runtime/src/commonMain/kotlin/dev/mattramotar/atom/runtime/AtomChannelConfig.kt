package dev.mattramotar.atom.runtime

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/**
 * Configuration for Atom event and effect channels.
 *
 * @property events Channel configuration for events dispatched to the reducer.
 * @property effects Channel configuration for side effects emitted by the reducer.
 *
 * Note: Atom requires effects to use a bounded capacity; [Channel.UNLIMITED] will throw.
 */
data class AtomChannelConfig(
    val events: ChannelConfig = ChannelConfig(),
    val effects: ChannelConfig = ChannelConfig(),
) {
    /**
     * Channel capacity and overflow behavior.
     *
     * Note: drop policies require a positive buffer capacity or [Channel.BUFFERED]. Rendezvous
     * channels only support [BufferOverflow.SUSPEND].
     */
    data class ChannelConfig(
        val capacity: Int = Channel.BUFFERED,
        val onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    )
}
