package app.theia.domain.model

import kotlinx.serialization.Serializable

/** Versioned, platform-neutral rendering values. All ratios/biases use documented normalized ranges. */
@Serializable
data class FilmVisualProfile(
    val profileId: String,
    val profileVersion: Int,
    val filmStockId: FilmStockId,
    val exposureBiasEv: Double,
    val contrast: Double,
    val saturation: Double,
    val warmth: Double,
    val greenMagenta: Double,
    val highlightRollOff: Double,
    val shadowLift: Double,
    val grainIntensity: Double,
    val grainScale: Double,
    val halation: Double,
    val vignette: Double,
    val bloom: Double,
) {
    init {
        require(profileId.isNotBlank()) { "Visual profile IDs cannot be blank" }
        require(profileVersion > 0) { "Visual profile versions must be positive" }
        require(exposureBiasEv in -1.0..1.0) { "Film exposure bias must be between -1 and 1 EV" }
        require(contrast in 0.5..1.8) { "Film contrast must be between 0.5 and 1.8" }
        require(saturation in 0.0..1.8) { "Film saturation must be between 0 and 1.8" }
        require(warmth in -1.0..1.0 && greenMagenta in -1.0..1.0) { "Color biases must be normalized" }
        require(highlightRollOff in 0.0..1.0) { "Highlight roll-off must be normalized" }
        require(shadowLift in 0.0..0.3) { "Shadow lift must be between 0 and 0.3" }
        require(grainIntensity in 0.0..1.0 && grainScale in 0.0..1.0) { "Grain values must be normalized" }
        require(halation in 0.0..1.0 && vignette in 0.0..0.6 && bloom in 0.0..0.8) {
            "Optical effects are outside supported ranges"
        }
    }
}

@Serializable
data class CameraBodyVisualAdjustment(
    val cameraBodyId: CameraBodyId,
    val exposureBiasEv: Double = 0.0,
    val contrastMultiplier: Double = 1.0,
    val saturationMultiplier: Double = 1.0,
    val warmth: Double = 0.0,
    val highlightProtection: Double = 0.0,
    val shadowLift: Double = 0.0,
) {
    init {
        require(exposureBiasEv in -0.5..0.5) { "Body exposure adjustment must be between -0.5 and 0.5 EV" }
        require(contrastMultiplier in 0.8..1.2 && saturationMultiplier in 0.8..1.2) {
            "Body tone multipliers must be between 0.8 and 1.2"
        }
        require(warmth in -0.25..0.25) { "Body warmth adjustment must be between -0.25 and 0.25" }
        require(highlightProtection in 0.0..0.3 && shadowLift in 0.0..0.15) {
            "Body tonal adjustments are outside supported ranges"
        }
    }
}

@Serializable
data class LensVisualAdjustment(
    val lensId: LensId,
    val edgeSoftness: Double = 0.0,
    val halation: Double = 0.0,
    val vignette: Double = 0.0,
    val bloom: Double = 0.0,
    val distortion: Double = 0.0,
    val sharpness: Double = 1.0,
    // Optical profile values are normalized to the rendered image, not sensor pixels.
    val profileVersion: Int = 1,
    val radialSoftness: Double = 0.0,
    val chromaticAberration: Double = 0.0,
    val opticalFlare: Double = 0.0,
) {
    init {
        require(edgeSoftness in 0.0..0.5 && halation in 0.0..0.5) { "Lens softness and halation must be between 0 and 0.5" }
        require(vignette in 0.0..0.4 && bloom in 0.0..0.4) { "Lens vignette and bloom must be between 0 and 0.4" }
        require(distortion in -0.15..0.15) { "Lens distortion must be between -0.15 and 0.15" }
        require(sharpness in 0.5..1.5) { "Lens sharpness must be between 0.5 and 1.5" }
        require(profileVersion > 0 && radialSoftness in 0.0..1.0 && chromaticAberration in 0.0..0.01 && opticalFlare in 0.0..1.0) { "Lens optical profile is outside supported ranges" }
    }
}

/** Fully composed parameters shared by native preview, still-photo, and video renderers. */
@Serializable
data class ResolvedVisualParameters(
    val schemaVersion: Int,
    val filmProfileId: String,
    val filmProfileVersion: Int,
    val filmStockId: FilmStockId,
    val cameraBodyId: CameraBodyId,
    val lensId: LensId,
    val exposureBiasEv: Double,
    val contrast: Double,
    val saturation: Double,
    val warmth: Double,
    val greenMagenta: Double,
    val highlightRollOff: Double,
    val shadowLift: Double,
    val grainIntensity: Double,
    val grainScale: Double,
    val halation: Double,
    val vignette: Double,
    val bloom: Double,
    val edgeSoftness: Double,
    val distortion: Double,
    val sharpness: Double,
    val lensProfileVersion: Int = 1,
    val radialSoftness: Double = 0.0,
    val chromaticAberration: Double = 0.0,
    val opticalFlare: Double = 0.0,
) {
    init {
        require(schemaVersion > 0 && filmProfileVersion > 0) { "Rendering schema and profile versions must be positive" }
        require(filmProfileId.isNotBlank()) { "Resolved film profile IDs cannot be blank" }
        require(exposureBiasEv in -1.5..1.5) { "Resolved exposure bias must be between -1.5 and 1.5 EV" }
        require(contrast in 0.5..2.0 && saturation in 0.0..2.0) { "Resolved tone values are outside supported ranges" }
        require(warmth in -1.0..1.0 && greenMagenta in -1.0..1.0) { "Resolved color biases must be normalized" }
        require(highlightRollOff in 0.0..1.0 && shadowLift in 0.0..0.45) { "Resolved tonal curve values are outside supported ranges" }
        require(grainIntensity in 0.0..1.0 && grainScale in 0.0..1.0) { "Resolved grain values must be normalized" }
        require(halation in 0.0..1.0 && vignette in 0.0..0.8 && bloom in 0.0..1.0) {
            "Resolved optical effects are outside supported ranges"
        }
        require(edgeSoftness in 0.0..0.5 && distortion in -0.15..0.15 && sharpness in 0.5..1.5) {
            "Resolved lens values are outside supported ranges"
        }
        require(lensProfileVersion > 0 && radialSoftness in 0.0..1.0 && chromaticAberration in 0.0..0.01 && opticalFlare in 0.0..1.0) { "Resolved optical profile is outside supported ranges" }
    }
}
