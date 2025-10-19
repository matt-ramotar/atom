package dev.mattramotar.atom.gradle.internal

import dev.mattramotar.atom.gradle.AtomExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Internal utility for configuring Kotlin compilation behavior.
 *
 * Handles optional build cache workarounds for Metro layered annotation processing.
 */
internal object KotlinConfiguration {

    /**
     * Configures Kotlin compilation settings based on the Atom extension.
     *
     * When `strictMode = true`, applies workarounds for Metro layered annotation processing:
     * - Disables Kotlin incremental compilation
     * - Forces KSP tasks to never be `UP-TO-DATE`
     *
     * These workarounds prevent stale code issues when KSP generates code with Metro annotations
     * that Metro's annotation processor then processes. Without these workarounds, Gradle's
     * incremental compilation and task caching can cause "LookupSymbols not converted to
     * ProgramSymbols" errors.
     *
     * **IMPORTANT**: Only enable strict mode if you encounter these cache-related build failures.
     *
     * @param project The Gradle project to configure
     * @param extension The Atom extension with user configuration
     */
    fun configure(project: Project, extension: AtomExtension) {
        if (!extension.strict.get()) {
            // Strict mode disabled - use normal incremental compilation for best performance
            return
        }

        project.logger.warn(
            "Atom: Strict mode enabled. Disabling incremental compilation and KSP task caching " +
            "to prevent stale code issues with Metro layered annotation processing. " +
            "This will impact build performance."
        )

        disableIncrementalCompilation(project)
        disableKspTaskCaching(project)
    }

    /**
     * Disables Kotlin incremental compilation.
     *
     * Incremental compilation can cause issues when KSP generates code with Metro annotations
     * because Kotlin's incremental compiler doesn't properly track this layered dependency.
     *
     * @param project The Gradle project.
     */
    private fun disableIncrementalCompilation(project: Project) {
        project.tasks.withType<KotlinCompile>().configureEach {
            incremental = false
        }
    }

    /**
     * Forces KSP tasks to never be `UP-TO-DATE`.
     *
     * When KSP generates code with Metro annotations, Metro's annotation processor processes
     * that code. If KSP marks itself `UP-TO-DATE`, Metro will process stale cached code,
     * causing "LookupSymbols not converted to ProgramSymbols" errors.
     *
     * @param project The Gradle project
     */
    private fun disableKspTaskCaching(project: Project) {
        project.tasks.matching { it.name.startsWith("ksp") }.configureEach {
            outputs.upToDateWhen { false }
        }
    }
}
