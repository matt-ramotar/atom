# Target Support Matrix (v1.0.0)

This document defines Atom's versioned target support and the CI lanes that enforce it for `v1.0.0`.

## Support Levels

- `Required CI lane`: must pass in the required `verify` workflow.
- `Release smoke`: must pass in `verify-release` before publishing artifacts.
- `Best effort`: documented behavior with no dedicated required lane.

## Module Coverage

Published modules:

- `runtime`
- `metro`
- `ksp`
- `gradle-plugin`

Validation/demo module:

- `sample` (used for integration smoke verification)

## Matrix

| Target | Scope | Level | Enforcement |
| --- | --- | --- | --- |
| JVM | `runtime`, `metro`, `sample`, `ksp`, `gradle-plugin` | Required CI lane | `verify-jvm` runs `:ksp:jvmTest :ksp:allTests :gradle-plugin:test :runtime:jvmTest :metro:jvmTest :sample:jvmTest` |
| Android | `runtime`, `metro`, `sample` | Required CI lane | `verify-android` runs debug unit tests + lint for all three modules |
| JS (browser + node/js) | `runtime`, `metro`, `sample` | Required CI lane | `verify-js` runs `:runtime:jsBrowserTest :runtime:jsTest :metro:jsTest :sample:jsTest` with `CHROME_BIN` set |
| iOS simulator arm64 | `runtime`, `metro`, `sample` | Required CI lane | `verify-native` runs `:runtime:iosSimulatorArm64Test :metro:iosSimulatorArm64Test :sample:iosSimulatorArm64Test` |
| iOS x64 | `runtime`, `metro`, `sample` | Release smoke | `verify-native` and `verify-release` run `linkDebugTestIosX64` smoke link tasks |

## Release Parity Requirement

`verify-release` in `publish-artifacts.yml` mirrors the same target expectations before publish:

- API compatibility gate (`apiCheck`)
- JVM and plugin checks
- Android smoke checks
- JS smoke checks
- Native test + link smoke checks

Any change to target claims for `1.x` must update this matrix and the corresponding workflow lanes in the same PR.
