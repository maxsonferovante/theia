package app.theia.domain.catalog

import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.CameraBodyVisualAdjustment
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.FilmVisualProfile
import app.theia.domain.model.LensId
import app.theia.domain.model.LensVisualAdjustment
import app.theia.domain.model.ResolvedVisualParameters

/** Stable rendering profile data. Increment a profile's version when its rendering values change. */
object BuiltInFilmVisualProfiles {
    const val SCHEMA_VERSION = 1

    val films: List<FilmVisualProfile> = listOf(
        film("apricot-noon", 0.05, 1.02, 1.12, 0.34, 0.02, 0.40, 0.02, 0.18, 0.36, 0.10, 0.10, 0.08),
        film("wheatfield", 0.00, 1.08, 1.05, 0.28, -0.08, 0.34, 0.04, 0.16, 0.42, 0.08, 0.06, 0.04),
        film("glasshouse", 0.00, 0.98, 0.98, -0.16, -0.04, 0.48, 0.05, 0.10, 0.32, 0.04, 0.02, 0.05),
        film("citrus-paper", 0.04, 0.88, 1.18, 0.22, 0.00, 0.30, 0.08, 0.12, 0.48, 0.06, 0.04, 0.06),
        film("porcelain-hour", 0.03, 0.92, 0.94, 0.16, 0.08, 0.58, 0.06, 0.08, 0.28, 0.12, 0.06, 0.10),
        film("rose-window", 0.00, 1.04, 1.12, 0.04, 0.18, 0.44, 0.02, 0.08, 0.30, 0.18, 0.08, 0.08),
        film("velvet-light", -0.04, 1.12, 0.96, 0.14, 0.10, 0.42, 0.03, 0.06, 0.36, 0.14, 0.05, 0.08),
        film("pearl-garden", 0.02, 0.90, 1.02, 0.04, 0.10, 0.50, 0.08, 0.07, 0.34, 0.16, 0.06, 0.12),
        film("silvergrain", 0.00, 1.12, 0.00, 0.00, 0.00, 0.42, 0.02, 0.52, 0.56, 0.00, 0.04, 0.00),
        film("ash-archive", -0.02, 1.06, 0.00, 0.12, 0.00, 0.38, 0.08, 0.44, 0.48, 0.00, 0.06, 0.00),
        film("blue-hour-negative", 0.00, 1.04, 0.00, -0.20, -0.02, 0.52, 0.02, 0.34, 0.42, 0.00, 0.08, 0.00),
        film("tungsten-noir", -0.08, 1.36, 0.00, 0.04, 0.00, 0.62, 0.00, 0.24, 0.38, 0.03, 0.08, 0.00),
        film("copper-gate", 0.00, 1.12, 1.08, 0.18, -0.10, 0.46, 0.02, 0.12, 0.38, 0.12, 0.08, 0.08),
        film("faded-reel", 0.06, 0.84, 0.82, 0.18, 0.08, 0.28, 0.12, 0.18, 0.44, 0.08, 0.12, 0.08),
        film("neon-frame", -0.02, 1.22, 1.28, -0.04, 0.04, 0.46, 0.02, 0.10, 0.40, 0.24, 0.10, 0.18),
        film("last-screening", -0.06, 1.12, 0.78, 0.18, 0.06, 0.48, 0.02, 0.16, 0.44, 0.12, 0.08, 0.12),
        film("pocket-sun", 0.08, 0.90, 1.16, 0.24, 0.00, 0.34, 0.12, 0.28, 0.54, 0.08, 0.06, 0.08),
        film("sugar-flash", 0.10, 0.96, 1.08, 0.04, 0.10, 0.54, 0.10, 0.20, 0.48, 0.18, 0.04, 0.16),
        film("sunday-print", 0.04, 0.88, 0.98, 0.22, 0.04, 0.32, 0.12, 0.20, 0.50, 0.06, 0.04, 0.06),
        film("blue-envelope", 0.02, 0.94, 0.92, -0.08, 0.10, 0.42, 0.06, 0.12, 0.40, 0.08, 0.05, 0.08),
        film("starling", -0.06, 1.16, 1.06, -0.18, -0.02, 0.58, 0.02, 0.12, 0.34, 0.20, 0.12, 0.18),
        film("cobalt-afterdark", -0.04, 1.10, 1.00, -0.14, 0.10, 0.52, 0.03, 0.10, 0.36, 0.14, 0.08, 0.10),
        film("sodium-glow", -0.02, 1.02, 0.96, 0.32, 0.04, 0.46, 0.04, 0.08, 0.34, 0.18, 0.10, 0.10),
        film("moonlit-120", -0.04, 0.98, 0.90, -0.22, 0.04, 0.56, 0.04, 0.42, 0.60, 0.12, 0.08, 0.08),
        film("atlas-color", 0.00, 1.04, 1.02, 0.08, -0.02, 0.44, 0.02, 0.12, 0.38, 0.04, 0.04, 0.04),
        film("coastal-air", 0.03, 0.96, 0.98, -0.08, -0.04, 0.46, 0.04, 0.08, 0.34, 0.04, 0.03, 0.04),
        film("old-station", -0.02, 0.92, 0.88, 0.04, -0.06, 0.34, 0.10, 0.20, 0.46, 0.05, 0.06, 0.04),
        film("red-earth", 0.00, 1.08, 1.02, 0.16, -0.06, 0.40, 0.04, 0.14, 0.40, 0.06, 0.05, 0.04),
        film("prism-bleach", 0.02, 1.26, 1.34, -0.04, 0.16, 0.66, 0.00, 0.08, 0.30, 0.10, 0.02, 0.02),
        film("violet-haze", 0.04, 0.90, 1.04, 0.12, 0.16, 0.38, 0.08, 0.06, 0.36, 0.24, 0.08, 0.18),
        film("solarized", 0.00, 1.30, 1.06, 0.00, 0.00, 0.78, 0.00, 0.06, 0.28, 0.00, 0.02, 0.00),
        film("paper-moon", 0.02, 0.94, 0.86, -0.08, 0.04, 0.38, 0.10, 0.16, 0.48, 0.06, 0.04, 0.06),
    )

