package app.theia.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CameraBody(
    val id: CameraBodyId,
    val nameKey: String,
    val descriptionKey: String,
) {
    init {
        require(nameKey.isNotBlank()) { "Camera body name keys cannot be blank" }
        require(descriptionKey.isNotBlank()) { "Camera body description keys cannot be blank" }
    }
}

@Serializable
data class Lens(
    val id: LensId,
    val nameKey: String,
    val descriptionKey: String,
) {
    init {
        require(nameKey.isNotBlank()) { "Lens name keys cannot be blank" }
        require(descriptionKey.isNotBlank()) { "Lens description keys cannot be blank" }
    }
}

@Serializable
data class FilmStock(
    val id: FilmStockId,
    val nameKey: String,
    val descriptionKey: String,
    val collectionKey: String,
) {
    init {
        require(nameKey.isNotBlank()) { "Film stock name keys cannot be blank" }
        require(descriptionKey.isNotBlank()) { "Film stock description keys cannot be blank" }
        require(collectionKey.isNotBlank()) { "Film stock collection keys cannot be blank" }
    }
}

@Serializable
data class CaptureRecipe(
    val bodyId: CameraBodyId,
    val lensId: LensId,
    val filmStockId: FilmStockId,
    val id: CaptureRecipeId = CaptureRecipeId.forEquipment(bodyId, lensId, filmStockId),
) {
    init {
        require(id == CaptureRecipeId.forEquipment(bodyId, lensId, filmStockId)) {
            "Capture recipe ID must match its equipment combination"
        }
    }
}
