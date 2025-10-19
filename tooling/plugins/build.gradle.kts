plugins {
    `kotlin-dsl`
}

group = "dev.mattramotar.atom.tooling"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.dokka.gradle.plugin)
    compileOnly(libs.maven.publish.plugin)
    implementation(libs.kover.gradle.plugin)
}

gradlePlugin {
    plugins {

        register("androidLibraryPlugin") {
            id = "plugin.atom.android.library"
            implementationClass = "dev.mattramotar.atom.tooling.plugins.AndroidLibraryConventionPlugin"
        }

        register("kotlinAndroidLibraryPlugin") {
            id = "plugin.atom.kotlin.android.library"
            implementationClass = "dev.mattramotar.atom.tooling.plugins.KotlinAndroidLibraryConventionPlugin"
        }

        register("kotlinMultiplatformPlugin") {
            id = "plugin.atom.kotlin.multiplatform"
            implementationClass = "dev.mattramotar.atom.tooling.plugins.KotlinMultiplatformConventionPlugin"
        }

        register("mavenPublishPlugin") {
            id = "plugin.atom.maven.publish"
            implementationClass = "dev.mattramotar.atom.tooling.plugins.MavenPublishConventionPlugin"
        }
    }
}