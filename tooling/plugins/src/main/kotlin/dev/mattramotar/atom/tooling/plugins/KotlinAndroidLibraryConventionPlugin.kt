package dev.mattramotar.atom.tooling.plugins

import com.android.build.gradle.LibraryExtension
import dev.mattramotar.atom.tooling.extensions.configureKotlin
import dev.mattramotar.atom.tooling.extensions.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class KotlinAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val versions = target.getVersions()

        with(target) {
            with(pluginManager) {
                apply("com.android.library")
            }

            configureKotlin()

            extensions.configure<LibraryExtension> {
                compileSdk = versions.compileSdk
                defaultConfig {
                    minSdk = versions.minSdk
                    targetSdk = versions.targetSdk
                    multiDexEnabled = true
                }
            }
        }
    }
}

fun Project.getVersions(): Versions {
    val minSdkVersion = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
    val targetSdkVersion = libs.findVersion("android-targetSdk").get().requiredVersion.toInt()
    val compileSdkVersion = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
    return Versions(
        minSdkVersion,
        targetSdkVersion,
        compileSdkVersion,
        JavaVersion.VERSION_17,
    )
}

data class Versions(
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int,
    val javaVersion: JavaVersion
)