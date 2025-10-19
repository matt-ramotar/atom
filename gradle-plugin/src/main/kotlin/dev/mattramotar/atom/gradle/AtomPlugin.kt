package dev.mattramotar.atom.gradle

import dev.mattramotar.atom.gradle.internal.AtomDependencies
import dev.mattramotar.atom.gradle.internal.KotlinConfiguration
import dev.mattramotar.atom.gradle.internal.KspConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

/**
 * Gradle plugin for configuring Atom.
 *
 * @see AtomExtension for all configuration options.
 */
class AtomPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        applyPlugins(project)
        val extension = project.extensions.create<AtomExtension>("atom")
        project.afterEvaluate {
            validateConfiguration(extension)
            configureAtom(project, extension)
        }
    }

    /**
     * Applies required Gradle plugins.
     */
    private fun applyPlugins(project: Project) {
        with(project.pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.google.devtools.ksp")
        }
    }

    /**
     * Validates the Atom configuration.
     *
     * @throws IllegalArgumentException if the configuration is invalid.
     */
    private fun validateConfiguration(extension: AtomExtension) {
        if (extension.version.isPresent) {
            val version = extension.version.get()
            if (version.isBlank()) {
                throw IllegalArgumentException("Atom version cannot be blank!")
            }
        }
    }

    /**
     * Warns if Metro plugin is not applied when using Metro DI.
     */
    private fun checkMetroPlugin(project: Project, extension: AtomExtension) {
        if (extension.di.get() == DI.METRO) {
            val hasMetroPlugin = project.pluginManager.hasPlugin("dev.zacsweers.metro")
            if (!hasMetroPlugin) {
                project.logger.warn(
                    """
                    |
                    |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    |⚠️  Atom: Metro DI is enabled but Metro plugin is not applied!
                    |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    |
                    |Atom will generate Metro annotations, but Metro won't process them.
                    |
                    |To fix this, add the Metro plugin to your build.gradle.kts:
                    |
                    |    plugins {
                    |        id("dev.mattramotar.atom")
                    |        id("dev.zacsweers.metro")
                    |    }
                    |
                    |Or change Atom to use manual DI:
                    |
                    |    atom {
                    |        di = DI.MANUAL
                    |    }
                    |
                    |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    |
                    """.trimMargin()
                )
            }
        }
    }

    /**
     * Warns if the Compose compiler plugin is not applied when Compose extensions are enabled.
     */
    private fun checkComposeCompilerPlugin(project: Project, extension: AtomExtension) {
        if (extension.compose.get()) {
            val hasKotlinComposePlugin = project.pluginManager.hasPlugin("org.jetbrains.kotlin.plugin.compose")
            val hasComposeMultiplatformPlugin = project.pluginManager.hasPlugin("org.jetbrains.compose")
            val hasComposeCompiler = hasKotlinComposePlugin || hasComposeMultiplatformPlugin

            if (!hasComposeCompiler) {
                project.logger.warn(
                    """
                    |
                    |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    |⚠️  Atom: Compose extensions enabled but Compose compiler plugin is not applied!
                    |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    |
                    |Atom will generate Compose helper functions (remember{Atom}()), but they will
                    |fail to compile because @Composable functions require the Compose compiler.
                    |
                    |To fix this, add the Compose compiler plugin to your build.gradle.kts:
                    |
                    |    plugins {
                    |        id("dev.mattramotar.atom")
                    |        kotlin("plugin.compose")
                    |    }
                    |
                    |Or if using Compose Multiplatform:
                    |
                    |    plugins {
                    |        id("dev.mattramotar.atom")
                    |        kotlin("plugin.compose")
                    |        id("org.jetbrains.compose")
                    |    }
                    |
                    |Or disable Compose extensions in Atom:
                    |
                    |    atom {
                    |        compose = false
                    |    }
                    |
                    |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    |
                    """.trimMargin()
                )
            }
        }
    }

    /**
     * Configures Atom for the project.
     */
    private fun configureAtom(project: Project, extension: AtomExtension) {
        project.logger.lifecycle("Atom: Configuring with DI framework '${extension.di.get()}'")
        AtomDependencies.configure(project, extension)
        KspConfiguration.configure(project, extension)
        KotlinConfiguration.configure(project, extension)
        checkMetroPlugin(project, extension)
        checkComposeCompilerPlugin(project, extension)
        project.logger.lifecycle("Atom: Configuration complete")
    }
}
