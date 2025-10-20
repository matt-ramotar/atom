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
    }
}