package app.theia.domain.usecase

import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.PermissionStatus
import app.theia.domain.model.VideoAsset
import app.theia.domain.model.VideoAssetId
import app.theia.domain.port.DevicePermission
import app.theia.domain.port.PermissionPort
import app.theia.domain.port.VideoCapturePort
import app.theia.domain.port.VideoMediaLibraryPort

class AudioPermissionRequiredException : IllegalStateException("Microphone permission is required when video audio is enabled")

class CaptureVideoUseCase(
    private val camera: VideoCapturePort,
    private val mediaLibrary: VideoMediaLibraryPort,
    private val permissions: PermissionPort,
) {
    suspend fun execute(
        recipe: CaptureRecipe,
        videoId: VideoAssetId,
        includeAudio: Boolean,
    ): VideoAsset {
        if (includeAudio) {
            val status = permissions.currentStatus(DevicePermission.MICROPHONE).let { current ->
                if (current == PermissionStatus.GRANTED) current else permissions.request(DevicePermission.MICROPHONE)
            }
            if (status != PermissionStatus.GRANTED) throw AudioPermissionRequiredException()
        }

        val captured = camera.recordVideo(
            recipe = recipe,
            includeAudio = includeAudio,
            maxDurationMillis = MAX_VIDEO_DURATION_MILLIS,
        )
        check(captured.durationMillis <= MAX_VIDEO_DURATION_MILLIS) { "The capture adapter exceeded the 60 second limit" }
        check(captured.includesAudio == includeAudio) { "The capture adapter returned an unexpected audio setting" }

        val location = mediaLibrary.saveVideo(captured, recipe)
        return VideoAsset(
            id = videoId,
            recipe = recipe,
            capturedAtEpochMillis = captured.capturedAtEpochMillis,
            durationMillis = captured.durationMillis,
            galleryLocation = location,
            includesAudio = captured.includesAudio,
        )
    }

    private companion object {
        const val MAX_VIDEO_DURATION_MILLIS = 60_000L
    }
}
