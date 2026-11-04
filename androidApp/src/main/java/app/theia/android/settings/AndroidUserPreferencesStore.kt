package app.theia.android.settings

import android.content.Context
import app.theia.domain.model.UserPreferences
import java.util.Locale

/** Small device-local preference adapter. It does not request permissions or access media. */
class AndroidUserPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): UserPreferences = UserPreferences(
        languageTag = preferences.getString(KEY_LANGUAGE, null) ?: deviceLanguageTag(),
        instantDevelopment = preferences.getBoolean(KEY_INSTANT_DEVELOPMENT, false),
        geotaggingEnabled = preferences.getBoolean(KEY_GEOTAGGING, false),
        shutterSoundEnabled = preferences.getBoolean(KEY_SHUTTER_SOUND, true),
    )

    fun save(value: UserPreferences) {
        preferences.edit()
            .putString(KEY_LANGUAGE, normalizeLanguageTag(value.languageTag))
            .putBoolean(KEY_INSTANT_DEVELOPMENT, value.instantDevelopment)
            .putBoolean(KEY_GEOTAGGING, value.geotaggingEnabled)
            .putBoolean(KEY_SHUTTER_SOUND, value.shutterSoundEnabled)
            .apply()
    }

    fun hasCompletedOnboarding(): Boolean = preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun markOnboardingCompleted() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    private fun deviceLanguageTag(): String =
        if (Locale.getDefault().language.equals("pt", ignoreCase = true)) LANGUAGE_PORTUGUESE else LANGUAGE_ENGLISH

    private fun normalizeLanguageTag(tag: String): String =
        if (tag.equals(LANGUAGE_PORTUGUESE, ignoreCase = true) || tag.startsWith("pt-", ignoreCase = true)) {
            LANGUAGE_PORTUGUESE
        } else {
            LANGUAGE_ENGLISH
        }

    private companion object {
        const val FILE_NAME = "theia_user_preferences"
        const val KEY_LANGUAGE = "language_tag"
        const val KEY_INSTANT_DEVELOPMENT = "instant_development"
        const val KEY_GEOTAGGING = "geotagging_enabled"
        const val KEY_SHUTTER_SOUND = "shutter_sound_enabled"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val LANGUAGE_PORTUGUESE = "pt-BR"
        const val LANGUAGE_ENGLISH = "en"
    }
}
