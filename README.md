# Aayna v2.5.0

**Aayna** is an offline-first, tactile retro & CCD camera Android application built with Kotlin, Jetpack Compose, CameraX, and a persistent 4-pass OpenGL ES 3.0 optical shader pipeline.

## Highlights

- **100% Offline & Private**: Zero `INTERNET` permission in the merged manifest. All optical shader processing, LUT grading, date stamping, GIF/MP4 encoding, and negative storage run strictly on-device.
- **ProCCD-Inspired Hardware Chassis & Skins**:
  - **83 Unique Clean-Room Cameras** spanning Classic CCD/Digicam, Disposable, DV/VHS, Instant, Rangefinder/SLR, D3D Multi-Lens, and 4-Strip Photo Booth profiles.
  - **24 Layered Hardware Skins** (`chassis_bg`, `bezel_overlay`, `shutter_button`, and `screen_glass_sheen`) plus **84 camera icons** and **5 custom TTF hardware fonts** (`aayna_led_seven_segment`, `aayna_dot_matrix`, `aayna_vcr_osd`, `aayna_pixel_lcd`, `aayna_hardware_condensed`).
  - Tactile horizontal notched ruler dials with mechanical haptics and bundled audio SFX.
- **Persistent OpenGL ES 3.0 Pipeline (`AaynaPersistentGlEngine`)**:
  - Shared `EGL14` ES 3.0 context with `EGL_RECORDABLE_ANDROID` driving CameraX `SurfaceProcessor` (`Preview` + `VideoCapture`), Photo Studio `TextureView` window rendering (`StudioGlPreviewRenderer`), and offscreen high-resolution/4-strip export (`OffscreenGlExporter`).
  - 4-pass optical rendering: pre-baked 2D PNG / `.cube` 3D LUT grading, Bradford chromatic adaptation WB, 4-stage halation/bloom, temporal film grain, 9 lens attachments, 8 frame borders, and bitmap-atlas date stamps.
- **Multi-Mode Capture**:
  - **Still & Analog Roll**: Instant Digicam Save, Non-Destructive Roll / Negatives, and 24/36-exposure Analog Developing Ritual.
  - **D3D Wigglegram Burst**: Multi-frame burst capture encoded offline to looping GIF89a (`Pictures/Aayna`).
  - **DCR VideoCapture**: Hardware-accelerated MP4 video recording (`Movies/Aayna`) with optional `RECORD_AUDIO` support and live shader effects.
  - **Photo Booth 4-Strip**: 4-frame composite strip rendering in both live capture and Studio import.
- **Non-Destructive Darkroom Studio**:
  - Real-time `StudioGlPreviewRenderer` scrubbing across Camera, Adjust, Film FX, Frame & Lens, Date Stamp, and Crop/Rotate tabs.
  - Room v2 negative database (`MIGRATION_1_2`) with soft-delete Trash/Restore, `ThumbnailMemoryCache`, split-swipe compare, hold-for-clean-negative preview, EXIF viewer, and batch JPEG/GIF export.
- **Device Quirk Registry & Diagnostics**:
  - Pure-Kotlin `DeviceQuirkRegistry` adapting preview mode, front-camera mirroring, EV clamping, and manual AE fallback per SoC/OEM HAL, inspectable alongside live Camera2 capabilities and GL shader pass statuses in Settings → Diagnostics.
- **v2.5.0 Integration & Hardening**:
  - `FileProvider` (`${applicationId}.fileprovider`) configured for pre-Q (API 26–28) shareable photo/video URIs across `Pictures/Aayna`, `Movies/Aayna`, app files, and cache.
  - Persistent `AaynaPersistentGlEngine` wired directly into `GalleryStudioRoute`, with `compileDebugAndroidTestKotlin` enforced in CI alongside `ktlintCheck`, `testDebugUnitTest`, and `lintDebug`.

## Module Architecture

- `:app` — Application container, lifecycle-safe GL engine retention, navigation, ProCCD Settings (`SettingActivity` parity), Diagnostics, R8 rules, and release packaging
- `:core:model` — Pure Kotlin domain contracts, 83-camera & 24-skin catalogs, exposure math, date formatting, and `DeviceQuirkRegistry`
- `:core:gl` — Persistent `EGL14` engine, CameraX `SurfaceProcessor`, `StudioGlPreviewRenderer`, `OffscreenGlExporter`, 4-pass GLSL shaders, LUTs, grunge frames, and stamp atlases
- `:core:camera` — CameraX `PreviewView` + `ImageCapture` + `VideoCapture<Recorder>` controller, Camera2 interop, zoom/focus/torch, and quirk enforcement
- `:core:storage` — Room v2 database (`AaynaDatabase`), DataStore preferences, `ThumbnailMemoryCache`, `MediaStoreExporter`, offline `GifWigglegramEncoder`, ExifInterface, and `AaynaSoundEngine`
- `:feature:viewfinder` — Fixed-deck ProCCD chassis viewfinder, 24-skin loader, notched ruler, camera shelf, and multi-mode capture pipelines
- `:feature:gallery` — Darkroom Studio editor, contact sheet, roll developing ritual, D3D GIF studio, Photo Booth 4-strip, and trash management

## Build Stack

- **Gradle**: 9.3.1
- **Android Gradle Plugin**: 9.1.1 (Built-In Kotlin)
- **Kotlin**: 2.4.10
- **KSP**: 2.3.12
- **Jetpack Compose BOM**: 2026.09.00
- **CameraX**: 1.6.2 (`core`, `camera2`, `lifecycle`, `view`, `video`)
- **Compile SDK / Target SDK / Min SDK**: 37 / 36 / 26

## Verification

```bash
./gradlew ktlintCheck testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug assembleRelease --no-daemon
```

## License

MIT License — see [LICENSE](LICENSE).
