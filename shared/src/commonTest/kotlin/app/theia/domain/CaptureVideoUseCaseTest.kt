package app.theia.domain

import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.GalleryLocation
import app.theia.domain.model.LensId
import app.theia.domain.model.PermissionStatus
import app.theia.domain.model.VideoAssetId
import app.theia.domain.port.CapturedVideo
import app.theia.domain.port.DevicePermission
import app.theia.domain.port.PermissionPort
import app.theia.domain.port.VideoCapturePort
import app.theia.domain.port.VideoMediaLibraryPort
import app.theia.domain.usecase.AudioPermissionRequiredException
import app.theia.domain.usecase.CaptureVideoUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CaptureVideoUseCaseTest {
    private val recipe = CaptureRecipe(CameraBodyId("body-test"), LensId("lens-test"), FilmStockId("film-test"))

    @Test
    fun videoIsBoundedToSixtySecondsAndSavedWithItsRecipe() = runTest {
        var maxDuration = 0L
        var savedRecipe: CaptureRecipe? = null
        val camera = object : VideoCapturePort {
            override suspend fun recordVideo(recipe: CaptureRecipe, includeAudio: Boolean, maxDurationMillis: Long): CapturedVideo {
                maxDuration = maxDurationMillis
                return CapturedVideo("staged://video/1", 500, 59_000, 1920, 1080, 30, includeAudio)
            }
        }
        val library = object : VideoMediaLibraryPort {
            override suspend fun saveVideo(video: CapturedVideo, recipe: CaptureRecipe): GalleryLocation {
                savedRecipe = recipe
                return GalleryLocation("gallery://video/1")
            }
        }
        val permissions = object : PermissionPort {
            override fun currentStatus(permission: DevicePermission) = PermissionStatus.NOT_REQUESTED
            override suspend fun request(permission: DevicePermission) = error("Muted video must not request a microphone")
        }

        val result = CaptureVideoUseCase(camera, library, permissions).execute(recipe, VideoAssetId("video-1"), false)

        assertEquals(60_000L, maxDuration)
        assertEquals(recipe, savedRecipe)
        assertEquals(GalleryLocation("gallery://video/1"), result.galleryLocation)
        assertEquals(false, result.includesAudio)
        assertEquals(59_000L, result.durationMillis)
    }

    @Test
    fun enabledAudioRequiresMicrophonePermissionBeforeRecording() = runTest {
        var recorded = false
        val camera = object : VideoCapturePort {
            override suspend fun recordVideo(recipe: CaptureRecipe, includeAudio: Boolean, maxDurationMillis: Long): CapturedVideo {
                recorded = true
                error("Must not record without permission")
            }
        }
        val library = object : VideoMediaLibraryPort {
            override suspend fun saveVideo(video: CapturedVideo, recipe: CaptureRecipe) = error("Must not save")
        }
        val permissions = object : PermissionPort {
            override fun currentStatus(permission: DevicePermission) = PermissionStatus.NOT_REQUESTED
            override suspend fun request(permission: DevicePermission) = PermissionStatus.DENIED
        }

        assertFailsWith<AudioPermissionRequiredException> {
            CaptureVideoUseCase(camera, library, permissions).execute(recipe, VideoAssetId("video-2"), true)
        }
        assertEquals(false, recorded)
    }
}
