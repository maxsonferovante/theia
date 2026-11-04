package app.theia.domain.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

interface StableIdentifier {
    val value: String
}

@Serializable
@JvmInline
value class CameraBodyId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "Camera body IDs cannot be blank" } }
}

@Serializable
@JvmInline
value class LensId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "Lens IDs cannot be blank" } }
}

@Serializable
@JvmInline
value class FilmStockId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "Film stock IDs cannot be blank" } }
}

@Serializable
@JvmInline
value class CaptureRecipeId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "Capture recipe IDs cannot be blank" } }

    companion object {
        fun forEquipment(
            bodyId: CameraBodyId,
            lensId: LensId,
            filmStockId: FilmStockId,
        ): CaptureRecipeId = CaptureRecipeId(
            "recipe-v1:${encodePart(bodyId.value)}${encodePart(lensId.value)}${encodePart(filmStockId.value)}",
        )

        private fun encodePart(value: String): String = "${value.length}:$value"
    }
}

@Serializable
@JvmInline
value class FilmRollId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "Film roll IDs cannot be blank" } }
}

@Serializable
@JvmInline
value class PhotoAssetId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "Photo asset IDs cannot be blank" } }
}

@Serializable
@JvmInline
value class VideoAssetId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "Video asset IDs cannot be blank" } }
}

@Serializable
@JvmInline
value class AlbumId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "Album IDs cannot be blank" } }
}

@Serializable
@JvmInline
value class PermissionStateId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "Permission state IDs cannot be blank" } }

    companion object {
        val DEFAULT = PermissionStateId("permission-state")
    }
}

@Serializable
@JvmInline
value class GeotagMetadataId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "Geotag metadata IDs cannot be blank" } }
}

@Serializable
@JvmInline
value class UserPreferencesId(override val value: String) : StableIdentifier {
    init { require(value.isNotBlank()) { "User preferences IDs cannot be blank" } }

    companion object {
        val DEFAULT = UserPreferencesId("user-preferences")
    }
}
