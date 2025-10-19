package dev.mattramotar.atom.tooling.extensions

import com.android.build.gradle.BaseExtension
import dev.mattramotar.atom.tooling.plugins.getVersions
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

fun Project.configureAndroid() {
    android {
        val versions = getVersions()

        compileSdkVersion(versions.compileSdk)

        defaultConfig {
            minSdk = versions.minSdk
            targetSdk = versions.targetSdk
            manifestPlaceholders["appAuthRedirectScheme"] = "empty"
        }

        compileOptions {
            isCoreLibraryDesugaringEnabled = true
        }

        packagingOptions {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }

    dependencies {
        "coreLibraryDesugaring"(libs.findLibrary("android.desugarJdkLibs").get())
    }
}

fun Project.android(action: BaseExtension.() -> Unit) = extensions.configure<BaseExtension>(action)

