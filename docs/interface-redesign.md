# Théia Interface Redesign

**Status:** Accepted; Android first pass implemented and reviewed on device
**Decision date:** 2026-11-05
**Reference device:** Motorola Edge 60 (`motorola_edge_60`)
**Reference app:** Moto Camera (`com.motorola.camera5`, version `10.0.15.58`)

## Purpose

Make the primary journey easy to understand, reduce controls that compete with the live camera preview, and keep recipe selection, capture, rolls, development, and browsing connected. Use the Motorola camera app as a source of interaction and layout patterns while keeping Théia's own visual identity and native platform interfaces.

## User decisions

- Redesign Android first, then adapt the agreed patterns to iOS.
- Cover the Android app as a whole, beginning with the camera and recipe journey.
- Open directly to the camera after onboarding is complete; the default recipe is ready to use.
- Keep the live preview as the primary surface and compress secondary navigation.
- Open recipe editing in a bottom sheet. Let the user compare body, lens, and film selections, see the live preview respond, then Apply or Cancel.
- Keep photo and video in the camera mode selector.
- Reach rolls and the album together from one compact camera action. Keep settings available through a compact action.
- Preserve Théia's palette, typography, catalog illustrations, and tactile character. Reuse the reference app's hierarchy and spacing ideas, not its artwork or branding.
- A roll has one fixed recipe. When a roll contains exposures, changing to another recipe asks before starting a new roll; the existing roll stays open and resumable. A recipe change without captured exposures is immediate.

## Reference evidence and limits

The connected device inventory identified a physical Motorola Edge 60. The installed camera package was read as version `10.0.15.58` (version code `10001558`). Evidence IDs from the read-only REA observations:

- Device inventory: `ev_2212b4cfb325314bc50442c64d1449e8bc450c40067501c09fea3a7f7390f263`.
- Installed package details: `ev_2cf447c9d3895a0939d5a701b03d1db250ac7aa554c87fcc47c0372ef39a5d22` (package list) and `ev_b24b1036d6ca7c4022458ac0c91778a625c182993fa00a5ca724b13de9dd9997` (launch).
- Camera package version details: `ev_6bbf77fcf82bd24bac5fb8649ff2936677cb94213194d236f8537d22cfd61df0`.
- Captured default camera screen: `ev_d15c7fc2467cf2e7d40c6060458ab8c3d0b9ab4d2ac02bfa23112ec306df1567`, 1220 × 2712 px.

The observed screen uses a large preview, compact icon controls at the top, lens/zoom choices near the preview edge, a photo/video mode strip, a prominent shutter, a recent-photo thumbnail, and camera switching. This was one static frame after launching the app. The other modes and their behavior were not inspected. The redesign therefore borrows the visible hierarchy and treats further Motorola behavior as unknown.

## Information architecture

```text
Camera (default)
├── Recipe sheet (temporary, returns to camera)
├── Photo mode
├── Video mode (same camera context)
├── Rolls and album (one destination)
└── Settings
```

The camera stays the home destination. Workbench is an in-context recipe editor instead of a separate top-level destination. The album screen keeps the roll panel so users can find open rolls, resume one, and develop it without a separate navigation branch.

## Primary journeys

### First use and quick photo

1. Show the short, skippable onboarding once.
2. Continue to the camera with a usable default recipe; do not require equipment setup first.
3. Request camera permission when the user enters camera capture.
4. Keep the preview, shutter, camera switch, and remaining exposure count visible.
5. After capture, show the latest visible capture and update the active roll count. Originals remain in the system gallery immediately; undeveloped roll images remain hidden in Théia's album.

### Change the recipe

1. Tap the compact current-recipe control above the preview.
2. The bottom sheet presents Body, Lens, and Film categories and horizontally browsable catalog choices.
3. Changing a choice updates the preview behind the sheet, while the current recipe remains uncommitted.
4. Apply commits the new combination and returns to the camera. Cancel or dismiss restores the previously applied recipe.
5. If the active roll contains exposures, show a confirmation with the number of exposures. Confirm starts a separate 24-exposure roll and leaves the prior roll open; cancel keeps the prior recipe active.
6. If the current roll has no exposures, switch directly to a new roll using the selected recipe.

### Record video

1. Choose Video from the camera mode selector.
2. Keep the selected recipe visible in context and request microphone access only when audio is enabled.
3. Return to Photo mode through the camera's back action. Video does not consume roll exposures.

### Review and develop

1. Open the combined Rolls/Album destination using the compact camera action.
2. Find rolls and developed media in one place; roll cards explain recipe, capacity, count, and development state.
3. Resume an eligible open roll or explicitly develop it. Early development remains an intentional action.
4. Inspect, favorite, search, share, and delete developed album items using the existing confirmation and privacy behavior.

### Settings and permission recovery

Settings stay secondary to capture. Keep privacy preferences, language, and permission recovery grouped under clear headings; request each permission in the feature that needs it, and preserve access to the rest of the app when one permission is denied.

## Screen patterns

### Camera

- Compact header: current recipe summary, Rolls/Album, and Settings.
- Large preview with only capture-critical overlays.
- Keep focus, zoom, exposure, flash, timer, and grid available without forcing every secondary control into a full-width text row.
- Keep exposure adjustment collapsed by default and remove the static capture title from the preview; show transient capture, focus, timer, and roll feedback only when relevant.
- Put Photo/Video mode selection near the shutter, with the active mode clearly marked.
- Keep the shutter as the strongest action, the latest visible photo as a thumbnail/action, and the remaining exposure count near the preview.
- Respect screen-reader labels, 48 dp minimum touch targets for icon actions, text scaling, contrast, and reduced-motion preferences.

