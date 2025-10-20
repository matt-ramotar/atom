group = "dev.mattramotar.atom"
version = libs.versions.atom.get()

plugins {
    id("plugin.atom.kotlin.android.library")
    id("plugin.atom.kotlin.multiplatform")
    id("plugin.atom.maven.publish")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.atom.runtime)
                api(libs.metro.runtime)
                api(compose.runtime)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}


android {
    namespace = "dev.mattramotar.atom.xplat.lib.atom.metro"
}

