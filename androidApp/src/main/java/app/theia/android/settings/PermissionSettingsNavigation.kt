package app.theia.android.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Opens the app detail page so a user can recover a denied or restricted permission. */
fun openApplicationPermissionSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
