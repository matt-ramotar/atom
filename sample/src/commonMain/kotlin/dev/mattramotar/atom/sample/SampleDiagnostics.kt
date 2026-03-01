package dev.mattramotar.atom.sample

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class SampleDiagnosticsRecord(
    val atom: String,
    val value: String
)

@Serializable
data class SampleDiagnosticsSnapshot(
    val activeAtoms: List<String>,
    val events: List<SampleDiagnosticsRecord>,
    val effects: List<SampleDiagnosticsRecord>,
    val states: List<SampleDiagnosticsRecord>
)

fun emptySampleDiagnosticsSnapshot(): SampleDiagnosticsSnapshot = SampleDiagnosticsSnapshot(
    activeAtoms = emptyList(),
    events = emptyList(),
    effects = emptyList(),
    states = emptyList()
)

interface SampleDiagnostics {
    suspend fun setActiveAtoms(atoms: Set<String>)

    suspend fun recordEvent(atom: String, value: String)

    suspend fun recordEffect(atom: String, value: String)

    suspend fun recordState(atom: String, value: String)

    suspend fun snapshot(): SampleDiagnosticsSnapshot

    suspend fun clear()
}

class InMemorySampleDiagnostics(
    private val maxEntries: Int = 25
) : SampleDiagnostics {
    init {
        require(maxEntries > 0) { "maxEntries must be positive." }
    }

    private val mutex = Mutex()
    private val activeAtoms = linkedSetOf<String>()
    private val eventHistory = mutableListOf<SampleDiagnosticsRecord>()
    private val effectHistory = mutableListOf<SampleDiagnosticsRecord>()
    private val latestStates = linkedMapOf<String, String>()

    override suspend fun setActiveAtoms(atoms: Set<String>) = mutex.withLock {
        activeAtoms.clear()
        activeAtoms.addAll(atoms.sorted())
        Unit
    }

    override suspend fun recordEvent(atom: String, value: String) = mutex.withLock {
        append(eventHistory, SampleDiagnosticsRecord(atom = atom, value = value))
    }

    override suspend fun recordEffect(atom: String, value: String) = mutex.withLock {
        append(effectHistory, SampleDiagnosticsRecord(atom = atom, value = value))
    }

    override suspend fun recordState(atom: String, value: String) = mutex.withLock {
        latestStates[atom] = value
    }

    override suspend fun snapshot(): SampleDiagnosticsSnapshot = mutex.withLock {
        SampleDiagnosticsSnapshot(
            activeAtoms = activeAtoms.toList(),
            events = eventHistory.toList(),
            effects = effectHistory.toList(),
            states = latestStates.entries
                .map { (atom, value) -> SampleDiagnosticsRecord(atom = atom, value = value) }
        )
    }

    override suspend fun clear() = mutex.withLock {
        activeAtoms.clear()
        eventHistory.clear()
        effectHistory.clear()
        latestStates.clear()
    }

    private fun append(
        target: MutableList<SampleDiagnosticsRecord>,
        record: SampleDiagnosticsRecord
    ) {
        target += record
        while (target.size > maxEntries) {
            target.removeAt(0)
        }
    }
}
