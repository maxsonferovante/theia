package app.theia.android.album

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-local metadata only. Image bytes remain in the user's media library. */
class AlbumMetadataRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<AlbumPhoto> = runCatching {
        val encoded = preferences.getString(KEY_PHOTOS, null) ?: return emptyList()
        val json = JSONArray(encoded)
        buildList {
            for (index in 0 until json.length()) {
                runCatching { json.getJSONObject(index).toAlbumPhoto() }.getOrNull()?.let(::add)
            }
        }.sortedByDescending { it.capturedAtEpochMillis }
    }.getOrDefault(emptyList())

    @Synchronized
    fun upsert(photo: AlbumPhoto) {
        persist(all().filterNot { it.id == photo.id } + photo)
    }

    @Synchronized
    fun update(photo: AlbumPhoto) {
        if (all().any { it.id == photo.id }) upsert(photo)
    }

    @Synchronized
    fun remove(id: String) {
        persist(all().filterNot { it.id == id })
    }

    private fun persist(photos: List<AlbumPhoto>) {
        val json = JSONArray()
        photos.distinctBy { it.id }.sortedByDescending { it.capturedAtEpochMillis }.forEach { photo ->
            json.put(photo.toJson())
        }
        check(preferences.edit().putString(KEY_PHOTOS, json.toString()).commit()) {
            "Could not save local album metadata"
        }
    }

    private fun AlbumPhoto.toJson() = JSONObject().apply {
        put("id", id)
        put("capturedAtEpochMillis", capturedAtEpochMillis)
        put("originalUri", originalUri)
        putNullable("developedUri", developedUri)
        put("cameraBodyId", cameraBodyId)
        put("lensId", lensId)
        put("filmStockId", filmStockId)
        putNullable("rollId", rollId)
        put("isFavorite", isFavorite)
        put("title", title)
        put("notes", notes)
        put("mediaKind", mediaKind.name)
        durationMillis?.let { put("durationMillis", it) }
    }

    private fun JSONObject.toAlbumPhoto() = AlbumPhoto(
        id = getString("id"),
        capturedAtEpochMillis = getLong("capturedAtEpochMillis"),
        originalUri = getString("originalUri"),
        developedUri = nullableString("developedUri"),
        cameraBodyId = getString("cameraBodyId"),
        lensId = getString("lensId"),
        filmStockId = getString("filmStockId"),
        rollId = nullableString("rollId"),
        isFavorite = optBoolean("isFavorite", false),
        title = optString("title", ""),
        notes = optString("notes", ""),
        mediaKind = runCatching { AlbumMediaKind.valueOf(optString("mediaKind", AlbumMediaKind.PHOTO.name)) }
            .getOrDefault(AlbumMediaKind.PHOTO),
        durationMillis = if (has("durationMillis") && !isNull("durationMillis")) optLong("durationMillis") else null,
    )

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private companion object {
        const val PREFERENCES_NAME = "theia_album_metadata"
        const val KEY_PHOTOS = "photos"
    }
}
