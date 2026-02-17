@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.mattramotar.atom.ksp

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtomProcessorCompileTest {

    @Test
    fun `manual DI generation emits factory entry and registry`() {
        val outcome = KspCompileTestHarness.compile(
            sources = listOf(
                SourceFile.kotlin(
                    "ManualAtom.kt",
                    """
                    package fixture.manual

                    import dev.mattramotar.atom.runtime.Atom
                    import dev.mattramotar.atom.runtime.annotations.AutoAtom
                    import dev.mattramotar.atom.runtime.annotations.InitialState
                    import dev.mattramotar.atom.runtime.fsm.Event
                    import dev.mattramotar.atom.runtime.fsm.Intent
                    import dev.mattramotar.atom.runtime.fsm.SideEffect
                    import dev.mattramotar.atom.runtime.state.StateHandle
                    import kotlinx.coroutines.CoroutineScope

                    object ManualIntent : Intent
                    object ManualEvent : Event
                    object ManualEffect : SideEffect
                    data class ManualState(val value: String)

                    @AutoAtom
                    class ManualAtom(
                        scope: CoroutineScope,
                        handle: StateHandle<ManualState>
                    ) : Atom<ManualState, ManualIntent, ManualEvent, ManualEffect>(scope, handle) {
                        companion object {
                            @InitialState
                            fun initial(): ManualState = ManualState("seed")
                        }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(ExitCode.OK, outcome.result.exitCode, outcome.diagnostics)
        assertNotNull(outcome.generatedFileEndingWith("ManualAtom_Factory.kt"), outcome.diagnostics)
        assertNotNull(outcome.generatedFileEndingWith("ManualAtom_Entry.kt"), outcome.diagnostics)
        assertNotNull(outcome.generatedFileEndingWith("GeneratedAtomRegistry.kt"), outcome.diagnostics)
    }

    @Test
    fun `metro DI generation emits metro entry and scope bindings`() {
        val outcome = KspCompileTestHarness.compile(
            sources = listOf(
                SourceFile.kotlin(
                    "MetroAtom.kt",
                    """
                    package fixture.metro

                    import dev.mattramotar.atom.runtime.Atom
                    import dev.mattramotar.atom.runtime.annotations.AutoAtom
                    import dev.mattramotar.atom.runtime.annotations.InitialState
                    import dev.mattramotar.atom.runtime.fsm.Event
                    import dev.mattramotar.atom.runtime.fsm.Intent
                    import dev.mattramotar.atom.runtime.fsm.SideEffect
                    import dev.mattramotar.atom.runtime.state.StateHandle
                    import kotlinx.coroutines.CoroutineScope

                    @Target(AnnotationTarget.CLASS)
                    annotation class TestScope

                    @Target(AnnotationTarget.CONSTRUCTOR)
                    annotation class TestInject

                    object MetroIntent : Intent
                    object MetroEvent : Event
                    object MetroEffect : SideEffect
                    data class MetroState(val value: String)

                    @AutoAtom(scope = TestScope::class)
                    class MetroAtom(
                        scope: CoroutineScope,
                        handle: StateHandle<MetroState>
                    ) : Atom<MetroState, MetroIntent, MetroEvent, MetroEffect>(scope, handle) {
                        companion object {
                            @InitialState
                            fun initial(): MetroState = MetroState("seed")
                        }
                    }
                    """.trimIndent(),
                ),
            ),
            kspArgs = mapOf(
                "atom.di" to "metro",
                "atom.metro.scope" to "fixture.metro.TestScope",
                "atom.metro.injectAnnotation" to "fixture.metro.TestInject",
                "atom.metro.origin" to "true",
            ),
        )

        assertEquals(ExitCode.OK, outcome.result.exitCode, outcome.diagnostics)
        assertNotNull(outcome.generatedFileEndingWith("MetroAtom_MetroEntry.kt"), outcome.diagnostics)
        assertNotNull(
            outcome.generatedFileEndingWith("AtomMetroGeneratedBindings_TestScope.kt"),
            outcome.diagnostics,
        )
        assertNull(outcome.generatedFileEndingWith("GeneratedAtomRegistry.kt"), outcome.diagnostics)
    }

    @Test
    fun `qualified default parameter still resolves through DI`() {
        val outcome = KspCompileTestHarness.compile(
            sources = listOf(
                SourceFile.kotlin(
                    "QualifiedAtom.kt",
                    """
                    package fixture.qualifier

                    import dev.mattramotar.atom.runtime.Atom
                    import dev.mattramotar.atom.runtime.annotations.AtomQualifier
                    import dev.mattramotar.atom.runtime.annotations.AutoAtom
                    import dev.mattramotar.atom.runtime.annotations.InitialState
                    import dev.mattramotar.atom.runtime.fsm.Event
                    import dev.mattramotar.atom.runtime.fsm.Intent
                    import dev.mattramotar.atom.runtime.fsm.SideEffect
                    import dev.mattramotar.atom.runtime.state.StateHandle
                    import kotlinx.coroutines.CoroutineScope

                    interface NamedDependency
                    object DefaultDependency : NamedDependency

                    object QualifiedIntent : Intent
                    object QualifiedEvent : Event
                    object QualifiedEffect : SideEffect
                    data class QualifiedState(val value: String)

                    @AutoAtom
                    class QualifiedAtom(
                        scope: CoroutineScope,
                        handle: StateHandle<QualifiedState>,
                        @AtomQualifier("analytics") named: NamedDependency = DefaultDependency
                    ) : Atom<QualifiedState, QualifiedIntent, QualifiedEvent, QualifiedEffect>(scope, handle) {
                        companion object {
                            @InitialState
                            fun initial(): QualifiedState = QualifiedState("seed")
                        }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(ExitCode.OK, outcome.result.exitCode, outcome.diagnostics)
        val factory = assertNotNull(
            outcome.generatedFileEndingWith("QualifiedAtom_Factory.kt"),
            outcome.diagnostics,
        )
        assertTrue(factory.contains("resolve<fixture.qualifier.NamedDependency>"), factory)
        assertTrue(factory.contains("\"analytics\""), factory)
        assertTrue(factory.contains("AtomContainer"), factory)
    }

    @Test
    fun `serializer generation supports explicit serializer and nullable fallback`() {
        val outcome = KspCompileTestHarness.compile(
            sources = listOf(
                SourceFile.kotlin(
                    "SerializerAtoms.kt",
                    """
                    package fixture.serializer

                    import dev.mattramotar.atom.runtime.Atom
                    import dev.mattramotar.atom.runtime.annotations.AutoAtom
                    import dev.mattramotar.atom.runtime.annotations.InitialState
                    import dev.mattramotar.atom.runtime.fsm.Event
                    import dev.mattramotar.atom.runtime.fsm.Intent
                    import dev.mattramotar.atom.runtime.fsm.SideEffect
                    import dev.mattramotar.atom.runtime.serialization.StateSerializer
                    import dev.mattramotar.atom.runtime.state.StateHandle
                    import kotlinx.coroutines.CoroutineScope

                    object SerializerIntent : Intent
                    object SerializerEvent : Event
                    object SerializerEffect : SideEffect

                    data class ExplicitState(val value: String)
                    data class NullableState(val value: String)

                    class ExplicitStateSerializer : StateSerializer<ExplicitState> {
                        override fun serialize(value: ExplicitState): String = value.value
                        override fun deserialize(text: String): ExplicitState = ExplicitState(text)
                    }

                    @AutoAtom(serializer = ExplicitStateSerializer::class)
                    class ExplicitSerializerAtom(
                        scope: CoroutineScope,
                        handle: StateHandle<ExplicitState>
                    ) : Atom<ExplicitState, SerializerIntent, SerializerEvent, SerializerEffect>(scope, handle) {
                        companion object {
                            @InitialState
                            fun initial(): ExplicitState = ExplicitState("seed")
                        }
                    }

                    @AutoAtom
                    class NullableSerializerAtom(
                        scope: CoroutineScope,
                        handle: StateHandle<NullableState>
                    ) : Atom<NullableState, SerializerIntent, SerializerEvent, SerializerEffect>(scope, handle) {
                        companion object {
                            @InitialState
                            fun initial(): NullableState = NullableState("seed")
                        }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(ExitCode.OK, outcome.result.exitCode, outcome.diagnostics)
        val explicitFactory = assertNotNull(
            outcome.generatedFileEndingWith("ExplicitSerializerAtom_Factory.kt"),
            outcome.diagnostics,
        )
        val nullableFactory = assertNotNull(
            outcome.generatedFileEndingWith("NullableSerializerAtom_Factory.kt"),
            outcome.diagnostics,
        )

        assertTrue(explicitFactory.contains("ExplicitStateSerializer()"), explicitFactory)
        assertTrue(nullableFactory.contains("= null"), nullableFactory)
    }

    @Test
    fun `state mismatch reports an explicit compile error`() {
        val outcome = KspCompileTestHarness.compile(
            sources = listOf(
                SourceFile.kotlin(
                    "MismatchAtom.kt",
                    """
                    package fixture.mismatch

                    import dev.mattramotar.atom.runtime.Atom
                    import dev.mattramotar.atom.runtime.annotations.AutoAtom
                    import dev.mattramotar.atom.runtime.annotations.InitialState
                    import dev.mattramotar.atom.runtime.fsm.Event
                    import dev.mattramotar.atom.runtime.fsm.Intent
                    import dev.mattramotar.atom.runtime.fsm.SideEffect
                    import dev.mattramotar.atom.runtime.state.StateHandle
                    import kotlinx.coroutines.CoroutineScope

                    object MismatchIntent : Intent
                    object MismatchEvent : Event
                    object MismatchEffect : SideEffect
                    data class DeclaredState(val value: String)
                    data class DifferentState(val value: String)

                    @AutoAtom(state = DifferentState::class)
                    class MismatchAtom(
                        scope: CoroutineScope,
                        handle: StateHandle<DeclaredState>
                    ) : Atom<DeclaredState, MismatchIntent, MismatchEvent, MismatchEffect>(scope, handle) {
                        companion object {
                            @InitialState
                            fun initial(): DeclaredState = DeclaredState("seed")
                        }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(ExitCode.COMPILATION_ERROR, outcome.result.exitCode, outcome.diagnostics)
        assertTrue(outcome.diagnostics.contains("State type mismatch"), outcome.diagnostics)
    }

    @Test
    fun `non public atom reports validation error`() {
        val outcome = KspCompileTestHarness.compile(
            sources = listOf(
                SourceFile.kotlin(
                    "InternalAtom.kt",
                    """
                    package fixture.visibility

                    import dev.mattramotar.atom.runtime.Atom
                    import dev.mattramotar.atom.runtime.annotations.AutoAtom
                    import dev.mattramotar.atom.runtime.annotations.InitialState
                    import dev.mattramotar.atom.runtime.fsm.Event
                    import dev.mattramotar.atom.runtime.fsm.Intent
                    import dev.mattramotar.atom.runtime.fsm.SideEffect
                    import dev.mattramotar.atom.runtime.state.StateHandle
                    import kotlinx.coroutines.CoroutineScope

                    object HiddenIntent : Intent
                    object HiddenEvent : Event
                    object HiddenEffect : SideEffect
                    data class HiddenState(val value: String)

                    @AutoAtom
                    internal class InternalAtom(
                        scope: CoroutineScope,
                        handle: StateHandle<HiddenState>
                    ) : Atom<HiddenState, HiddenIntent, HiddenEvent, HiddenEffect>(scope, handle) {
                        companion object {
                            @InitialState
                            fun initial(): HiddenState = HiddenState("seed")
                        }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(ExitCode.COMPILATION_ERROR, outcome.result.exitCode, outcome.diagnostics)
        assertTrue(outcome.diagnostics.contains("@AutoAtom class must be public"), outcome.diagnostics)
    }

    @Test
    fun `non atom class reports validation error`() {
        val outcome = KspCompileTestHarness.compile(
            sources = listOf(
                SourceFile.kotlin(
                    "NotAnAtom.kt",
                    """
                    package fixture.invalid

                    import dev.mattramotar.atom.runtime.annotations.AutoAtom

                    @AutoAtom
                    class NotAnAtom
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(ExitCode.COMPILATION_ERROR, outcome.result.exitCode, outcome.diagnostics)
        assertTrue(
            outcome.diagnostics.contains("@AutoAtom must annotate a subclass of Atom"),
            outcome.diagnostics,
        )
    }
}
