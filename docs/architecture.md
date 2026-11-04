# Théia Architecture

## Purpose

Théia shares domain rules between Android and iOS while keeping the interface, capture stack, media access, permissions, and image processing native. This document describes the current project structure and boundaries.

## Project Structure

- `shared` is the Kotlin Multiplatform module. Its `commonMain` source set owns domain models, stable identifiers, the built-in equipment catalog, versioned serialization, platform ports, and photo/video/film-roll/privacy use cases.
- `androidApp` is a native Jetpack Compose app. CameraX captures still photos and videos; MediaStore saves originals; an app-private album repository stores capture associations and user annotations.
- `iosApp/Théia` is a native SwiftUI app. AVFoundation captures still photos and videos; Photos saves gallery assets; an app-private album repository stores capture associations and favorites.
- The Android KMP target builds an Android library. The iOS targets produce static `TheiaShared` frameworks for ARM64 devices and Apple Silicon simulators.
- The shared catalog is the source of truth for item identifiers, localized copy keys, artwork references, and versioned film visual profiles. Native renderers consume the same resolved profile and apply platform-specific image and video processing.

## Dependency Direction

Shared use cases depend on narrow interfaces defined in the domain layer. Native adapters are supplied by the app composition root. Platform types such as `AVCaptureSession`, `android.net.Uri`, and `PHAsset` must not cross into `commonMain`.

```text
Native UI and platform adapters
             ↓
Shared use cases and domain ports
             ↓
Shared domain models and rules
```

Automated seams include `CapturePhotoUseCase`, film-roll use cases, video capture, and geotag use cases. Tests provide simulated platform adapters and assert save order, recipe association, permission gating, and visibility rules.

## Domain Model

Persistent domain models use typed, non-empty identifiers. `CaptureRecipe` contains exactly one camera body, one lens, and one film stock; its identifier is deterministic from those three stable IDs. Film rolls accept 24 or 36 exposures, reject duplicates, and cannot accept photos after development. `FilmRollRecord` keeps a roll's recipe fixed and associates gallery originals with each exposure.

`VersionedJsonCodec` wraps serialized domain values in an envelope containing `schemaVersion`. The initial schema version is 1. Decoding rejects unsupported versions and ignores unknown object fields so additive values can be introduced compatibly.

## Platform Ports

- `CameraCapturePort` captures an original image for a recipe.
- `VideoCapturePort` records a video with an explicit audio choice and maximum duration.
- `ImageProcessingPort` develops a captured image with its recipe.
- `MediaLibraryPort` saves original and developed images and removes gallery items.
- `FilmRollRepositoryPort` persists roll records and serializes mutations per roll.
- `OriginalPhotoSourcePort` reopens a gallery original after process restart so local development can resume.
- `PermissionPort` reads and requests platform permissions.
- Geotag ports are called only after explicit opt-in and operate on device-local metadata.
- `AppLifecyclePort` reports and observes foreground state.
- `EquipmentCatalogPort` exposes camera body, lens, and film-stock data by stable ID.

Ports use common Kotlin values and opaque string handles. Android and iOS adapters translate them to their native APIs.

## Local Media and Privacy

Original stills are saved to the system photo library at capture time so uninstalling Théia does not remove them. Local album metadata remains app-private and is separate from the gallery originals. Geotagging defaults to off; camera, microphone, photo-library, and location permissions are requested in the feature flow that needs them. The application has no account, required server, advertising, or analytics dependency.

Théia keeps undeveloped photos out of its album view until the roll is developed; the originals remain in the system gallery. Development writes a separate rendered asset. Video audio is opt-in. Saved recipe metadata is compact and best-effort because gallery apps and sharing flows may strip it. Android disables app backup, and iOS excludes local metadata files from backup.

## Build and Verification

Run the shared domain tests and Android build with the checked-in Gradle wrapper:

```sh
./gradlew :shared:testAndroidHostTest :androidApp:compileDebugKotlin :androidApp:assembleDebug
```

Build the iOS shell with a full Xcode installation:

```sh
xcodebuild -project 'iosApp/Théia.xcodeproj' -scheme Théia \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

The simulator build does not replace device-level camera, gallery, permission, and performance checks. Verify representative devices and the product targets before a release.

## Verification Boundary

The shared domain, catalog and Workbench, native camera flows, rolls and development, Album/Lab, optional local geotags, video, onboarding, settings, and English/Brazilian Portuguese resources are implemented in this repository. Shared domain tests, an Android debug build, and a generic iOS Simulator build have passed during implementation. An Android physical-device smoke check previously reached the live camera preview, but the final additions have not yet been reinstalled on that device.

The iOS and Android renderers use the same resolved recipe profile, but their Core Image and GLES implementations are platform-specific; visual parity still requires side-by-side device validation. No sustained 30 fps measurement, Instruments trace, or Perfetto/System Trace has been recorded. Those device measurements and interrupted-capture/storage-failure checks remain open release validation tasks in `docs/performance-validation.md`.
