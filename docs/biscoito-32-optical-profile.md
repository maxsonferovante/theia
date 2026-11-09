# Biscoito 32 optical profile

This is an original, fictional rendering profile for Théia. It takes broad visual cues from simple disposable-camera optics; it does not claim to reproduce a physical lens or represent an affiliation with a camera or lens maker.

## Identity and compatibility

- Lens ID: `lens-biscoito-32`
- Lens profile version: `1`
- Rendering algorithm ID: `theia.visual-pipeline`
- Rendering schema version: `1`
- New optical controls use normalized values, independent of sensor pixel count. Their sampling distances are calculated from the shorter side of the rendered frame.
- Older recipes that omit the new fields decode with lens profile version 1 and rendering schema version 1. New optical adjustment fields default to zero, preserving existing lens behavior.
- The resolver selects a lens adjustment by both stable lens ID and profile version. Rendering schema versions are explicitly registered; each renderer dispatches to its version-specific implementation and rejects an unsupported version rather than rendering it with unrelated behavior. Keep schema 1 registered and unchanged for existing rolls when adding a future schema, then assign the new schema to new recipes.

## Version 1 values

The lens contributes vignette `0.28`, radial softness `0.45`, chromatic aberration `0.002`, distortion `0.04`, and optical flare `0.16`. The selected film can add its own vignette; the resolved combined amount is capped at `0.8`. These are starting artistic settings, not measured optical properties. Physical calibration is still pending on both platforms.

For normalized output coordinates `(u, v)`, let `c = (u - 0.5, v - 0.5)` and `r² = 4 · dot(c, c)`. The frame center has `r² = 0`; corners approach `r² = 2`. Coordinates refer to the rendered frame after orientation and crop, so the effect does not use sensor pixels or assume a particular aspect ratio.

| Control | Meaning and version 1 behavior | Supported range |
| --- | --- | --- |
| Vignette | Multiply each color channel by `1 - amount · smoothstep(0.18, 0.75, r²)`. At the center it is neutral; it reaches the selected strength toward the frame corners. | `0...0.8` after film and lens values are combined |
| Radial softness | Blend the center sample with the mean of four samples on the horizontal and vertical axes. Offset radius is `0.006 · minSide · softness · min(r², 1)` pixels; blend amount is `clamp(softness · r², 0, 0.65)`. | `0...1` |
| Chromatic aberration | Sample red at `c + c · r² · amount` and blue at `c - c · r² · amount`; green stays at the base sample. The opposite radial shifts create color fringes that grow toward the edges. | `0...0.01` of normalized frame coordinates |
| Distortion | Sample at `(c · (1 + r² · amount)) + (0.5, 0.5)`. Positive values move source samples outward; negative values move them inward. Edge sampling clamps to the image. | `-0.15...0.15` |
| Optical flare | Average four nearby samples offset by `0.015 · minSide` pixels. Each color channel adds up to `0.098 · amount` when its sampled value rises above `0.745`; dark scenes receive no added veil. It does not reconstruct lights outside the frame. | `0...1` |

All sample coordinates are clamped to the source image to avoid transparent borders. The Android preview and still renderer use the same vignette curve; iOS uses that normalized curve for the live preview, photo, and video pipeline. Texture interpolation differs between GPU and CPU paths, so pixel-identical results are not expected. Visual parity still needs comparison using the calibration scenes and physical devices listed in issue #20; see [partial validation notes](performance-validation.md#biscoito-32--partial-validation--2026-11-08).
