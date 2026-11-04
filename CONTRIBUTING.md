# Contributing

Thanks for helping improve Théia. Start with the [product specification](docs/theia-product-spec.md), [architecture guide](docs/architecture.md), and the issue linked to your change.

## Development setup

- Android: install Android Studio/SDK and JDK 17, then use the checked-in Gradle wrapper.
- iOS: use Xcode and select the Xcode developer directory before building if Command Line Tools are active.
- The source tree and build instructions are in the [README](README.md).

## Change expectations

- Preserve stable IDs and versioned serialized payloads. Add compatibility tests when changing persistent models.
- Keep platform APIs out of `shared/src/commonMain`; use narrow shared ports and native adapters.
- Add both English and Brazilian Portuguese strings for user-facing copy. Keep resource identifiers consistent across Android and iOS.
- Request permissions only when a user opens the feature that needs them. Keep location disabled until explicit opt-in.
- Keep media processing and indexing local to the device. Do not add accounts, remote uploads, tracking, advertising, subscriptions, or in-app backup.
- Use original fictional catalog items and assets. Follow [Catalog Authoring](docs/catalog-authoring.md).
- Add or update common tests for domain behavior and native tests for adapter behavior. When an adapter requires physical hardware or an unavailable platform runtime, record the exact manual verification and keep its acceptance criteria open until that check is completed. Run focused checks while editing and the documented full checks before submitting.
- Use a Conventional Commit message, such as `feat(catalog): add evening film collection`.

## Pull request checklist

- [ ] The change matches an approved issue or product requirement.
- [ ] English and Brazilian Portuguese copy are present where needed.
- [ ] Accessibility labels, focus order, touch targets, and reduced-motion behavior were considered.
- [ ] Relevant automated checks pass, or the unavailable device/runtime is documented.
- [ ] No user media, credentials, build outputs, or commercial assets are committed.
- [ ] The description explains behavior, verification, and platform limitations.

## License and brand

Contributions to source code are under the repository's PolyForm Noncommercial License 1.0.0. Commercial use requires separate authorization from the licensor. The Théia name, logo, and brand assets are separately protected; contribution or code access does not grant independent brand rights.
