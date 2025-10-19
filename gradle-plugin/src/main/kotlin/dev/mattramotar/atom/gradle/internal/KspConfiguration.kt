package dev.mattramotar.atom.gradle.internal

import com.google.devtools.ksp.gradle.KspExtension
import dev.mattramotar.atom.gradle.DI
import dev.mattramotar.atom.gradle.AtomExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Internal utility for configuring KSP for Atom.
 */
internal object KspConfiguration {

    /**
     * Configures KSP for the project.
     */
    fun configure(project: Project, extension: AtomExtension) {
        configureKspArguments(project, extension)
        configureGeneratedSourceDirectories(project)
        configureCompilationDependencies(project)
    }

    /**
     * Configures KSP processor arguments.
     */
    private fun configureKspArguments(project: Project, extension: AtomExtension) {
        project.extensions.configure<KspExtension> {
            // Core Atom arguments
            arg("atom.di", extension.di.get().value)
            arg("atom.compose.extensions", extension.compose.get().toString())

            // Module identifier for per-module package namespacing
            // Converts ":feature:home:impl" → "feature.home.impl"
            val moduleId = project.path.removePrefix(":").replace(":", ".")
            arg("atom.module.id", moduleId)

            // Metro-specific arguments
            if (extension.di.get() == DI.METRO) {
                arg("atom.metro.scope", extension.scope.get())
                arg("atom.metro.injectAnnotation", extension.injectAnnotation.get())
                arg("atom.metro.origin", "true") // Enable @Origin annotation
            }
        }
    }

    /**
     * Configures generated source directories.
     *
     * Adds the KSP-generated Kotlin source directory to `commonMain` so that
     * generated Atom factories are available to all targets.
     */
    private fun configureGeneratedSourceDirectories(project: Project) {
        val kmpExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

        kmpExtension.sourceSets.named("commonMain").configure {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
    }

    /**
     * Configures compilation task dependencies.
     *
     * Ensures that all target compilation tasks depend on the KSP metadata task,
     * so that generated code is available before compilation.
     */
    private fun configureCompilationDependencies(project: Project) {
        val kmpExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

        kmpExtension.targets.configureEach {
            compilations.configureEach {
                compileTaskProvider.configure {
                    dependsOn("kspCommonMainKotlinMetadata")
                }
            }
        }
    }
}
