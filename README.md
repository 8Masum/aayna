<div align="center">

<img src="logo/aayna.png" alt="Aayna Logo" width="160" />

# Aayna

**Offline-first, tactile retro & CCD camera for Android**

[![Build and Release Android APK](https://github.com/8Masum/aayna/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/8Masum/aayna/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/8Masum/aayna?label=release)](https://github.com/8Masum/aayna/releases/latest)
[![License: MIT](https://img.shields.io/github/license/8Masum/aayna)](LICENSE)
[![Android Min SDK](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![Offline First](https://img.shields.io/badge/Privacy-100%25%20Offline-111111)](#privacy--offline-guarantee)

[Download APK](https://github.com/8Masum/aayna/releases/latest) · [Highlights](#highlights) · [Architecture](#module-architecture) · [Build & Verify](#build--verification) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md)

</div>

---

**Aayna** (current release: **v2.5.0**) is an offline-first, tactile retro & CCD camera Android application built with Kotlin, Jetpack Compose, CameraX, and a persistent 4-pass OpenGL ES 3.0 optical shader pipeline.

## Download & Verify APK

Signed release APKs are published on [GitHub Releases](https://github.com/8Masum/aayna/releases/latest):

- **`Aayna.apk`** / **`Aayna-v2.5.0.apk`** — Signed universal release APK (Android 8.0 Oreo / API 26+)
- **`SHA256SUMS.txt`** — SHA-256 checksums to verify APK integrity before installation:

```bash
sha256sum -c SHA256SUMS.txt
```

## Highlights

- **100% Offline & Private**: Zero `INTERNET` permission in the merged manifest. All optical shader processing, LUT grading, date stamping, GIF/MP4 encoding, and negative storage run strictly on-device.
- **ProCCD-Inspired Hardware Chassis & Skins**:
  - **83 Unique Clean-Room Cameras** spanning Classic CCD/Digicam, Disposable, DV/VHS, Instant, Rangefinder/SLR, D3D Multi-Lens, and 4-Strip Photo Booth profiles.
  - **24 Layered Hardware Skins** (`chassis_bg`, `bezel_overlay`, `shutter_button`, and `screen_glass_sheen`) plus **84 camera icons** and **5 custom TTF hardware fonts** (`aayna_led_seven_segment`, `aayna_dot_matrix`, `aayna_vcr_osd`, `aayna_pixel_lcd`, `aayna_hardware_condensed`).
  - Tactile horizontal notched ruler dials with mechanical haptics and bundled audio SFX.
- **Persistent OpenGL ES 3.0 Pipeline (`AaynaPersistentGlEngine`)**:
  - Shared `EGL14` ES 3.0 context with `EGL_RECORDABLE_ANDROID` driving CameraX `SurfaceProcessor` (`Preview` + `VideoCapture`), Photo Studio `TextureView` window rendering (`StudioGlPreviewRenderer`), and offscreen high-resolution/4-strip export (`OffscreenGlExporter`).
  - 4-pass optical rendering: pre-baked 2D PNG / `.cube` 3D LUT grading, Bradford chromatic adaptation WB, 4-stage halation/bloom, temporal film grain, 9 lens attachment modes (Standard + 8 optical attachments), 8 frame border styles (None + 7 frames), and bitmap-atlas date stamps.
- **Multi-Mode Capture**:
  - **Still & Analog Roll**: Instant Digicam Save, Non-Destructive Roll / Negatives, and 24/36-exposure Analog Developing Ritual.
  - **D3D Wigglegram Burst**: Multi-frame burst capture encoded offline to looping GIF89a (`Pictures/Aayna`) and MP4 (`D3dMp4Encoder`).
  - **DCR VideoCapture**: Hardware-accelerated MP4 video recording (`Movies/Aayna`) with optional `RECORD_AUDIO` support and live shader effects (`VideoGlTranscoder`).
  - **Photo Booth 4-Strip**: 4-frame composite strip rendering in both live capture and Studio import.
- **Non-Destructive Darkroom Studio**:
  - Real-time `StudioGlPreviewRenderer` scrubbing across Camera, Adjust, Film FX, Frame & Lens, Date Stamp, and Crop/Rotate tabs with undo support.
  - Room v2 negative database (`MIGRATION_1_2`) with soft-delete Trash/Restore, `ThumbnailMemoryCache`, split-swipe compare, hold-for-clean-negative preview, EXIF viewer, and batch JPEG/GIF export.
- **Device Quirk Registry & Diagnostics**:
  - Pure-Kotlin `DeviceQuirkRegistry` adapting preview mode, front-camera mirroring, EV clamping, and manual AE fallback per SoC/OEM HAL, inspectable alongside live Camera2 capabilities and GL shader pass statuses in **Settings → Diagnostics**.
- **v2.5.0 Integration & Hardening**:
  - `FileProvider` (`${applicationId}.fileprovider`) configured for pre-Q (API 26–28) shareable photo/video URIs across `Pictures/Aayna`, `Movies/Aayna`, app files, and cache.
  - Persistent `AaynaPersistentGlEngine` wired directly into `GalleryStudioRoute`, with `:core:model:test`, `compileDebugAndroidTestKotlin`, and manifest offline checks enforced in CI alongside `ktlintCheck`, `testDebugUnitTest`, and `lintDebug`.

## Module Architecture

| Module | Responsibility |
| --- | --- |
| `:app` | Application container, lifecycle-safe GL engine retention, navigation, ProCCD Settings, Diagnostics, `FileProvider`, R8 rules, and release packaging |
| `:core:model` | Pure Kotlin domain contracts, 83-camera & 24-skin catalogs, exposure math, date formatting, and `DeviceQuirkRegistry` |
| `:core:gl` | Persistent `EGL14` engine, CameraX `SurfaceProcessor`, `StudioGlPreviewRenderer`, `OffscreenGlExporter`, `D3dMp4Encoder`, `VideoGlTranscoder`, 4-pass GLSL shaders, LUTs, grunge frames, and stamp atlases |
| `:core:camera` | CameraX `PreviewView` + `ImageCapture` + `VideoCapture<Recorder>` controller, Camera2 interop, zoom/focus/torch, and quirk enforcement |
| `:core:storage` | Room v2 database (`AaynaDatabase`), DataStore preferences, `ThumbnailMemoryCache`, `MediaStoreExporter`, `D3dBurstFrameStore`, offline `GifWigglegramEncoder`, ExifInterface, and `AaynaSoundEngine` |
| `:feature:viewfinder` | Fixed-deck ProCCD chassis viewfinder, 24-skin loader, notched ruler, camera shelf, and multi-mode capture pipelines |
| `:feature:gallery` | Darkroom Studio editor, contact sheet, roll developing ritual, D3D GIF/MP4 studio, Photo Booth 4-strip, video editor, and trash management |

## Repository Layout

```text
.
├── .github/
│   ├── ISSUE_TEMPLATE/          # Bug report, device quirk, and feature request forms
│   ├── workflows/               # CI verification, offline manifest check, signed release APK, dependency review
│   ├── CODEOWNERS
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── dependabot.yml
├── app/                         # Android application shell, manifest, resources, navigation, diagnostics
├── core/
│   ├── camera/                  # CameraX & Camera2 capability probing and policy math
│   ├── gl/                      # Persistent OpenGL ES 3.0 shaders, LUTs, grunge assets, and video/D3D encoders
│   ├── model/                   # Pure Kotlin domain catalog, math, and DeviceQuirkRegistry
│   └── storage/                 # Room v2 archive, DataStore preferences, MediaStore export, SFX
├── feature/
│   ├── gallery/                 # Darkroom Studio, contact sheet, video editor, 4-strip & D3D flows
│   └── viewfinder/              # ProCCD chassis viewfinder, 24 skins, rulers, drawers
├── gradle/                      # Gradle wrapper & libs.versions.toml version catalog
├── logo/                        # Brand logo assets (logo/aayna.png)
├── CHANGELOG.md
├── CODE_OF_CONDUCT.md
├── CONTRIBUTING.md
├── LICENSE
└── SECURITY.md
```

## Build Stack

- **Gradle**: `9.3.1`
- **Android Gradle Plugin**: `9.1.1` (Built-In Kotlin)
- **Kotlin**: `2.4.10`
- **KSP**: `2.3.12`
- **Jetpack Compose BOM**: `2026.09.00`
- **CameraX**: `1.6.2` (`core`, `camera2`, `lifecycle`, `view`, `video`)
- **Compile SDK / Target SDK / Min SDK**: `37` / `36` / `26`

## Build & Verification

### Prerequisites

- **JDK 21+** (Temurin or Android Studio JBR)
- **Android SDK** with `platform-tools`, `platforms;android-37.0`, and `build-tools;37.0.0`

### Local CI Checks & APK Build

```bash
./gradlew ktlintCheck :core:model:test testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug --no-daemon
```

To build a signed release APK locally, provide the signing environment variables before running `assembleRelease` (without these variables, `release` falls back to the debug keystore for local verification):

```bash
export AAYNA_KEYSTORE_PATH="/path/to/aayna-release.p12"
export AAYNA_KEYSTORE_PASSWORD="..."
export AAYNA_KEY_ALIAS="..."
export AAYNA_KEY_PASSWORD="..."
./gradlew assembleRelease --no-daemon
```

## Privacy & Offline Guarantee

Aayna intentionally omits `android.permission.INTERNET` and ships zero network or analytics SDKs. Every PR and release workflow in [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) asserts that `android.permission.INTERNET` is absent from the merged Android manifests.

## Contributing & Security

- Read [CONTRIBUTING.md](CONTRIBUTING.md) for code style, commit conventions, and local test commands.
- Use [Device / Camera Quirk Report](.github/ISSUE_TEMPLATE/device_quirk.yml) with your **Settings → Diagnostics** plain-text report to report OEM/SoC camera issues.
- Report security issues privately via [SECURITY.md](SECURITY.md).
- Community expectations are defined in [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
- Release history is tracked in [CHANGELOG.md](CHANGELOG.md).

## License

MIT License — see [LICENSE](LICENSE).
