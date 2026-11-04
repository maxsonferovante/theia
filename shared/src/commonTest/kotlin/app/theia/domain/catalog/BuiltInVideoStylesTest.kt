package app.theia.domain.catalog

import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.LensId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BuiltInVideoStylesTest {
    private val recipe = CaptureRecipe(
        CameraBodyId("body-helia"),
        LensId("lens-aurora"),
        FilmStockId("film-apricot-noon"),
    )

    @Test
    fun equipmentRecipeChoiceKeepsThePhotoRecipeProfileExactly() {
        assertEquals(
            BuiltInFilmVisualProfiles.resolve(recipe),
            BuiltInVideoStyles.resolve(recipe, BuiltInVideoStyles.EQUIPMENT_RECIPE_ID),
        )
    }

    @Test
    fun authoredPresetRetainsEquipmentIdentityAndCreatesASeparateProfile() {
        val recipeProfile = assertNotNull(BuiltInFilmVisualProfiles.resolve(recipe))
        val videoProfile = assertNotNull(BuiltInVideoStyles.resolve(recipe, "moonlit-monochrome"))

        assertEquals(recipe.bodyId, videoProfile.cameraBodyId)
        assertEquals(recipe.lensId, videoProfile.lensId)
        assertEquals(recipe.filmStockId, videoProfile.filmStockId)
        assertNotEquals(recipeProfile.filmProfileId, videoProfile.filmProfileId)
        assertEquals(0.0, videoProfile.saturation)
        assertNotEquals(recipeProfile.grainIntensity, videoProfile.grainIntensity)
    }

    @Test
    fun unknownStyleFailsClosed() {
        assertNull(BuiltInVideoStyles.resolve(recipe, "unrecognized-style"))
    }
}
