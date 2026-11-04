package app.theia.domain.port

import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.GalleryLocation

data class CapturedVideo(
    val handle: String,
    val capturedAtEpochMillis: Long,
    val durationMillis: Long,
    val widthPixels: Int,
    val heightPixels: Int,
    val framesPerSecond: Int,
    val includesAudio: Boolean,
) {
    init {
        require(handle.isNotBlank())
        require(capturedAtEpochMillis >= 0)
        require(durationMillis in 1..60_000)
        require(widthPixels > 0 && heightPixels > 0)
        require(framesPerSecond > 0)
    }
}

interface VideoCapturePort {
    suspend fun recordVideo(
        recipe: CaptureRecipe,
        includeAudio: Boolean,
        maxDurationMillis: Long,
    ): CapturedVideo
}

interface VideoMediaLibraryPort {
    suspend fun saveVideo(video: CapturedVideo, recipe: CaptureRecipe): GalleryLocation
}