    val cameraBodies: List<CameraBodyVisualAdjustment> = listOf(
        CameraBodyVisualAdjustment(CameraBodyId("body-helia"), exposureBiasEv = 0.03, contrastMultiplier = 0.96, warmth = 0.10, highlightProtection = 0.12),
        CameraBodyVisualAdjustment(CameraBodyId("body-celeste"), contrastMultiplier = 1.00, saturationMultiplier = 1.02, shadowLift = 0.02),
        CameraBodyVisualAdjustment(CameraBodyId("body-nocturne"), exposureBiasEv = -0.04, contrastMultiplier = 1.04, highlightProtection = 0.22),
        CameraBodyVisualAdjustment(CameraBodyId("body-terra"), contrastMultiplier = 1.02, warmth = 0.08, saturationMultiplier = 1.02),
        CameraBodyVisualAdjustment(CameraBodyId("body-ivory"), contrastMultiplier = 0.94, warmth = 0.03, highlightProtection = 0.08, shadowLift = 0.02),
        CameraBodyVisualAdjustment(CameraBodyId("body-orbit"), contrastMultiplier = 1.06, saturationMultiplier = 0.96, warmth = -0.06),
    )

    val lenses: List<LensVisualAdjustment> = listOf(
        LensVisualAdjustment(LensId("lens-aurora"), edgeSoftness = 0.06, halation = 0.08, bloom = 0.06, sharpness = 0.96),
        LensVisualAdjustment(LensId("lens-portrait"), edgeSoftness = 0.10, vignette = 0.05, sharpness = 0.94),
        LensVisualAdjustment(LensId("lens-macro"), sharpness = 1.12),
        LensVisualAdjustment(LensId("lens-halo"), halation = 0.14, bloom = 0.10, sharpness = 0.98),
        LensVisualAdjustment(LensId("lens-wide"), vignette = 0.06, distortion = -0.04, sharpness = 0.98),
        LensVisualAdjustment(LensId("lens-tele"), edgeSoftness = 0.02, vignette = 0.02, distortion = 0.02, sharpness = 1.04),
        LensVisualAdjustment(LensId("lens-soft-focus"), edgeSoftness = 0.28, halation = 0.16, bloom = 0.18, sharpness = 0.78),
        LensVisualAdjustment(LensId("lens-tilt"), edgeSoftness = 0.12, vignette = 0.08, distortion = 0.04, sharpness = 0.92),
    )

    fun film(filmStockId: FilmStockId): FilmVisualProfile? = films.firstOrNull { it.filmStockId == filmStockId }
    fun cameraBody(id: CameraBodyId): CameraBodyVisualAdjustment? = cameraBodies.firstOrNull { it.cameraBodyId == id }
    fun lens(id: LensId): LensVisualAdjustment? = lenses.firstOrNull { it.lensId == id }

    fun resolve(recipe: CaptureRecipe): ResolvedVisualParameters? {
        val film = film(recipe.filmStockId) ?: return null
        val body = cameraBody(recipe.bodyId) ?: return null
        val lens = lens(recipe.lensId) ?: return null
        return ResolvedVisualParameters(
            schemaVersion = SCHEMA_VERSION,
            filmProfileId = film.profileId,
            filmProfileVersion = film.profileVersion,
            filmStockId = recipe.filmStockId,
            cameraBodyId = recipe.bodyId,
            lensId = recipe.lensId,
            exposureBiasEv = (film.exposureBiasEv + body.exposureBiasEv).coerceIn(-1.5, 1.5),
            contrast = (film.contrast * body.contrastMultiplier).coerceIn(0.5, 2.0),
            saturation = (film.saturation * body.saturationMultiplier).coerceIn(0.0, 2.0),
            warmth = (film.warmth + body.warmth).coerceIn(-1.0, 1.0),
            greenMagenta = film.greenMagenta,
            highlightRollOff = (film.highlightRollOff + body.highlightProtection).coerceAtMost(1.0),
            shadowLift = (film.shadowLift + body.shadowLift).coerceAtMost(0.45),
            grainIntensity = film.grainIntensity,
            grainScale = film.grainScale,
            halation = (film.halation + lens.halation).coerceAtMost(1.0),
            vignette = (film.vignette + lens.vignette).coerceAtMost(0.8),
            bloom = (film.bloom + lens.bloom).coerceAtMost(1.0),
            edgeSoftness = lens.edgeSoftness,
            distortion = lens.distortion,
            sharpness = lens.sharpness,
        )
    }

    private fun film(
        slug: String,
        exposure: Double,
        contrast: Double,
        saturation: Double,
        warmth: Double,
        greenMagenta: Double,
        highlight: Double,
        shadow: Double,
        grain: Double,
        grainScale: Double,
        halation: Double,
        vignette: Double,
        bloom: Double,
    ) = FilmVisualProfile(
        profileId = "film-profile-v1-$slug",
        profileVersion = 1,
        filmStockId = FilmStockId("film-$slug"),
        exposureBiasEv = exposure,
        contrast = contrast,
        saturation = saturation,
        warmth = warmth,
        greenMagenta = greenMagenta,
        highlightRollOff = highlight,
        shadowLift = shadow,
        grainIntensity = grain,
        grainScale = grainScale,
        halation = halation,
        vignette = vignette,
        bloom = bloom,
    )
}
