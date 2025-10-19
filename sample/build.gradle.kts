import com.google.devtools.ksp.gradle.KspExtension

plugins {
    id("plugin.atom.kotlin.android.library")
    id("plugin.atom.kotlin.multiplatform")
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.runtime)
                implementation(projects.metro)
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.serialization.json)
            }
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
    }

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                dependsOn("kspCommonMainKotlinMetadata")
            }
        }
    }
}

android {
    namespace = "dev.mattramotar.atom.sample"
}

configure<KspExtension> {
    arg("atom.di", "metro")
    arg("atom.compose.extensions", "true")
    arg("atom.metro.origin", "true")
}

dependencies {
    add("kspCommonMainMetadata", project(":ksp"))
}
