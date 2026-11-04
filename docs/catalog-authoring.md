# Catalog Authoring

## Catalog model

Built-in equipment is defined in `shared/src/commonMain/kotlin/app/theia/domain/catalog/BuiltInEquipmentCatalog.kt`. Each body, lens, and film stock has a stable identifier, localization keys, original artwork metadata, and an unlocked state. Films belong to one of the eight named collections. IDs are persistent data: once released, do not rename or reuse them. Add a migration if an ID must change.

The catalog currently includes six camera bodies, eight lenses, and 32 film stocks. Every film is available from first launch. Catalog order is the authored browsing order and should remain deterministic.

## Add an item

1. Add an entry to the appropriate built-in catalog list. Use a lowercase, hyphenated slug and a globally unique domain ID (`body-…`, `lens-…`, or `film-…`).
2. Write an original name and concise visual description. Describe behavior users can observe; do not promise device-independent optical precision.
3. Add `name`, `description`, and `visual` strings to both Android catalog resource files: `androidApp/src/main/res/values/catalog.xml` and `androidApp/src/main/res/values-pt-rBR/catalog.xml`. Resource names use underscores in place of dots and hyphens. Add matching keys to iOS `Localizable.strings` files.
4. Set a stable `artworkKey`. The native renderers in `androidApp/.../catalog/CatalogArtworkCard.kt` and `iosApp/Théia/Features/Catalog/CatalogArtworkView.swift` create original 4:3 illustrations from the key. Provide localized accessibility text through the `visual` string.
5. For film, assign an existing collection key or add a `FilmCollection` with localized name and description keys. Keep the eight-collection minimum.
6. Add catalog integrity assertions in `BuiltInEquipmentCatalogTest` for counts, ID uniqueness, localization keys, artwork, and unlocked availability.

Catalog names and artwork must be fictional and independently authored. Do not imply affiliation with real camera, lens, or film manufacturers.

## Validate

From the repository root, run:

```sh
./gradlew :shared:testAndroidHostTest :androidApp:compileDebugKotlin
```

Build the iOS shell with a full Xcode installation:

```sh
xcodebuild -project 'iosApp/Théia.xcodeproj' -scheme Théia \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

Review both locales, VoiceOver/TalkBack labels, artwork aspect ratio, and light/dark contrast on platform devices before a release.

## Licensing and brand

Code is under PolyForm Noncommercial 1.0.0. Commercial use requires separate written authorization. The Théia name, logo, and brand assets are separately protected and are not licensed for independent use by the code license.
