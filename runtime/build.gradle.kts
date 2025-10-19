plugins {
    id("plugin.atom.kotlin.android.library")
    id("plugin.atom.kotlin.multiplatform")
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
