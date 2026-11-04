package app.theia.domain

import app.theia.domain.model.GalleryLocation
import app.theia.domain.model.GeotagMetadata
import app.theia.domain.model.GeotagMetadataId
import app.theia.domain.model.PermissionStatus
import app.theia.domain.model.UserPreferences
import app.theia.domain.port.DeviceLocationPort
import app.theia.domain.port.DevicePermission
import app.theia.domain.port.LocalPhotoLocationIndexPort
import app.theia.domain.port.PermissionPort
import app.theia.domain.port.PhotoMetadataPort
import app.theia.domain.usecase.AddGeotagUseCase
import app.theia.domain.usecase.GeotagPermissionRequiredException
import app.theia.domain.usecase.LocalGeotaggedPhotosUseCase
import app.theia.domain.usecase.RemoveGeotagUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GeotagUseCasesTest {
    private val location = GeotagMetadata(GeotagMetadataId("place-1"), 40.7, -74.0, 12.0, 1_000)

    @Test
    fun disabledByDefaultDoesNotAskForLocationOrReadIt() = runTest {
        var permissionRequests = 0
        val permissions = object : PermissionPort {
            override fun currentStatus(permission: DevicePermission) = PermissionStatus.NOT_REQUESTED
            override suspend fun request(permission: DevicePermission): PermissionStatus { permissionRequests++; return PermissionStatus.GRANTED }
        }
        val deviceLocation = object : DeviceLocationPort {
            override suspend fun currentLocation() = error("Location must not be read when disabled")
        }
        val metadata = object : PhotoMetadataPort {
            override suspend fun writeGeotag(location: GalleryLocation, geotag: GeotagMetadata) = error("Must not write")
            override suspend fun removeGeotag(location: GalleryLocation) = Unit
        }

        assertNull(AddGeotagUseCase(permissions, deviceLocation, metadata).execute(UserPreferences(languageTag = "en"), GalleryLocation("photo://1")))
        assertEquals(0, permissionRequests)
    }

    @Test
    fun enabledGeotaggingRequiresPermissionThenWritesMetadata() = runTest {
        var written: GeotagMetadata? = null
        var requested = false
        val permissions = object : PermissionPort {
            override fun currentStatus(permission: DevicePermission) = PermissionStatus.NOT_REQUESTED
            override suspend fun request(permission: DevicePermission): PermissionStatus { requested = true; return PermissionStatus.GRANTED }
        }
        val deviceLocation = object : DeviceLocationPort { override suspend fun currentLocation() = location }
        val metadata = object : PhotoMetadataPort {
            override suspend fun writeGeotag(location: GalleryLocation, geotag: GeotagMetadata) { written = geotag }
            override suspend fun removeGeotag(location: GalleryLocation) = Unit
        }

        val result = AddGeotagUseCase(permissions, deviceLocation, metadata).execute(
            UserPreferences(languageTag = "en", geotaggingEnabled = true),
            GalleryLocation("photo://1"),
        )

        assertEquals(true, requested)
        assertEquals(location, result)
        assertEquals(location, written)
    }

    @Test
    fun deniedLocationDoesNotReadOrWriteTheGeotag() = runTest {
        val permissions = object : PermissionPort {
            override fun currentStatus(permission: DevicePermission) = PermissionStatus.DENIED
            override suspend fun request(permission: DevicePermission) = PermissionStatus.DENIED
        }
        val deviceLocation = object : DeviceLocationPort { override suspend fun currentLocation() = error("Must not read") }
        val metadata = object : PhotoMetadataPort {
            override suspend fun writeGeotag(location: GalleryLocation, geotag: GeotagMetadata) = error("Must not write")
            override suspend fun removeGeotag(location: GalleryLocation) = Unit
        }

        assertFailsWith<GeotagPermissionRequiredException> {
            AddGeotagUseCase(permissions, deviceLocation, metadata).execute(
                UserPreferences(languageTag = "en", geotaggingEnabled = true), GalleryLocation("photo://1"),
            )
        }
    }

    @Test
    fun localMapQueryReadsOnlyTheLocalIndexAndCanRemoveMetadata() = runTest {
        val photo = GalleryLocation("photo://1")
        var removed: GalleryLocation? = null
        val index = object : LocalPhotoLocationIndexPort {
            override suspend fun photosWithGeotags() = listOf(app.theia.domain.port.LocalGeotaggedPhoto(photo, location))
        }
        val metadata = object : PhotoMetadataPort {
            override suspend fun writeGeotag(location: GalleryLocation, geotag: GeotagMetadata) = Unit
            override suspend fun removeGeotag(location: GalleryLocation) { removed = location }
        }

        assertEquals(photo, LocalGeotaggedPhotosUseCase(index).execute().single().photoLocation)
        RemoveGeotagUseCase(metadata).execute(photo)
        assertEquals(photo, removed)
    }
}
