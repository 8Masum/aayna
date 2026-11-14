# Changelog

All notable changes to **Aayna** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Repository community health and governance files (`CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CHANGELOG.md`, `.github/CODEOWNERS`, issue forms, PR template, and Dependabot configuration).
- Split CI verification and signed release jobs in `.github/workflows/build-apk.yml` with least-privilege permissions, concurrency control, `assembleDebug`, merged-manifest `INTERNET` permission verification, and `dependency-review.yml` for pull requests.

## [2.5.0] - 2026-11-14

### Added
- Configured `FileProvider` (`${applicationId}.fileprovider` and `res/xml/file_paths.xml`) for pre-Q (API 26–28) shareable photo/video URIs across `Pictures/Aayna`, `Movies/Aayna`, app files, and cache.
- Wired persistent `AaynaPersistentGlEngine` directly into `GalleryStudioRoute` (`StudioGlPreviewRenderer`).
- Added lossless Darkroom Studio edits, undo support, audio recording permission prompt, D3D frame/MP4 export flow (`D3dBurstFrameStore`, `D3dMp4Encoder`), and video editor (`VideoGlTranscoder`).
- Enforced `:core:model:test` and `compileDebugAndroidTestKotlin` alongside `ktlintCheck`, `testDebugUnitTest`, and `lintDebug` in CI, plus SHA-pinned workflows and fork-safe Gradle dependency submission.

### Fixed
- Hardened CameraX / Camera2 HAL video priority, transform ordering, GL aspect-ratio/unsharp math, and pre-Q MediaStore storage behavior.

## [2.0.0] - 2026-11-14

### Added
- ProCCD-inspired fixed-deck hardware viewfinder chassis, 24 layered hardware skins, 84 camera icons, 5 custom hardware TTF fonts, and tactile notched ruler dials with haptics/SFX.
- Expanded clean-room camera catalog to 83 unique profiles across Classic CCD/Digicam, Disposable, DV/VHS, Instant, Rangefinder/SLR, D3D Multi-Lens, and 4-Strip Photo Booth categories.
- Persistent `EGL14` OpenGL ES 3.0 pipeline (`AaynaPersistentGlEngine`) driving CameraX `SurfaceProcessor`, Darkroom Studio `TextureView`, and offscreen high-res/4-strip export.
- `DeviceQuirkRegistry` in `:core:model` and live quirk/shader status reporting in Settings → Diagnostics.
- Darkroom Studio editing across Camera, Adjust, Film FX, Frame & Lens, Date Stamp, and Crop/Rotate tabs with Room v2 negative archive (`MIGRATION_1_2`) and Trash/Restore.

## [1.0.0] - 2026-11-13

### Added
- Initial multi-module Android architecture (`:app`, `:core:model`, `:core:gl`, `:core:camera`, `:core:storage`, `:feature:viewfinder`, `:feature:gallery`).
- Core 4-pass OpenGL ES 3.0 optical shader pipeline, `.cube` 3D LUTs, grunge frames, and date stamp atlases.
- CameraX + Camera2 capability probe, Room negative archive, DataStore preferences, MediaStore exporter, offline GIF wigglegram encoder, adaptive launcher icons, and signed APK CI/CD release pipeline.

[Unreleased]: https://github.com/8Masum/aayna/compare/v2.5.0...HEAD
[2.5.0]: https://github.com/8Masum/aayna/compare/v2.0.0...v2.5.0
[2.0.0]: https://github.com/8Masum/aayna/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/8Masum/aayna/releases/tag/v1.0.0
