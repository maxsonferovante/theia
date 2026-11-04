package app.theia.domain

import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmRoll
import app.theia.domain.model.FilmRollCapacity
import app.theia.domain.model.FilmRollId
import app.theia.domain.model.FilmRollRecord
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.GalleryLocation
import app.theia.domain.model.LensId
import app.theia.domain.model.PhotoAsset
import app.theia.domain.model.PhotoAssetId
import app.theia.domain.port.CameraCapturePort
import app.theia.domain.port.CapturedPhoto
import app.theia.domain.port.DevelopedPhoto
import app.theia.domain.port.FilmRollRepositoryPort
import app.theia.domain.port.ImageProcessingPort
import app.theia.domain.port.MediaLibraryPort
import app.theia.domain.port.OriginalPhotoSourcePort
import app.theia.domain.usecase.CapturePhotoIntoRollUseCase
import app.theia.domain.usecase.CreateFilmRollUseCase
import app.theia.domain.usecase.DevelopFilmRollUseCase
import app.theia.domain.usecase.DevelopPhotoInRollUseCase
import app.theia.domain.usecase.GetVisibleRollPhotosUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FilmRollUseCasesTest {
    private val recipe = CaptureRecipe(CameraBodyId("body"), LensId("lens"), FilmStockId("film"))

    @Test
    fun createAndCapturePersistRecipeAndOriginalBeforeConsumingExposure() = runTest {
        val repository = FakeRollRepository()
        val media = FakeMediaLibrary()
        val camera = FakeCamera()
        val rollId = FilmRollId("roll")

        val created = CreateFilmRollUseCase(repository).execute(
            id = rollId,
            recipe = recipe,
            capacity = FilmRollCapacity.EXPOSURES_24,
            createdAtEpochMillis = 100,
        )
        val captured = CapturePhotoIntoRollUseCase(repository, camera, media)
            .execute(rollId, PhotoAssetId("photo-1"))

        assertEquals(recipe, created.recipe)
        assertEquals(recipe, camera.receivedRecipes.single())
        assertEquals("gallery://original/photo", captured.originalGalleryLocation.value)
        assertEquals(listOf(PhotoAssetId("photo-1")), repository.find(rollId)?.roll?.photoIds)
        assertEquals(1, repository.find(rollId)?.photos?.size)
    }

    @Test
    fun instantDevelopmentMakesOnlyDevelopedPhotoVisibleAndRollRemainsOpen() = runTest {
        val fixture = fixture()
        val captured = CapturePhotoIntoRollUseCase(fixture.repository, fixture.camera, fixture.media)
            .execute(fixture.rollId, PhotoAssetId("photo-1"))
        val source = FakeOriginalSource()
        source.originals[captured.originalGalleryLocation] = fixture.camera.photo

        val revealed = DevelopPhotoInRollUseCase(
            fixture.repository, source, fixture.processor, fixture.media,
        ).execute(fixture.rollId, captured.id)

        assertEquals(GalleryLocation("gallery://developed/photo-1"), revealed.developedGalleryLocation)
        assertEquals(listOf(captured.id), GetVisibleRollPhotosUseCase(fixture.repository).execute(fixture.rollId).map { it.id })
        assertNull(fixture.repository.find(fixture.rollId)?.roll?.developedAtEpochMillis)
        CapturePhotoIntoRollUseCase(fixture.repository, fixture.camera, fixture.media)
            .execute(fixture.rollId, PhotoAssetId("photo-2"))
        assertEquals(2, fixture.repository.find(fixture.rollId)?.photos?.size)
    }

    @Test
    fun fullRollIsRequiredUnlessEarlyDevelopmentIsExplicitAndClosedRollRevealsAllPhotos() = runTest {
        val fixture = fixture()
        val photo1 = PhotoAsset(
            PhotoAssetId("photo-1"), recipe, fixture.rollId, 120,
            GalleryLocation("gallery://original/1"),
        )
        fixture.repository.record = FilmRollRecord(fixture.repository.record!!.roll.recordPhoto(photo1.id), recipe, listOf(photo1))
        val source = FakeOriginalSource().also {
            it.originals[photo1.originalGalleryLocation] = fixture.camera.photo
        }
        val develop = DevelopFilmRollUseCase(fixture.repository, source, fixture.processor, fixture.media)

        assertFailsWith<IllegalArgumentException> { develop.execute(fixture.rollId, atEpochMillis = 130) }
        assertNull(fixture.repository.record?.roll?.developedAtEpochMillis)

        val result = develop.execute(fixture.rollId, atEpochMillis = 130, allowEarly = true)
        assertEquals(130, result.roll.developedAtEpochMillis)
        assertEquals(listOf(photo1.id), GetVisibleRollPhotosUseCase(fixture.repository).execute(fixture.rollId).map { it.id })
        assertEquals(GalleryLocation("gallery://developed/photo-1"), result.photos.single().developedGalleryLocation)
    }

    @Test
    fun fullRollDevelopsAndClosesAfterItsLastExposure() = runTest {
        val fixture = fixture()
        var full = fixture.repository.record!!
        val source = FakeOriginalSource()
        repeat(FilmRollCapacity.EXPOSURES_24.exposures) { index ->
            val photo = PhotoAsset(
                id = PhotoAssetId("photo-$index"),
                recipe = recipe,
                rollId = fixture.rollId,
                capturedAtEpochMillis = 110L + index,
                originalGalleryLocation = GalleryLocation("gallery://original/$index"),
            )
            full = full.append(photo)
            source.originals[photo.originalGalleryLocation] = fixture.camera.photo
        }
        fixture.repository.record = full

        val developed = DevelopFilmRollUseCase(fixture.repository, source, fixture.processor, fixture.media)
            .execute(fixture.rollId, atEpochMillis = 140)

        assertEquals(FilmRollCapacity.EXPOSURES_24.exposures, developed.photos.size)
        assertEquals(FilmRollCapacity.EXPOSURES_24.exposures, developed.visiblePhotos().size)
        assertEquals(140, developed.roll.developedAtEpochMillis)
    }

    @Test
    fun failedOriginalSaveDoesNotConsumeExposure() = runTest {
        val fixture = fixture()
        fixture.media.failOriginalSave = true

        assertFailsWith<IllegalStateException> {
            CapturePhotoIntoRollUseCase(fixture.repository, fixture.camera, fixture.media)
                .execute(fixture.rollId, PhotoAssetId("photo-1"))
        }
        assertEquals(emptyList(), fixture.repository.record?.roll?.photoIds)
    }

    private fun fixture(): Fixture {
        val repository = FakeRollRepository()
        val rollId = FilmRollId("roll")
        repository.record = FilmRollRecord(FilmRoll(rollId, FilmRollCapacity.EXPOSURES_24, 100), recipe)
        return Fixture(repository, rollId, FakeCamera(), FakeMediaLibrary(), FakeProcessor())
    }

    private data class Fixture(
        val repository: FakeRollRepository,
        val rollId: FilmRollId,
        val camera: FakeCamera,
        val media: FakeMediaLibrary,
        val processor: FakeProcessor,
    )

    private class FakeRollRepository : FilmRollRepositoryPort {
        var record: FilmRollRecord? = null
        override suspend fun find(id: FilmRollId): FilmRollRecord? = record?.takeIf { it.roll.id == id }
        override suspend fun insert(record: FilmRollRecord) {
            check(this.record == null) { "Roll already exists" }
            this.record = record
        }
        override suspend fun save(record: FilmRollRecord) { this.record = record }
        override suspend fun <T> withRollLock(id: FilmRollId, operation: suspend () -> T): T = operation()
    }

    private class FakeCamera : CameraCapturePort {
        val receivedRecipes = mutableListOf<CaptureRecipe>()
        val photo = CapturedPhoto("staged://photo", 120, 100, 100)
        override suspend fun capturePhoto(recipe: CaptureRecipe): CapturedPhoto {
            receivedRecipes += recipe
            return photo
        }
    }

    private class FakeProcessor : ImageProcessingPort {
        override suspend fun developPhoto(photo: CapturedPhoto, recipe: CaptureRecipe) = DevelopedPhoto("developed://photo")
    }

    private class FakeOriginalSource : OriginalPhotoSourcePort {
        val originals = mutableMapOf<GalleryLocation, CapturedPhoto>()
        override suspend fun loadOriginal(location: GalleryLocation): CapturedPhoto =
            originals[location] ?: error("Missing fixture original: ${location.value}")
    }

    private class FakeMediaLibrary : MediaLibraryPort {
        var failOriginalSave = false
        override suspend fun saveOriginal(photo: CapturedPhoto): GalleryLocation {
            check(!failOriginalSave) { "Gallery unavailable" }
            return GalleryLocation("gallery://original/photo")
        }
        override suspend fun saveDeveloped(photo: DevelopedPhoto): GalleryLocation =
            GalleryLocation("gallery://developed/photo-1")
        override suspend fun delete(location: GalleryLocation) = Unit
    }
}
