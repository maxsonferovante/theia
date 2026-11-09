package app.theia.android.album

import android.os.Environment
import java.io.File

/** Shared public-gallery destinations for newly captured and developed photos. */
internal object TheiaGalleryFolders {
    const val ROOT = "Théia"
    const val ORIGINALS = "Originais"
    const val REVEALED = "Reveladas"

    fun relativePath(folder: String): String =
        "${Environment.DIRECTORY_PICTURES}/$ROOT/$folder"

    fun legacyDirectory(folder: String): File =
        File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ROOT), folder)
}
