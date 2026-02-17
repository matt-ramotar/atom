# API Compatibility Policy (1.x)

This document defines Atom's API compatibility guarantees for the `1.x` line and the CI checks that enforce them.

## Scope

Compatibility guarantees apply to published modules:

- `runtime`
- `ksp`
- `metro`
- `gradle-plugin`

Non-published sample/demo code is excluded from API compatibility enforcement.

## Versioning Contract

Atom follows semantic versioning:

- `MAJOR` (`2.0.0`): may include breaking API changes.
- `MINOR` (`1.y.0`): adds functionality in a backward-compatible way.
- `PATCH` (`1.y.z`): backward-compatible bug fixes and internal changes.

For `1.x`, public API breaking changes are not allowed.

## What Counts as Breaking

Examples of incompatible changes include:

- Removing or renaming public classes, interfaces, functions, properties, or typealiases.
- Changing public method signatures (parameter types/order/count, return type, visibility).
- Changing type hierarchy in ways that break existing binaries.
- Tightening nullability or generic bounds in binary-incompatible ways.

## Deprecation and Removal Policy

For `1.x`:

- Public API removals must not happen in `1.x`.
- APIs should be deprecated before removal in a future major version.
- Deprecation should include a clear replacement path.

## Enforcement in CI

API compatibility is enforced using Kotlin Binary Compatibility Validator:

- Baselines are generated with `./gradlew apiDump`.
- Compatibility checks run with `./gradlew apiCheck`.
- `apiCheck` is required in CI verify/release workflows.

Any API-incompatible change must either:

1. be reverted, or
2. be postponed to the next major version and handled via explicit major-release planning.

## Updating Baselines

`apiDump` should only be run intentionally when introducing approved additive API changes.

Before committing updated `api/*.api` baselines:

- verify the change is backward compatible for `1.x`,
- confirm docs/tests are updated where relevant,
- ensure `apiCheck` passes in CI.
