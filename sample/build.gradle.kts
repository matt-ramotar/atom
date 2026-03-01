import com.google.devtools.ksp.gradle.KspExtension

plugins {
    id("plugin.atom.kotlin.android.library")
    id("plugin.atom.kotlin.multiplatform")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.runtime)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
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
    arg("atom.di", "manual")
    arg("atom.compose.extensions", "true")
}

dependencies {
    add("kspCommonMainMetadata", project(":ksp"))
}
