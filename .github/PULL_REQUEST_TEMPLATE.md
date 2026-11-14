## Summary

<!-- Describe what this pull request changes and why. -->

Fixes #

## Type of Change

- [ ] `feat` — New feature, camera profile, skin, or darkroom tool
- [ ] `fix` — Bug fix or OEM/HAL quirk fix
- [ ] `docs` — Documentation or repository metadata update
- [ ] `ci` — GitHub Actions workflow, Dependabot, or build tooling
- [ ] `refactor` — Code restructuring without behavior changes
- [ ] `test` — Unit or instrumented test additions/improvements
- [ ] `chore` — Dependency update or maintenance

## Modules Affected

- [ ] `:app`
- [ ] `:core:model`
- [ ] `:core:gl`
- [ ] `:core:camera`
- [ ] `:core:storage`
- [ ] `:feature:viewfinder`
- [ ] `:feature:gallery`
- [ ] `.github/` / docs / Gradle config

## Verification Checklist

- [ ] `./gradlew ktlintCheck` passes with zero errors
- [ ] `./gradlew :core:model:test` passes
- [ ] `./gradlew testDebugUnitTest` passes
- [ ] `./gradlew lintDebug` passes
- [ ] `./gradlew compileDebugAndroidTestKotlin` passes
- [ ] `./gradlew assembleDebug` succeeds
- [ ] **Offline-First Guarantee**: Confirmed no `INTERNET` permission, network SDK, or telemetry was added
- [ ] Added or updated unit tests for domain, math, or state-reducer changes
- [ ] Verified UI / camera / OpenGL behavior on device or emulator (attach screenshots or diagnostics if applicable)
