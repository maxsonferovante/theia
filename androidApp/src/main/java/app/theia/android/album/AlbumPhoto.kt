package app.theia.android.album

enum class AlbumMediaKind { PHOTO, VIDEO }

/** Local app metadata for one captured exposure. Gallery URIs point to the untouched original and optional developed copy. */
data class AlbumPhoto(
    val id: String,
    val capturedAtEpochMillis: Long,
    val originalUri: String,
    val developedUri: String? = null,
    val cameraBodyId: String,
    val lensId: String,
    val filmStockId: String,
    val rollId: String? = null,
    val isFavorite: Boolean = false,
    val title: String = "",
    val notes: String = "",
    val mediaKind: AlbumMediaKind = AlbumMediaKind.PHOTO,
    val durationMillis: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(capturedAtEpochMillis >= 0)
        require(originalUri.isNotBlank())
        require(cameraBodyId.isNotBlank() && lensId.isNotBlank() && filmStockId.isNotBlank())
        require(durationMillis == null || durationMillis > 0)
        require(mediaKind != AlbumMediaKind.VIDEO || durationMillis != null)
    }
}
