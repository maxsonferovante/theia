# Théia Architecture

## Purpose

Théia shares domain rules between Android and iOS while keeping the user interface, camera capture, media access, permissions, and visual processing native to each platform. This document describes the current project foundation and the boundaries that later feature work should follow.

## Project Structure

- `shared` is the Kotlin Multiplatform module. Its `commonMain` source set owns domain models, stable identifiers, versioned serialization, platform ports, and application use cases.
- The Android target is provided by Google's Android-KMP library plugin and builds an Android library.
- The iOS targets produce a static `TheiaShared` framework for ARM64 devices and Apple Silicon simulators.
- Native app entry points and platform adapter implementations are added by their feature tickets. The current module intentionally contains no camera or UI framework dependencies.

## Dependency Direction

The shared use cases depend on narrow interfaces defined in the shared domain layer. Native adapters will implement those interfaces and be supplied at the app entry point. Platform types such as `AVCaptureSession`, `android.net.Uri`, and `PHAsset` must not cross into `commonMain`.

```text
Native UI and platform adapters
             ↓
Shared use cases and domain ports
             ↓
Shared domain models and rules
```

The first automated test seam is `CapturePhotoUseCase`. Tests provide simulated camera and media-library adapters and assert the resulting `PhotoAsset` and save order.

## Domain Model

Persistent domain models use typed, non-empty identifiers. `PermissionState` has a stable singleton ID, while each `GeotagMetadata` value receives an ID from its caller. `CaptureRecipe` contains exactly one camera body, one lens, and one film stock; its identifier is derived deterministically from those three stable IDs. Film rolls accept 24 or 36 exposures, reject duplicate photos, and cannot accept additional photos after development.

`VersionedJsonCodec` wraps serialized domain values in an envelope containing `schemaVersion`. The initial schema version is 1. Decoding rejects versions this build does not support and ignores unknown object fields so additive fields can be introduced without breaking older readers.

## Platform Ports

- `CameraCapturePort` captures an original image for a recipe.
- `ImageProcessingPort` develops a captured image with its recipe.
- `MediaLibraryPort` saves original and developed images and removes gallery items.
- `PermissionPort` reads and requests platform permissions.
- `AppLifecyclePort` reports and observes foreground state.
- `EquipmentCatalogPort` exposes the camera body, lens, and film-stock catalog by stable ID.

The interfaces use common Kotlin values and opaque string handles. Android and iOS adapters translate those values to their platform APIs.

## Build and Verification

Use the checked-in Gradle wrapper and the Android SDK:

```sh
./gradlew :shared:compileAndroidMain
./gradlew :shared:testAndroidHostTest
```

The project declares `iosArm64` and `iosSimulatorArm64` framework targets. Building those targets and running the full native test aggregation requires a full Xcode installation that matches the Kotlin Multiplatform compatibility range; Command Line Tools alone are not enough.

## Current Boundary

The foundation defines reusable models, serialization, ports, and one representative use case. It does not yet implement real camera or media-library adapters, UI, catalog content, image effects, storage, or dependency injection. Those responsibilities belong to their corresponding implementation tickets.
