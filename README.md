# Théia

Théia is a digital analog camera for iOS and Android. A camera body, lens, and film stock form a visual recipe applied to captures. The product favors a tactile, expressive, collectible experience that keeps photos and videos under the user's local control.

## Status

The repository contains a Kotlin Multiplatform domain and native Android/iOS applications. Android now opens into a camera-first flow with an in-context recipe sheet, Photo/Video modes, film rolls and development, a local Album/Lab, optional geotags, onboarding, and privacy/accessibility settings. Photos and videos are saved to the system library as originals; development produces separate rendered copies. English and Brazilian Portuguese are supported.

The apps and shared domain compile, and the shared domain tests pass. Physical-device performance measurements and complete capture-flow validation on both platforms are still release-validation work; see `docs/performance-validation.md`.

## Documentation

- Rationale for the name: `docs/rationale-for-the-name-theia.docx`
- Product specification: `docs/theia-product-spec.md`
- Architecture: `docs/architecture.md`
- Interface redesign decisions and journeys: `docs/interface-redesign.md`
- Camera-first navigation decision: `docs/adr/0001-camera-first-interface.md`
- Android screen presentation with Motorola Edge 60 captures: [`docs/screenshots/android/README.md`](docs/screenshots/android/README.md)
- Catalog authoring: `docs/catalog-authoring.md`
- Performance validation: `docs/performance-validation.md`
- Contribution guide: `CONTRIBUTING.md`
- Brand mark: `assets/brand/theia-mark.svg`

## Development

Use the checked-in Gradle wrapper, JDK 17, and Android SDK:

```sh
./gradlew :shared:testAndroidHostTest :androidApp:compileDebugKotlin :androidApp:assembleDebug
```

The debug APK is written to `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Open the app on a device to use CameraX and MediaStore; emulator camera support depends on the selected virtual device.

Build the iOS app with Xcode:

```sh
xcodebuild -project 'iosApp/Théia.xcodeproj' -scheme Théia \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

Running the iOS app requires an installed Simulator runtime or a configured iOS device. The shared Kotlin frameworks can be built with `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` on macOS.

## License

The code is available under PolyForm Noncommercial 1.0.0. Commercial use requires separate authorization. The Théia name, logo, and brand assets are protected and are not licensed for independent use.
