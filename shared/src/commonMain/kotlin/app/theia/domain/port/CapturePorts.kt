package app.theia.domain.port

import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.GalleryLocation

data class CapturedPhoto(
    val handle: String,
    val capturedAtEpochMillis: Long,
    val widthPixels: Int,
    val heightPixels: Int,
) {
    init {
        require(handle.isNotBlank()) { "Captured photo handles cannot be blank" }
        require(capturedAtEpochMillis >= 0) { "Capture time must be non-negative" }
        require(widthPixels > 0 && heightPixels > 0) { "Captured photo dimensions must be positive" }
    }
}

data class DevelopedPhoto(val handle: String) {
    init { require(handle.isNotBlank()) { "Developed photo handles cannot be blank" } }
}

interface CameraCapturePort {
    suspend fun capturePhoto(recipe: CaptureRecipe): CapturedPhoto
}

interface ImageProcessingPort {
    suspend fun developPhoto(photo: CapturedPhoto, recipe: CaptureRecipe): DevelopedPhoto
}

interface MediaLibraryPort {
    suspend fun saveOriginal(photo: CapturedPhoto): GalleryLocation

    suspend fun saveDeveloped(photo: DevelopedPhoto): GalleryLocation

    suspend fun delete(location: GalleryLocation)
}
