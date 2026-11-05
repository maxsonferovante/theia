# ADR 0001: Camera-first navigation and in-context recipe editing

- **Status:** Accepted
- **Date:** 2026-11-05
- **Decision owners:** Product owner and implementation team
- **Related:** [Interface redesign](../interface-redesign.md)

## Context

People found the existing screens difficult to use, the main journey unclear, and the camera interface crowded. The Android app placed four full-text destinations above the camera preview and sent equipment selection to a long, separate Workbench screen. A Motorola Edge 60 was available as a concrete camera-app reference. Its Moto Camera Photo screen gave most of the display to the preview, used compact controls, and placed capture modes near the shutter.

Théia has a product-specific constraint: each film roll stores one immutable camera body, lens, and film recipe. A recipe change while exposures are still on the active roll must not silently mix recipes or abandon user work.

## Decision

1. Make the Android camera the default destination after optional onboarding.
2. Replace the camera's full-text action strip with a compact recipe summary and concise Rolls/Album and Settings actions.
3. Edit recipes in a bottom sheet with body, lens, and film categories. Draft choices update the live preview; Apply commits them and Cancel/dismiss restores the previous recipe.
4. Keep Photo and Video together in the camera mode selector.
5. Keep rolls and the local album reachable from one destination. Keep settings secondary to the capture journey.
6. When an active roll contains photos, confirm before a different recipe starts a separate roll. Explain that the old roll stays open and can be resumed. Do not interrupt for an empty roll.
7. Preserve Théia's palette, localized content, accessibility semantics, local-first media behavior, and platform-native UI implementations.
8. Implement Android first, then carry the settled navigation and journey into native SwiftUI.

## Alternatives considered

- **Keep the Workbench as a top-level full screen:** rejected because it breaks the camera journey for a short recipe choice and uses a tall stacked layout.
- **Put all destinations in a permanent bottom navigation bar:** rejected for the camera because it would reserve screen height while the preview and capture controls already need vertical space.
- **Allow recipes to vary within one roll:** rejected because the domain model and developed output require one fixed recipe per roll.
- **Copy Motorola's colors and artwork:** rejected; the reference informs hierarchy and interaction only, while Théia keeps its own identity.

## Consequences

### Positive

- Users can take a photo immediately without first traversing equipment setup.
- The recipe remains visible and editable without leaving camera context.
- The camera receives more room by removing four large text buttons and a duplicate latest-photo banner.
- Recipe changes clearly communicate roll continuity and preserve the existing open roll.
- Android and iOS can share the information architecture without sharing UI components.

### Costs and follow-up

- Android camera mode switching becomes a first-class action and needs a clear active-mode state.
- The recipe sheet must preserve the currently applied selection when canceled and update the active GPU effect as its draft changes.
- The roll-switch confirmation needs localized copy and must be accessible at larger text sizes.
- iOS navigation is explicitly subsequent work and should be recorded against this ADR when implemented.
- The Motorola comparison is based on one captured Photo screen; other Moto Camera modes remain unobserved.
