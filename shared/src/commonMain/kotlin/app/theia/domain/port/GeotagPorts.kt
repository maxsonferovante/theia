package app.theia.domain.port

import app.theia.domain.model.GeotagMetadata
import app.theia.domain.model.GalleryLocation

interface DeviceLocationPort {
    suspend fun currentLocation(): GeotagMetadata?
}

/** Metadata adapters must operate on device-local files and must not call a network service. */
interface PhotoMetadataPort {
    suspend fun writeGeotag(location: GalleryLocation, geotag: GeotagMetadata)

    suspend fun removeGeotag(location: GalleryLocation)
}

data class LocalGeotaggedPhoto(val photoLocation: GalleryLocation, val geotag: GeotagMetadata)

interface LocalPhotoLocationIndexPort {
    suspend fun photosWithGeotags(): List<LocalGeotaggedPhoto>
}
