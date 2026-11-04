package app.theia.domain.usecase

import app.theia.domain.model.GalleryLocation
import app.theia.domain.model.GeotagMetadata
import app.theia.domain.model.PermissionStatus
import app.theia.domain.model.UserPreferences
import app.theia.domain.port.DeviceLocationPort
import app.theia.domain.port.DevicePermission
import app.theia.domain.port.LocalPhotoLocationIndexPort
import app.theia.domain.port.PermissionPort
import app.theia.domain.port.PhotoMetadataPort

class GeotagPermissionRequiredException : IllegalStateException("Location permission is required to add a geotag")

/** Location is read only after the user has explicitly enabled geotagging. */
class AddGeotagUseCase(
    private val permissions: PermissionPort,
    private val location: DeviceLocationPort,
    private val metadata: PhotoMetadataPort,
) {
    suspend fun execute(preferences: UserPreferences, photo: GalleryLocation): GeotagMetadata? {
        if (!preferences.geotaggingEnabled) return null
        val status = permissions.currentStatus(DevicePermission.LOCATION).let { current ->
            if (current == PermissionStatus.GRANTED) current else permissions.request(DevicePermission.LOCATION)
        }
        if (status != PermissionStatus.GRANTED) throw GeotagPermissionRequiredException()
        val geotag = location.currentLocation() ?: return null
        metadata.writeGeotag(photo, geotag)
        return geotag
    }
}

class RemoveGeotagUseCase(private val metadata: PhotoMetadataPort) {
    suspend fun execute(photo: GalleryLocation) = metadata.removeGeotag(photo)
}

/** Returns the local location index directly; no remote map or geocoding dependency exists. */
class LocalGeotaggedPhotosUseCase(private val index: LocalPhotoLocationIndexPort) {
    suspend fun execute() = index.photosWithGeotags()
}
