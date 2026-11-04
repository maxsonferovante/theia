package app.theia.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PermissionStatus {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    RESTRICTED,
}

@Serializable
data class PermissionState(
    val id: PermissionStateId = PermissionStateId.DEFAULT,
    val camera: PermissionStatus = PermissionStatus.NOT_REQUESTED,
    val photoLibrary: PermissionStatus = PermissionStatus.NOT_REQUESTED,
    val microphone: PermissionStatus = PermissionStatus.NOT_REQUESTED,
    val location: PermissionStatus = PermissionStatus.NOT_REQUESTED,
)

@Serializable
data class GeotagMetadata(
    val id: GeotagMetadataId,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val capturedAtEpochMillis: Long,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
        require(accuracyMeters == null || accuracyMeters >= 0.0) { "Accuracy cannot be negative" }
        require(capturedAtEpochMillis >= 0) { "Geotag capture time must be non-negative" }
    }
}

@Serializable
data class UserPreferences(
    val id: UserPreferencesId = UserPreferencesId.DEFAULT,
    val languageTag: String,
    val instantDevelopment: Boolean = false,
    val geotaggingEnabled: Boolean = false,
    val shutterSoundEnabled: Boolean = true,
) {
    init { require(languageTag.isNotBlank()) { "Language tag cannot be blank" } }
}
