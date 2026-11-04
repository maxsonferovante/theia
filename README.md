# Théia

Théia is a digital analog camera for iOS and Android. A camera body, lens, and film stock form a visual recipe applied to the live preview and final capture. The product favors a tactile, expressive, collectible experience that works locally without a required account or server.

## Status

The repository contains the approved product specification, the Kotlin Multiplatform domain foundation, and an installable Android development shell. Product camera capture and the remaining native app flows will be added in their implementation tickets.

## Documentation

- Rationale for the name: `docs/rationale-for-the-name-theia.docx`
- Product specification: `docs/theia-product-spec.md`
- Architecture: `docs/architecture.md`
- Brand mark: `assets/brand/theia-mark.svg`

## Development

The shared module targets Android and iOS. Use the Gradle wrapper to compile and run the Android host tests:

```sh
./gradlew :shared:compileAndroidMain
./gradlew :shared:testAndroidHostTest
./gradlew :androidApp:assembleDebug
```

The development APK is written to `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. It exercises the shared capture-recipe model; camera capture is not implemented in this shell.

Building iOS frameworks and running iOS simulator tests requires Xcode and an installed iOS Simulator runtime.

## License

The code is available under PolyForm Noncommercial 1.0.0. Commercial use requires separate authorization. The Théia name, logo, and brand assets are protected and are not licensed for independent use.
