package dev.mattramotar.atom.sample

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleDiagnosticsTest {

    @Test
    fun snapshotReflectsRecordedEventsEffectsStateAndActiveAtoms() = runTest {
        val diagnostics = InMemorySampleDiagnostics(maxEntries = 4)

        diagnostics.setActiveAtoms(setOf("TaskAtom[task-1]", "BoardAtom[main]"))
        diagnostics.recordEvent(atom = "BoardAtom", value = "LoadRequested")
        diagnostics.recordEffect(atom = "BoardAtom", value = "LoadTasks")
        diagnostics.recordState(atom = "BoardAtom", value = "loaded=3")
        diagnostics.recordState(atom = "TaskAtom[task-1]", value = "completed=false")

        val snapshot = diagnostics.snapshot()

        assertEquals(
            listOf("BoardAtom[main]", "TaskAtom[task-1]"),
            snapshot.activeAtoms
        )
        assertEquals(
            listOf(SampleDiagnosticsRecord(atom = "BoardAtom", value = "LoadRequested")),
            snapshot.events
        )
        assertEquals(
            listOf(SampleDiagnosticsRecord(atom = "BoardAtom", value = "LoadTasks")),
            snapshot.effects
        )
        assertEquals(
            listOf(
                SampleDiagnosticsRecord(atom = "BoardAtom", value = "loaded=3"),
                SampleDiagnosticsRecord(atom = "TaskAtom[task-1]", value = "completed=false")
            ),
            snapshot.states
        )
    }

    @Test
    fun historyIsTrimmedToConfiguredLimit() = runTest {
        val diagnostics = InMemorySampleDiagnostics(maxEntries = 2)

        diagnostics.recordEvent("BoardAtom", "event-1")
        diagnostics.recordEvent("BoardAtom", "event-2")
        diagnostics.recordEvent("BoardAtom", "event-3")

        val snapshot = diagnostics.snapshot()

        assertEquals(
            listOf(
                SampleDiagnosticsRecord("BoardAtom", "event-2"),
                SampleDiagnosticsRecord("BoardAtom", "event-3")
            ),
            snapshot.events
        )
    }
}
