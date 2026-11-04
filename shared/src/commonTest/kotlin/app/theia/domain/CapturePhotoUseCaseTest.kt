package app.theia.domain

import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmRollId
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.GalleryLocation
import app.theia.domain.model.LensId
import app.theia.domain.model.PhotoAssetId
import app.theia.domain.port.CameraCapturePort
import app.theia.domain.port.CapturedPhoto
import app.theia.domain.port.DevelopedPhoto
import app.theia.domain.port.MediaLibraryPort
import app.theia.domain.usecase.CapturePhotoUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CapturePhotoUseCaseTest {
    @Test
    fun savesOriginalAndReturnsPhotoWithSelectedRecipe() = runTest {
        val selectedRecipe = CaptureRecipe(
            bodyId = CameraBodyId("body-01"),
            lensId = LensId("lens-01"),
            filmStockId = FilmStockId("film-01"),
        )
        val calls = mutableListOf<String>()
        val captured = CapturedPhoto(
            handle = "staged://capture/42",
            capturedAtEpochMillis = 1_798_000_000_000,
            widthPixels = 4_032,
            heightPixels = 3_024,
        )
        val camera = object : CameraCapturePort {
            override suspend fun capturePhoto(recipe: CaptureRecipe): CapturedPhoto {
                calls += "capture"
                assertSame(selectedRecipe, recipe)
                return captured
            }
        }
        val mediaLibrary = object : MediaLibraryPort {
            override suspend fun saveOriginal(photo: CapturedPhoto): GalleryLocation {
                calls += "save-original"
                assertSame(captured, photo)
                return GalleryLocation("gallery://original/42")
            }

            override suspend fun saveDeveloped(photo: DevelopedPhoto): GalleryLocation =
                error("Development is outside this use case")

            override suspend fun delete(location: GalleryLocation) =
                error("Deletion is outside this use case")
        }

        val result = CapturePhotoUseCase(camera, mediaLibrary).execute(
            recipe = selectedRecipe,
            photoId = PhotoAssetId("photo-42"),
            rollId = FilmRollId("roll-7"),
        )

        assertEquals(listOf("capture", "save-original"), calls)
        assertEquals(PhotoAssetId("photo-42"), result.id)
        assertEquals(FilmRollId("roll-7"), result.rollId)
        assertEquals(captured.capturedAtEpochMillis, result.capturedAtEpochMillis)
        assertEquals(GalleryLocation("gallery://original/42"), result.originalGalleryLocation)
        assertSame(selectedRecipe, result.recipe)
    }
}