### Recipe sheet

- Three stable categories: camera body, lens, and film stock.
- A compact selected-item summary with original Théia catalog art and description.
- A horizontal item list so a user can scan alternatives without a tall stack of full-size cards.
- A concise summary of the whole recipe and a persistent Apply action; Cancel remains available.
- Draft selection updates the camera preview but is rolled back if the sheet is dismissed.

### Rolls and album

- Keep roll status and actions adjacent to the media list.
- Preserve search, filters, grouping, favorites, metadata, geotag controls, viewing, sharing, and explicit deletion scope.
- Keep undeveloped media hidden in Théia until its roll or individual exposure is developed, in line with the product specification.

### Settings and onboarding

- Use a compact, consistent page inset and section spacing while retaining existing explanatory copy and accessible controls.
- Onboarding remains skippable and does not request permissions.

## Visual and interaction rules

1. One primary action per view; secondary actions use concise labels or accessible icon controls.
2. Prefer contextual sheets for short, reversible choices that should not break the capture flow.
3. Reserve full screens for sustained tasks such as browsing the album, configuring settings, or recording video.
4. Keep the active recipe, roll, mode, and development status explicit where a user needs them to make the next decision.
5. Use spacing to group related actions; do not use decorative containers around every control.
6. Keep the existing palette and high-contrast text pairing: solar gold `#EAAE35`, ivory `#E7D7B7`, deep sky `#182344`, cosmic violet `#2D2D57`, and stone `#B6A58C`.
7. Keep all UI strings localized in English and Brazilian Portuguese.

## Android implementation map

- `MainActivity.kt`: camera-first destination, compact entry actions, in-context recipe sheet, and guarded recipe/roll transition.
- `RecipePickerSheet.kt`: draft recipe selection and live preview update with Apply/Cancel.
- `CameraCaptureScreen.kt` and `FilmPreviewEffect.kt`: compact Photo/Video mode control, transient preview status, and recipe updates in the active GPU preview.
- `AlbumLabScreen.kt`: combined album and roll destination, with tighter page spacing.
- `SettingsScreen.kt`: tighter page/section spacing while preserving permission recovery and preferences.
- Existing capture, roll, album, permission, and geotag domain behavior remains the source of truth.

## iOS follow-up

The same information architecture should be implemented in SwiftUI after Android interaction details settle: camera as the first destination, a native presentation for recipe editing, an explicit new-roll confirmation, Photo/Video as camera modes, and one entry to rolls plus album. Preserve the existing iOS capture stack, Photos integration, local metadata, privacy rules, and accessibility behavior. Do not share UI components across platforms.

## Acceptance checklist

- A returning user opens directly to the camera and can capture without visiting the recipe editor.
- The camera has no four-button full-text strip or permanent capture title over the preview.
- Body/lens/film changes update the preview while the sheet is open; Apply commits, and Cancel/backdrop dismissal restores the prior recipe.
- A populated active roll is never silently displaced by a different recipe; confirmation identifies the exposure count and preserves the old roll as resumable.
- Photo and Video are visible as camera modes; Rolls/Album and Settings remain reachable with accessible compact actions.
- Album and settings page insets and section gaps are reduced without removing existing actions or privacy explanations.
- Controls and text remain usable with screen readers, at 48 dp minimum touch size for compact navigation and mode/category controls, with larger text, and under reduced-motion settings.
- English and Brazilian Portuguese have matching strings, including singular/plural exposure-count copy.

## Validation status

The Motorola reference was captured once on the connected Edge 60. The Android first pass is implemented in source and localized. `./gradlew :androidApp:assembleDebug` completed successfully and the resulting debug APK was installed over the existing app on the Edge 60 (`0091239821`) with app data preserved.

Manual on-device review confirmed that the live camera preview starts after camera permission is granted, the compact camera-controls panel expands and collapses, and the Photo-to-Video transition opens the video setup screen. The recipe sheet was also opened and reviewed on-device earlier in the same session. The app remained running as `app.theia` and the inspected logcat window had no crash entries. No photo was captured and no video recording was started. The expanded controls remain scrollable; the device's large text scale makes some long horizontal chip labels require scrolling.

Reference capture evidence: `ev_d15c7fc2467cf2e7d40c6060458ab8c3d0b9ab4d2ac02bfa23112ec306df1567`. Final app launch: `ev_bd22fc179cd3a14e23313ba75abd700972148c18bc1febd1412d53d6adfa2e03`. Final collapsed camera frame: `ev_4ccb55d3a46a3d0a9de799af6df3a82e6e7654351f5a3498dfe1a67a658c6e05`. Expanded controls: `ev_362c422f3d7c05b425a60aa2a69e9a34ef9fe15168ea560ec0f5d6650323d1d4`. Video setup: `ev_49f1cbed876a766560066cf676b6d46b661472cfbe347bd3d2024081bb2560e5`. Installed APK SHA-256: `dc44c4cb6a2e2ab55067cfe31b00be343358136455ed99200a88096293f60d24`.

## Deferred questions

- The Motorola app's other camera modes and settings were not inspected; no behavior beyond the captured Photo screen is assumed.
- Whether to change the initial roll capacity from 24 exposures is out of scope; retain current behavior until a separate product decision is made.
- Detailed visual layout for landscape, tablets, and foldables needs a later device-specific pass.
