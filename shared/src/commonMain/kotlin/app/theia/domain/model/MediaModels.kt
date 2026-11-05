package app.theia.domain.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class GalleryLocation(val value: String) {
    init { require(value.isNotBlank()) { "Gallery locations cannot be blank" } }
}

@Serializable
enum class FilmRollCapacity(val exposures: Int) {
    EXPOSURES_24(24),
    EXPOSURES_36(36),
}

@Serializable
data class FilmRoll(
    val id: FilmRollId,
    val capacity: FilmRollCapacity,
    val createdAtEpochMillis: Long,
    val photoIds: List<PhotoAssetId> = emptyList(),
    val developedAtEpochMillis: Long? = null,
) {
    init {
        require(createdAtEpochMillis >= 0) { "Film roll creation time must be non-negative" }
        require(photoIds.size <= capacity.exposures) { "Film roll cannot exceed its exposure capacity" }
        require(photoIds.distinct().size == photoIds.size) { "A photo can consume only one roll exposure" }
        require(developedAtEpochMillis == null || developedAtEpochMillis >= createdAtEpochMillis) {
            "Development time cannot precede roll creation"
        }
    }

    @Throws(IllegalArgumentException::class)
    fun recordPhoto(photoId: PhotoAssetId): FilmRoll {
        require(developedAtEpochMillis == null) { "A developed roll cannot accept new photos" }
        require(photoId !in photoIds) { "A photo can consume only one roll exposure" }
        require(photoIds.size < capacity.exposures) { "Film roll has no exposures remaining" }
        return copy(photoIds = photoIds + photoId)
    }

    @Throws(IllegalArgumentException::class)
    fun develop(atEpochMillis: Long): FilmRoll {
        require(developedAtEpochMillis == null) { "Film roll has already been developed" }
        require(atEpochMillis >= createdAtEpochMillis) { "Development time cannot precede roll creation" }
        return copy(developedAtEpochMillis = atEpochMillis)
    }
}

@Serializable
data class PhotoAsset(
    val id: PhotoAssetId,
    val recipe: CaptureRecipe,
    val rollId: FilmRollId?,
    val capturedAtEpochMillis: Long,
    val originalGalleryLocation: GalleryLocation,
    val developedGalleryLocation: GalleryLocation? = null,
) {
    init { require(capturedAtEpochMillis >= 0) { "Photo capture time must be non-negative" } }
}

@Serializable
data class VideoAsset(
    val id: VideoAssetId,
    val recipe: CaptureRecipe,
    val capturedAtEpochMillis: Long,
    val durationMillis: Long,
    val galleryLocation: GalleryLocation,
    val includesAudio: Boolean,
) {
    init {
        require(capturedAtEpochMillis >= 0) { "Video capture time must be non-negative" }
        require(durationMillis > 0) { "Video duration must be positive" }
    }
}

@Serializable
data class Album(
    val id: AlbumId,
    val photoIds: List<PhotoAssetId> = emptyList(),
    val videoIds: List<VideoAssetId> = emptyList(),
)
