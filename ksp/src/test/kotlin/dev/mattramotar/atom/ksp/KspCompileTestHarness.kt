@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.mattramotar.atom.ksp

import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal data class KspCompilationOutcome(
    val result: CompilationResult,
    val generatedFiles: Map<String, String>,
    val diagnostics: String,
) {
    fun generatedFileEndingWith(fileName: String): String? =
        generatedFiles.entries.firstOrNull { it.key.endsWith(fileName) }?.value
}

internal object KspCompileTestHarness {
    private val defaultArgs = mapOf(
        "atom.di" to "manual",
        "atom.module.id" to "default",
        "atom.compose.extensions" to "false",
    )

    fun compile(
        sources: List<SourceFile>,
        kspArgs: Map<String, String> = emptyMap(),
    ): KspCompilationOutcome {
        val messageStream = ByteArrayOutputStream()
        val compilation = KotlinCompilation().apply {
            this.sources = sources
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = messageStream
            configureKsp {
                symbolProcessorProviders.add(AtomProcessorProvider())
                processorOptions.putAll(defaultArgs + kspArgs)
            }
        }

        val result = compilation.compile()
        val generatedFiles = compilation.kspSourcesDir
            .takeIf { it.exists() }
            ?.walkTopDown()
            ?.filter { it.isFile && it.extension == "kt" }
            ?.associate { it.relativeTo(compilation.kspSourcesDir).path to it.readText() }
            ?: emptyMap()

        val diagnostics = buildString {
            append(result.messages)
            val captured = messageStream.toString(StandardCharsets.UTF_8.name())
            if (captured.isNotBlank()) {
                appendLine()
                append(captured)
            }
        }

        return KspCompilationOutcome(result, generatedFiles, diagnostics)
    }
}
