# Théia

Théia is a digital analog camera for iOS and Android. A camera body, lens, and film stock form a visual recipe applied to the live preview and final capture. The product favors a tactile, expressive, collectible experience that works locally without a required account or server.

## Status

The repository contains the approved product specification and the Kotlin Multiplatform domain foundation. The Android and iOS interfaces and platform adapters will be added in the implementation tickets.

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
```

Building the iOS framework and running every native test target requires Xcode 26.4 or later.

## License

The code is available under PolyForm Noncommercial 1.0.0. Commercial use requires separate authorization. The Théia name, logo, and brand assets are protected and are not licensed for independent use.
