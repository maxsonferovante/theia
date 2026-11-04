package app.theia.domain.catalog

import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.LensId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuiltInFilmVisualProfilesTest {
    @Test
    fun everyBuiltInFilmHasOneStableVersionedProfile() {
        val catalogIds = BuiltInEquipmentCatalog.filmStocks().map { it.id }.toSet()
        val profiles = BuiltInFilmVisualProfiles.films

        assertEquals(32, catalogIds.size)
        assertEquals(catalogIds, profiles.map { it.filmStockId }.toSet())
        assertEquals(profiles.size, profiles.map { it.filmStockId }.toSet().size)
        assertEquals(profiles.size, profiles.map { it.profileId }.toSet().size)
        assertTrue(profiles.all { it.profileVersion == 1 && it.profileId.startsWith("film-profile-v1-") })
    }

    @Test
    fun profileAndAdjustmentValuesStayWithinNativeAdapterRanges() {
        BuiltInFilmVisualProfiles.films.forEach { profile ->
            assertTrue(profile.exposureBiasEv in -1.0..1.0)
            assertTrue(profile.contrast in 0.5..1.8)
            assertTrue(profile.saturation in 0.0..1.8)
            assertTrue(profile.warmth in -1.0..1.0)
            assertTrue(profile.greenMagenta in -1.0..1.0)
            assertTrue(profile.highlightRollOff in 0.0..1.0)
            assertTrue(profile.shadowLift in 0.0..0.3)
            assertTrue(profile.grainIntensity in 0.0..1.0)
            assertTrue(profile.grainScale in 0.0..1.0)
            assertTrue(profile.halation in 0.0..1.0)
            assertTrue(profile.vignette in 0.0..0.6)
            assertTrue(profile.bloom in 0.0..0.8)
        }
        assertEquals(BuiltInEquipmentCatalog.cameraBodies().map { it.id }.toSet(), BuiltInFilmVisualProfiles.cameraBodies.map { it.cameraBodyId }.toSet())
        assertEquals(BuiltInEquipmentCatalog.lenses().map { it.id }.toSet(), BuiltInFilmVisualProfiles.lenses.map { it.lensId }.toSet())
    }

    @Test
    fun resolverIsDeterministicAndComposesFilmBodyAndLensValues() {
        val recipe = CaptureRecipe(
            bodyId = CameraBodyId("body-helia"),
            lensId = LensId("lens-soft-focus"),
            filmStockId = FilmStockId("film-apricot-noon"),
        )

        val first = assertNotNull(BuiltInFilmVisualProfiles.resolve(recipe))
        val second = assertNotNull(BuiltInFilmVisualProfiles.resolve(recipe))

        assertEquals(first, second)
        assertEquals(BuiltInFilmVisualProfiles.SCHEMA_VERSION, first.schemaVersion)
        assertEquals("film-profile-v1-apricot-noon", first.filmProfileId)
        assertEquals(0.28, first.edgeSoftness)
        assertEquals(0.26, first.halation)
        assertEquals(0.08, first.exposureBiasEv, 0.000001)
        assertEquals(1.12, first.saturation, 0.000001)
        assertNotSame(first, second)
    }

    @Test
    fun everySupportedCombinationResolvesToBoundedAdapterParameters() {
        for (film in BuiltInFilmVisualProfiles.films) {
            for (body in BuiltInFilmVisualProfiles.cameraBodies) {
                for (lens in BuiltInFilmVisualProfiles.lenses) {
                    val resolved = assertNotNull(
                        BuiltInFilmVisualProfiles.resolve(
                            CaptureRecipe(body.cameraBodyId, lens.lensId, film.filmStockId),
                        ),
                    )
                    assertTrue(resolved.exposureBiasEv in -1.5..1.5)
                    assertTrue(resolved.contrast in 0.5..2.0)
                    assertTrue(resolved.saturation in 0.0..2.0)
                    assertTrue(resolved.warmth in -1.0..1.0)
                    assertTrue(resolved.greenMagenta in -1.0..1.0)
                    assertTrue(resolved.highlightRollOff in 0.0..1.0)
                    assertTrue(resolved.shadowLift in 0.0..0.45)
                    assertTrue(resolved.grainIntensity in 0.0..1.0)
                    assertTrue(resolved.grainScale in 0.0..1.0)
                    assertTrue(resolved.halation in 0.0..1.0)
                    assertTrue(resolved.vignette in 0.0..0.8)
                    assertTrue(resolved.bloom in 0.0..1.0)
                    assertTrue(resolved.edgeSoftness in 0.0..0.5)
                    assertTrue(resolved.distortion in -0.15..0.15)
                    assertTrue(resolved.sharpness in 0.5..1.5)
                }
            }
        }
    }

    @Test
    fun unknownRecipeComponentsHaveNoImplicitFallbackProfile() {
        val recipe = CaptureRecipe(
            bodyId = CameraBodyId("body-unknown"),
            lensId = LensId("lens-aurora"),
            filmStockId = FilmStockId("film-apricot-noon"),
        )
        assertNull(BuiltInFilmVisualProfiles.resolve(recipe))
    }
}
