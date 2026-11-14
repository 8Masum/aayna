# Contributing to Aayna

Thank you for your interest in contributing to **Aayna**!

## Core Design Principles

1. **100% Offline & Private**: Aayna never requests `android.permission.INTERNET`, telemetry, analytics, or remote assets. Every shader pass, LUT transform, date stamp, GIF/MP4 export, and Room negative database operation must run strictly on-device.
2. **ProCCD-Inspired Tactile Design**: Keep UI changes aligned with the fixed-deck retro chassis aesthetic, mechanical haptics, and clean-room camera profiles.
3. **Device Compatibility First**: Hardware workarounds belong in `DeviceQuirkRegistry` (`:core:model`) with matching unit tests rather than ad-hoc OEM checks scattered across UI code.

## Prerequisites

- **JDK**: Java 21 (Temurin or Android Studio JBR)
- **Android SDK**: `platforms;android-37.0`, `build-tools;37.0.0`, `platform-tools`
- **Gradle**: Uses the checked-in Gradle Wrapper (`./gradlew`, Gradle 9.3.1)

Set `JAVA_HOME` and `ANDROID_HOME` (or `sdk.dir` in an untracked `local.properties` file) before running Gradle commands locally.

## Development Workflow

1. Fork `8Masum/aayna` and create a topic branch from `main`:
   - `feat/<short-description>`
   - `fix/<short-description>`
   - `docs/<short-description>`
   - `ci/<short-description>`
   - `refactor/<short-description>`
2. Follow [Conventional Commits](https://www.conventionalcommits.org/) matching repository history:
   - `feat(core-gl): ...`
   - `fix(feature-gallery): ...`
   - `docs: ...`
   - `chore(ci): ...`
3. Keep formatting compliant with `.editorconfig` and `ktlint` (Android Studio Kotlin style, 4-space indentation, LF line endings, 120-char line limit).

## Verification Before Opening a Pull Request

Run the full CI verification suite locally before pushing:

```bash
./gradlew ktlintCheck :core:model:test testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug --no-daemon
```

Also verify that the merged debug manifest does not include `android.permission.INTERNET`:

```bash
grep -n 'android.permission.INTERNET' app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml && exit 1 || echo "OK: no INTERNET permission"
```

## Contributing Device / Camera Quirks

If you are fixing or reporting an OEM/SoC Camera2 HAL or OpenGL rendering issue:

1. Open **Settings → ON-DEVICE CAMERA2, QUIRKS & GL DIAGNOSTICS** inside Aayna and tap **COPY REPORT** (or share the exported report) to capture the plain-text diagnostics report (`=== Project Aayna Diagnostics ===`).
2. Add or update rules in `core/model/src/main/kotlin/com/themasum/aayna/core/model/DeviceQuirkRegistry.kt`.
3. Add deterministic coverage in `core/model/src/test/kotlin/com/themasum/aayna/core/model/DeviceQuirkRegistryTest.kt`.

## Pull Request Guidelines

- Keep diffs focused on a single concern.
- Include unit tests for domain models, math helpers, quirk matching, or state reducers.
- Never commit keystores, signing properties, `local.properties`, or secrets.
- Complete the checklist in [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md).
