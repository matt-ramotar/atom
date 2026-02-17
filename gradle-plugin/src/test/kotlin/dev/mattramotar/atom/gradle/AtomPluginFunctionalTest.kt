package dev.mattramotar.atom.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomPluginFunctionalTest {

    @get:Rule
    val testProjectDir = TemporaryFolder()

    @Test
    fun `defaults to manual DI`() {
        writeGradleProject(
            buildFile = """
                plugins {
                    id 'dev.mattramotar.atom'
                }

                kotlin {
                    jvm()
                }

                tasks.register('printAtomDefaults') {
                    doLast {
                        def ext = project.extensions.getByName('atom')
                        println("atom.di=${'$'}{ext.di.get()}")
                        println("atom.compose=${'$'}{ext.compose.get()}")
                    }
                }
            """.trimIndent()
        )

        val result = runner().withArguments("printAtomDefaults", "-q").build()

        assertTrue(result.output, result.output.contains("atom.di=MANUAL"))
        assertTrue(result.output, result.output.contains("atom.compose=true"))
    }

    @Test
    fun `allows overriding DI to metro`() {
        writeGradleProject(
            buildFile = """
                plugins {
                    id 'dev.mattramotar.atom'
                }

                kotlin {
                    jvm()
                }

                atom {
                    di = dev.mattramotar.atom.gradle.DI.METRO
                }

                tasks.register('printAtomDefaults') {
                    doLast {
                        def ext = project.extensions.getByName('atom')
                        println("atom.di=${'$'}{ext.di.get()}")
                    }
                }
            """.trimIndent()
        )

        val result = runner().withArguments("printAtomDefaults", "-q").build()

        assertTrue(result.output, result.output.contains("atom.di=METRO"))
    }

    private fun runner(): GradleRunner = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withPluginClasspath()
        .forwardOutput()

    private fun writeGradleProject(buildFile: String) {
        writeFile(
            "settings.gradle",
            """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                        maven { url "https://maven.pkg.jetbrains.space/public/p/compose/dev" }
                        maven { url "https://oss.sonatype.org/content/repositories/snapshots/" }
                    }
                }

                dependencyResolutionManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                        maven { url "https://maven.pkg.jetbrains.space/public/p/compose/dev" }
                        maven { url "https://oss.sonatype.org/content/repositories/snapshots/" }
                    }
                }

                rootProject.name = "atom-test-project"
            """.trimIndent()
        )

        writeFile(
            "gradle/libs.versions.toml",
            """
                [versions]
                atom = "1.0.0-test"
            """.trimIndent()
        )

        writeFile(
            "build.gradle",
            """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                    }
                    dependencies {
                        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20"
                        classpath "com.google.devtools.ksp:symbol-processing-gradle-plugin:2.2.20-2.0.3"
                    }
                }

                ${buildFile.trimIndent()}
            """.trimIndent()
        )
    }

    private fun writeFile(path: String, content: String) {
        val file = File(testProjectDir.root, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
