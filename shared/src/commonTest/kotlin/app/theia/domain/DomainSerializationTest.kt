package app.theia.domain

import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.CaptureRecipeId
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.GeotagMetadata
import app.theia.domain.model.GeotagMetadataId
import app.theia.domain.model.LensId
import app.theia.domain.model.PermissionState
import app.theia.domain.model.PermissionStateId
import app.theia.domain.serialization.UnsupportedSchemaVersionException
import app.theia.domain.serialization.VersionedJsonCodec
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DomainSerializationTest {
    private val codec = VersionedJsonCodec()

    @Test
    fun recipeIdentifierIsStableForTheSameEquipment() {
        val parts = Triple(CameraBodyId("body-01"), LensId("lens-02"), FilmStockId("film-03"))

        val first = CaptureRecipe(
            bodyId = parts.first,
            lensId = parts.second,
            filmStockId = parts.third,
        )
        val second = CaptureRecipe(
            bodyId = parts.first,
            lensId = parts.second,
            filmStockId = parts.third,
        )

        assertEquals(first.id, second.id)
    }

    @Test
    fun recipeRoundTripsInsideVersionedEnvelope() {
        val recipe = CaptureRecipe(
            bodyId = CameraBodyId("body-01"),
            lensId = LensId("lens-02"),
            filmStockId = FilmStockId("film-03"),
        )

        assertEquals(recipe, codec.decode(CaptureRecipe.serializer(), codec.encode(recipe)))
    }

    @Test
    fun unsupportedSchemaVersionFailsClearly() {
        assertFailsWith<UnsupportedSchemaVersionException> {
            codec.decode(Int.serializer(), "{\"schemaVersion\":99,\"payload\":1}")
        }
    }

    @Test
    fun callerCannotSupplyAnIdentifierForADifferentRecipe() {
        assertFailsWith<IllegalArgumentException> {
            CaptureRecipe(
                bodyId = CameraBodyId("body-01"),
                lensId = LensId("lens-02"),
                filmStockId = FilmStockId("film-03"),
                id = CaptureRecipeId("recipe-v1:invalid"),
            )
        }
    }

    @Test
    fun permissionStateHasAStableIdentifierAndRoundTrips() {
        val state = PermissionState()

        assertEquals(PermissionStateId.DEFAULT, state.id)
        assertEquals(state, codec.decode(PermissionState.serializer(), codec.encode(state)))
    }

    @Test
    fun geotagMetadataHasAStableIdentifierAndRoundTrips() {
        val geotag = GeotagMetadata(
            id = GeotagMetadataId("geotag-photo-42"),
            latitude = -23.5505,
            longitude = -46.6333,
            accuracyMeters = 8.0,
            capturedAtEpochMillis = 1_798_000_000_000,
        )

        assertEquals(geotag, codec.decode(GeotagMetadata.serializer(), codec.encode(geotag)))
    }
}
