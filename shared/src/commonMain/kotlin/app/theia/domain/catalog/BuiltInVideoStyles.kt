package app.theia.domain.catalog

import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.ResolvedVisualParameters
import app.theia.domain.model.VideoStylePreset

/** Original authored looks for video. Presets layer over, and retain, the selected equipment recipe. */
object BuiltInVideoStyles {
    const val EQUIPMENT_RECIPE_ID = "equipment-recipe"

    val presets: List<VideoStylePreset> = listOf(
        VideoStylePreset(
            id = "cinematic-warm",
            localizationKey = "video_style_cinematic_warm",
            contrastMultiplier = 1.08,
            saturationMultiplier = 0.92,
            warmthOffset = 0.16,
            highlightRollOffOffset = 0.12,
            shadowLiftOffset = 0.025,
            grainMultiplier = 0.72,
            halationMultiplier = 0.85,
            vignetteMultiplier = 1.08,
        ),
        VideoStylePreset(
            id = "moonlit-monochrome",
            localizationKey = "video_style_moonlit_monochrome",
            contrastMultiplier = 1.14,
            saturationMultiplier = 0.0,
            warmthOffset = -0.10,
            highlightRollOffOffset = 0.08,
            shadowLiftOffset = 0.045,
            grainMultiplier = 1.18,
            halationMultiplier = 0.72,
            vignetteMultiplier = 1.16,
        ),
    )

    /** Returns the exact shared equipment profile in recipe mode or a composed authored video look. */
    fun resolve(recipe: CaptureRecipe, styleId: String): ResolvedVisualParameters? {
        val recipeProfile = BuiltInFilmVisualProfiles.resolve(recipe) ?: return null
        if (styleId == EQUIPMENT_RECIPE_ID) return recipeProfile
        val preset = presets.firstOrNull { it.id == styleId } ?: return null
        return recipeProfile.copy(
            filmProfileId = "video-style-v1-${preset.id}+${recipeProfile.filmProfileId}",
            contrast = (recipeProfile.contrast * preset.contrastMultiplier).coerceIn(0.5, 2.0),
            saturation = (recipeProfile.saturation * preset.saturationMultiplier).coerceIn(0.0, 2.0),
            warmth = (recipeProfile.warmth + preset.warmthOffset).coerceIn(-1.0, 1.0),
            highlightRollOff = (recipeProfile.highlightRollOff + preset.highlightRollOffOffset).coerceIn(0.0, 1.0),
            shadowLift = (recipeProfile.shadowLift + preset.shadowLiftOffset).coerceIn(0.0, 0.45),
            grainIntensity = (recipeProfile.grainIntensity * preset.grainMultiplier).coerceIn(0.0, 1.0),
            halation = (recipeProfile.halation * preset.halationMultiplier).coerceIn(0.0, 1.0),
            vignette = (recipeProfile.vignette * preset.vignetteMultiplier).coerceIn(0.0, 0.8),
        )
    }
}
