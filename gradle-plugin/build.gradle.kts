@file:Suppress("UnstableApiUsage")

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("plugin.atom.maven.publish")
    alias(libs.plugins.kover)
}

group = "dev.mattramotar.atom"
version = libs.versions.atom.get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    compileOnly(libs.android.gradle.plugin)
    implementation(libs.symbol.processing.gradle.plugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("atomPlugin") {
            id = "dev.mattramotar.atom"
            implementationClass = "dev.mattramotar.atom.gradle.AtomPlugin"
            displayName = "Atom Plugin"
            description = "Gradle plugin for configuring Atom."
        }
    }
}
