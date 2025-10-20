group = "dev.mattramotar.atom"
version = libs.versions.atom.get()

plugins {
    id("plugin.atom.kotlin.android.library")
    id("plugin.atom.kotlin.multiplatform")
    id("plugin.atom.maven.publish")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
                api(compose.runtime)
            }
        }
    }
}

android {
    namespace = "dev.mattramotar.atom.runtime"
}
