# Aayna

**Aayna** is an offline-first, tactile retro & CCD camera Android application built with Kotlin, Jetpack Compose, CameraX, and a custom OpenGL ES 3.0 optical shader pipeline.

## Highlights

- **100% Offline & Private**: Zero `INTERNET` permission. All optical processing, film emulation, date stamping, and storage stay strictly on-device.
- **Tactile Camera Roster**: Vintage digicam CCD, disposable film, lo-fi cross-processed, 35mm rangefinder, and instant print aesthetics.
- **Custom OpenGL ES 3.0 Pipeline**: Real-time 3D `.cube` LUT grading, halation/bloom, film grain, chromatic aberration, optical date stamps, and print frames.
- **Modular Clean Architecture**:
  - `:app` — Application entry point, navigation, and release packaging
  - `:core:model` — Pure Kotlin domain models and serialization contracts
  - `:core:gl` — OpenGL ES 3.0 optical shader pipeline, LUTs, stamps, and grunge assets
  - `:core:camera` — CameraX capture, exposure, flash, and hardware integration
  - `:core:storage` — Room database, DataStore preferences, ExifInterface, MediaStore export, and tactile audio SFX
  - `:feature:viewfinder` — ProCCD-inspired charcoal/metallic studio viewfinder UI
  - `:feature:gallery` — Film roll / instant print review and export gallery

## Build Stack

- **Gradle**: 9.3.1
- **Android Gradle Plugin**: 9.1.1 (Built-In Kotlin)
- **Kotlin**: 2.4.10
- **KSP**: 2.3.12
- **Jetpack Compose BOM**: 2026.09.00
- **Compile SDK / Target SDK / Min SDK**: 37 / 36 / 26

## Verification

```bash
./gradlew ktlintCheck testDebugUnitTest lintDebug
```

## License

MIT License — see [LICENSE](LICENSE).
