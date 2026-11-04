package app.theia.domain.model

/** Stable style choices used by video capture. Equipment-recipe mode is always available. */
data class VideoStylePreset(
    val id: String,
    val localizationKey: String,
    val contrastMultiplier: Double,
    val saturationMultiplier: Double,
    val warmthOffset: Double,
    val highlightRollOffOffset: Double,
    val shadowLiftOffset: Double,
    val grainMultiplier: Double,
    val halationMultiplier: Double,
    val vignetteMultiplier: Double,
) {
    init {
        require(id.isNotBlank() && localizationKey.isNotBlank())
        require(contrastMultiplier in 0.5..2.0 && saturationMultiplier in 0.0..2.0)
        require(warmthOffset in -1.0..1.0)
        require(highlightRollOffOffset in -1.0..1.0 && shadowLiftOffset in -0.45..0.45)
        require(grainMultiplier in 0.0..2.0 && halationMultiplier in 0.0..2.0 && vignetteMultiplier in 0.0..2.0)
    }
}
