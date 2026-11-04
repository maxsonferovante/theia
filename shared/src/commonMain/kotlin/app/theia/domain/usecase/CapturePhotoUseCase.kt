package app.theia.domain.usecase

import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmRollId
import app.theia.domain.model.PhotoAsset
import app.theia.domain.model.PhotoAssetId
import app.theia.domain.port.CameraCapturePort
import app.theia.domain.port.MediaLibraryPort

class CapturePhotoUseCase(
    private val camera: CameraCapturePort,
    private val mediaLibrary: MediaLibraryPort,
) {
    suspend fun execute(
        recipe: CaptureRecipe,
        photoId: PhotoAssetId,
        rollId: FilmRollId?,
    ): PhotoAsset {
        val capturedPhoto = camera.capturePhoto(recipe)
        val originalLocation = mediaLibrary.saveOriginal(capturedPhoto)

        return PhotoAsset(
            id = photoId,
            recipe = recipe,
            rollId = rollId,
            capturedAtEpochMillis = capturedPhoto.capturedAtEpochMillis,
            originalGalleryLocation = originalLocation,
        )
    }
}
