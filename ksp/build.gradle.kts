@file:Suppress("UnstableApiUsage")

group = "dev.mattramotar.atom"
version = libs.versions.atom.get()

plugins {
    kotlin("multiplatform")
    id("plugin.atom.maven.publish")
    alias(libs.plugins.kover)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain {
            dependencies {
                implementation(libs.symbol.processing.api)
                implementation(libs.kotlinpoet.ksp)
                api(projects.atom.runtime)
            }

            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kctfork.core)
                implementation(libs.kctfork.ksp)
                implementation(libs.metro.runtime)
                implementation(projects.atom.runtime)
                implementation(projects.atom.metro)
            }

            kotlin.srcDir("src/test/kotlin")
            resources.srcDir("src/test/resources")
        }
    }
}