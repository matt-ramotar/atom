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
- Android SDK (for Android test/lint lanes)
- Google Chrome (for `jsBrowserTest`)
- macOS + Xcode toolchain (for `iosSimulatorArm64Test` lanes)

## Installation matrix

| Use case | What to add | Notes |
| --- | --- | --- |
| Atom Gradle plugin | `plugins { id("dev.mattramotar.atom") version "<version>" }` | Applies KMP + KSP and configures Atom defaults. |
| Runtime only | `implementation("dev.mattramotar.atom:runtime:<version>")` | Use when you do not want plugin auto-wiring. |
| KSP processor only | `add("kspCommonMainMetadata", "dev.mattramotar.atom:ksp:<version>")` | Required for `@AutoAtom` generation without plugin wiring. |
| Metro runtime bridge | `implementation("dev.mattramotar.atom:metro:<version>")` | Needed for Metro DI when not relying on plugin-managed dependencies. |

The plugin default DI mode is `DI.MANUAL`.

If you use the Gradle plugin without a `libs.versions.toml` entry for `atom`, set:

```kotlin
atom {
    version = "<version>"
}
```

When `di = DI.METRO`, the plugin adds the `metro` dependency automatically.

## Quickstart (manual DI, default)

```kotlin
plugins {
    id("dev.mattramotar.atom") version "<version>"
    alias(libs.plugins.kotlinx.serialization)
    // Needed only if compose helpers are enabled.
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

atom {
    // Explicit for clarity; this is the default.
    di = DI.MANUAL
    compose = true
}
```

## Quickstart (Metro DI)

```kotlin
plugins {
    id("dev.mattramotar.atom") version "<version>"
    id("dev.zacsweers.metro")
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.compose.compiler)
}

atom {
    di = DI.METRO
    compose = true
    // Optional overrides
    // scope = "dev.zacsweers.metro.AppScope"
    // injectAnnotation = "dev.zacsweers.metro.Inject"
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

## Compose usage notes

- Use `key` to create independent atom instances of the same type.
- `params` are part of atom identity in Compose; changing `params` with the same `key` recreates the atom.
- Keep `params` stable across recompositions (for example, with `remember`) to avoid lifecycle churn.
- If CompositionLocals that provide factories/state handles/store owner change, atom instances are recreated by design.

## Troubleshooting

If `./gradlew check --continue` fails with `SDK location not found`, set one of:

- `ANDROID_HOME=/absolute/path/to/android/sdk`
- `ANDROID_SDK_ROOT=/absolute/path/to/android/sdk`
- `local.properties` at repo root with:

```properties
sdk.dir=/absolute/path/to/android/sdk
```

If JS browser tests fail with `Please set env variable CHROME_BIN`:

```bash
export CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
```

If Metro DI is enabled and you see Metro wiring errors, ensure the Metro plugin is applied:

```kotlin
plugins {
    id("dev.zacsweers.metro")
}
```

If Compose helper generation is enabled and compile fails for missing Compose compiler integration, add:

```kotlin
plugins {
    alias(libs.plugins.compose.compiler)
}
```

Or disable helper generation:

```kotlin
atom {
    compose = false
}
```

## Compatibility and migration

Atom enforces binary compatibility for `1.x` public APIs in CI using Kotlin Binary Compatibility Validator.

See `docs/API_COMPATIBILITY_POLICY.md` for scope, guarantees, and baseline update rules.

## Target support

Atom's versioned multiplatform support and CI enforcement levels are defined in
`docs/TARGET_SUPPORT_MATRIX.md`.

## Local verification

- `./gradlew apiCheck --no-configuration-cache`
- `./gradlew :ksp:jvmTest :ksp:allTests :gradle-plugin:test --continue`
- `./gradlew :runtime:jvmTest :metro:jvmTest :sample:jvmTest --continue`
- `./gradlew :runtime:testDebugUnitTest :metro:testDebugUnitTest :sample:testDebugUnitTest --continue`
- `./gradlew :runtime:jsBrowserTest :runtime:jsTest :metro:jsTest :sample:jsTest --continue`
- `./gradlew :runtime:iosSimulatorArm64Test :metro:iosSimulatorArm64Test :sample:iosSimulatorArm64Test --continue`
- `./gradlew check --continue` (requires Android SDK configured)

## License

Licensed under Apache 2.0. See `LICENSE`.
