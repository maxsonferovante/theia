package app.theia.domain.model

import kotlinx.serialization.Serializable

/** Persistable roll state, including the recipe selected when the roll was created. */
@Serializable
data class FilmRollRecord(
    val roll: FilmRoll,
    val recipe: CaptureRecipe,
    val photos: List<PhotoAsset> = emptyList(),
) {
    init {
        require(photos.map { it.id } == roll.photoIds) {
            "Roll photo references must match the ordered roll exposure list"
        }
        require(photos.all { it.rollId == roll.id }) { "Every photo must belong to this roll" }
        require(photos.all { it.recipe == recipe }) { "Every photo must use the roll's fixed recipe" }
    }

    fun append(photo: PhotoAsset): FilmRollRecord {
        require(photo.rollId == roll.id) { "Photo must belong to this roll" }
        require(photo.recipe == recipe) { "Photo must use the roll's fixed recipe" }
        return copy(roll = roll.recordPhoto(photo.id), photos = photos + photo)
    }

    fun updatePhoto(photo: PhotoAsset): FilmRollRecord {
        require(photo.rollId == roll.id) { "Photo must belong to this roll" }
        require(photo.recipe == recipe) { "Photo must use the roll's fixed recipe" }
        require(photos.any { it.id == photo.id }) { "Photo must already belong to this roll" }
        return copy(photos = photos.map { if (it.id == photo.id) photo else it })
    }

    /** Reveals instant-developed photos before the rest of a roll is developed. */
    fun visiblePhotos(): List<PhotoAsset> =
        if (roll.developedAtEpochMillis != null) photos else photos.filter { it.developedGalleryLocation != null }
}
