package app.theia.domain.usecase

import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmRoll
import app.theia.domain.model.FilmRollCapacity
import app.theia.domain.model.FilmRollId
import app.theia.domain.model.FilmRollRecord
import app.theia.domain.model.PhotoAsset
import app.theia.domain.model.PhotoAssetId
import app.theia.domain.port.CameraCapturePort
import app.theia.domain.port.FilmRollRepositoryPort
import app.theia.domain.port.ImageProcessingPort
import app.theia.domain.port.MediaLibraryPort
import app.theia.domain.port.OriginalPhotoSourcePort

class CreateFilmRollUseCase(private val rolls: FilmRollRepositoryPort) {
    suspend fun execute(
        id: FilmRollId,
        recipe: CaptureRecipe,
        capacity: FilmRollCapacity,
        createdAtEpochMillis: Long,
    ): FilmRollRecord {
        val record = FilmRollRecord(
            roll = FilmRoll(id = id, capacity = capacity, createdAtEpochMillis = createdAtEpochMillis),
            recipe = recipe,
        )
        rolls.insert(record)
        return record
    }
}

/** Captures and persists one exposure under a per-roll lock, so concurrent captures cannot overfill it. */
class CapturePhotoIntoRollUseCase(
    private val rolls: FilmRollRepositoryPort,
    private val camera: CameraCapturePort,
    private val mediaLibrary: MediaLibraryPort,
) {
    suspend fun execute(rollId: FilmRollId, photoId: PhotoAssetId): PhotoAsset =
        rolls.withRollLock(rollId) {
            val record = rolls.find(rollId) ?: error("Film roll does not exist")
            // Check eligibility before invoking the camera or writing to the gallery.
            record.roll.recordPhoto(photoId)
            val captured = camera.capturePhoto(record.recipe)
            val originalLocation = mediaLibrary.saveOriginal(captured)
            val photo = PhotoAsset(
                id = photoId,
                recipe = record.recipe,
                rollId = rollId,
                capturedAtEpochMillis = captured.capturedAtEpochMillis,
                originalGalleryLocation = originalLocation,
            )
            try {
                rolls.save(record.append(photo))
            } catch (failure: Throwable) {
                // Avoid leaving an unreferenced original if persistence fails.
                runCatching { mediaLibrary.delete(originalLocation) }
                throw failure
            }
            photo
        }
}

/** Develops one exposure immediately while leaving its roll open for further captures. */
class DevelopPhotoInRollUseCase(
    private val rolls: FilmRollRepositoryPort,
    private val originals: OriginalPhotoSourcePort,
    private val processor: ImageProcessingPort,
    private val mediaLibrary: MediaLibraryPort,
) {
    suspend fun execute(rollId: FilmRollId, photoId: PhotoAssetId): PhotoAsset =
        rolls.withRollLock(rollId) {
            val record = rolls.find(rollId) ?: error("Film roll does not exist")
            require(record.roll.developedAtEpochMillis == null) { "Film roll has already been developed" }
            val photo = record.photos.firstOrNull { it.id == photoId } ?: error("Photo does not belong to this roll")
            if (photo.developedGalleryLocation != null) return@withRollLock photo
            val original = originals.loadOriginal(photo.originalGalleryLocation)
            val developed = processor.developPhoto(original, record.recipe)
            val location = mediaLibrary.saveDeveloped(developed)
            val result = photo.copy(developedGalleryLocation = location)
            try {
                rolls.save(record.updatePhoto(result))
            } catch (failure: Throwable) {
                runCatching { mediaLibrary.delete(location) }
                throw failure
            }
            result
        }
}

/** Develops all remaining exposures and closes the roll. Early development is explicit. */
class DevelopFilmRollUseCase(
    private val rolls: FilmRollRepositoryPort,
    private val originals: OriginalPhotoSourcePort,
    private val processor: ImageProcessingPort,
    private val mediaLibrary: MediaLibraryPort,
) {
    suspend fun execute(rollId: FilmRollId, atEpochMillis: Long, allowEarly: Boolean = false): FilmRollRecord =
        rolls.withRollLock(rollId) {
            var record = rolls.find(rollId) ?: error("Film roll does not exist")
            if (record.roll.developedAtEpochMillis != null) return@withRollLock record
            require(allowEarly || record.roll.photoIds.size == record.roll.capacity.exposures) {
                "Film roll must be full before development"
            }
            require(atEpochMillis >= record.roll.createdAtEpochMillis) {
                "Development time cannot precede roll creation"
            }

            for (photo in record.photos.filter { it.developedGalleryLocation == null }) {
                val source = originals.loadOriginal(photo.originalGalleryLocation)
                val developed = processor.developPhoto(source, record.recipe)
                val location = mediaLibrary.saveDeveloped(developed)
                val updated = photo.copy(developedGalleryLocation = location)
                try {
                    record = record.updatePhoto(updated)
                    rolls.save(record)
                } catch (failure: Throwable) {
                    runCatching { mediaLibrary.delete(location) }
                    throw failure
                }
            }
            record = record.copy(roll = record.roll.develop(atEpochMillis))
            rolls.save(record)
            record
        }
}

class GetVisibleRollPhotosUseCase(private val rolls: FilmRollRepositoryPort) {
    suspend fun execute(rollId: FilmRollId): List<PhotoAsset> =
        rolls.find(rollId)?.visiblePhotos() ?: emptyList()
}
