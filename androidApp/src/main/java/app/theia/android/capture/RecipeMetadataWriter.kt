package app.theia.android.capture

import android.content.ContentResolver
import android.net.Uri
import android.media.ExifInterface
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.catalog.BuiltInFilmVisualProfiles
import org.json.JSONObject

/** Writes a small, versioned recipe to JPEG EXIF when the provider supports updates. */
fun writeRecipeMetadata(contentResolver: ContentResolver, photoUri: Uri, recipe: CaptureRecipe): Boolean =
    runCatching {
        val descriptor = requireNotNull(contentResolver.openFileDescriptor(photoUri, "rw"))
        descriptor.use {
            val exif = ExifInterface(it.fileDescriptor)
            exif.setAttribute(
                ExifInterface.TAG_USER_COMMENT,
                JSONObject()
                    .put("version", 1)
                    .put("recipe", recipe.id.value)
                    .put("body", recipe.bodyId.value)
                    .put("lens", recipe.lensId.value)
                    .put("lens_profile_version", recipe.lensProfileVersion)
                    .put("rendering_algorithm_id", BuiltInFilmVisualProfiles.RENDERING_ALGORITHM_ID)
                    .put("rendering_schema_version", recipe.renderingSchemaVersion)
                    .put("film", recipe.filmStockId.value)
                    .put("filter_applied", false)
                    .put("filter_status", "original_unfiltered")
                    .toString(),
            )
            exif.saveAttributes()
        }
    }.isSuccess
