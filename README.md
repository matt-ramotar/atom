# Atom

[![Kotlin](https://img.shields.io/badge/kotlin-2.2.20+-white.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Atom is a Kotlin Multiplatform state-management toolkit that combines:

- A runtime MVI engine (`Atom`)
- KSP code generation for factory/registry wiring (`@AutoAtom`)
- Optional Metro DI helpers
- Optional Compose helper generation

## Modules

- `runtime`: core `Atom` APIs, lifecycle, state handles, and Compose integration.
- `ksp`: annotation processor that generates factories and registry entries.
- `metro`: Metro-specific runtime bridge types.
- `gradle-plugin`: `dev.mattramotar.atom` plugin for build/config wiring.

## Requirements

- JDK 17+
- Kotlin 2.2.20+
- Android SDK (required for full `check` and Android targets)

If `./gradlew check --continue` fails with `SDK location not found`, set one of:

- `ANDROID_HOME=/absolute/path/to/android/sdk`
- `ANDROID_SDK_ROOT=/absolute/path/to/android/sdk`
- `local.properties` at repo root with:

```properties
sdk.dir=/absolute/path/to/android/sdk
```

## API compatibility

Atom enforces binary compatibility for `1.x` public APIs in CI using Kotlin Binary Compatibility Validator.

See `docs/API_COMPATIBILITY_POLICY.md` for scope, guarantees, and baseline update rules.

## Quickstart (KMP)

```kotlin
plugins {
    id("dev.mattramotar.atom") version "<version>"
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("dev.mattramotar.atom:runtime:<version>")
            }
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", "dev.mattramotar.atom:ksp:<version>")
}

atom {
    di = DI.MANUAL
    compose = true
}
```

To enable Metro-based DI codegen:

```kotlin
dependencies {
    implementation("dev.mattramotar.atom:metro:<version>")
}

atom {
    di = DI.METRO
}
```

## Minimal Atom example

```kotlin
@Serializable
data class PostState(val id: String)

@Serializable data object PostIntent : Intent
@Serializable data object PostEvent : Event
@Serializable data object PostEffect : SideEffect
@Serializable data class PostParams(val id: String)

@AutoAtom
class PostAtom(
    scope: CoroutineScope,
    handle: StateHandle<PostState>
) : Atom<PostState, PostIntent, PostEvent, PostEffect>(scope, handle) {
    companion object {
        @InitialState
        fun initial(params: PostParams): PostState = PostState(params.id)
    }
}
```

## Local verification

- `./gradlew apiCheck --no-configuration-cache`
- `./gradlew :ksp:jvmTest :ksp:allTests :gradle-plugin:test --continue`
- `./gradlew :runtime:tasks --all`
- `./gradlew :metro:tasks --all`
- `./gradlew :sample:tasks --all`
- `./gradlew check --continue` (requires Android SDK configured)

## License

Licensed under Apache 2.0. See `LICENSE`.
