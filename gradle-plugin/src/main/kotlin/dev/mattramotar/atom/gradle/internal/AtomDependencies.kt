package dev.mattramotar.atom.gradle.internal

import dev.mattramotar.atom.gradle.AtomExtension
import dev.mattramotar.atom.gradle.DI
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Internal utility for resolving and adding Atom dependencies to the project.
 */
internal object AtomDependencies {

    /** Maven group ID for all Atom artifacts. */
    private const val GROUP = "dev.mattramotar.atom"

    /**
     * Maven coordinate for Atom runtime.
     */
    private fun runtimeCoordinate(version: String) = "$GROUP:runtime:$version"

    /**
     * Maven coordinate for Atom KSP processor.
     */
    private fun kspCoordinate(version: String) = "$GROUP:ksp:$version"

    /**
     * Maven coordinate for Atom Metro integration.
     */
    private fun metroCoordinate(version: String) = "$GROUP:metro:$version"

    /**
     * Configures Atom dependencies for the project.
     * @param project The Gradle project to configure.
     * @param extension The Atom extension with user configuration.
     */
    fun configure(project: Project, extension: AtomExtension) {
        val defaultVersion = project.libs.findVersion("atom").get().requiredVersion

        val version = extension.version.getOrElse(defaultVersion)

        val kmpExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

        project.dependencies {
            add("commonMainApi", runtimeCoordinate(version))

            when (extension.di.get()) {
                DI.METRO -> {
                    add("commonMainApi", metroCoordinate(version))
                }

                DI.MANUAL -> {
                    // No additional dependency
                }
            }
        }

        // Add KSP processor for all KMP targets
        addKspDependencyForAllTargets(project, kspCoordinate(version))
    }

    /**
     * Adds a KSP dependency for all Kotlin Multiplatform targets.
     *
     * This is necessary because KSP requires target-specific configurations (e.g., kspAndroid, kspJvm).
     *
     * @param project The Gradle project.
     * @param dependencyNotation The dependency to add.
     */
    private fun addKspDependencyForAllTargets(
        project: Project,
        dependencyNotation: String
    ) {
        project.dependencies {
            add("kspCommonMainMetadata", dependencyNotation)
        }
    }
}


private val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")