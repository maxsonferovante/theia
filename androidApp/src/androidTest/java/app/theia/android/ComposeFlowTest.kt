package app.theia.android

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.ComponentActivity
import app.theia.android.album.AlbumLabScreen
import app.theia.android.album.AlbumMetadataRepository
import app.theia.android.album.AlbumPhoto
import app.theia.android.settings.OnboardingScreen
import app.theia.android.settings.SettingsScreen
import app.theia.android.workbench.WorkbenchScreen
import app.theia.domain.catalog.BuiltInEquipmentCatalog
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.PermissionState
import app.theia.domain.model.UserPreferences
import app.theia.domain.port.DevicePermission
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = compose.activity

    @Test
    fun onboardingCanBeSkippedAndExposesItsBrandMarkLabel() {
        var skipped = false
        compose.setContent {
            OnboardingScreen(onGetStarted = {}, onSkip = { skipped = true })
        }

        compose.onNodeWithContentDescription(context.getString(R.string.onboarding_mark_description))
            .assertExists()
        compose.onNodeWithText(context.getString(R.string.onboarding_skip)).performClick()

        assertEquals(true, skipped)
    }

    @Test
    fun onboardingSkipRemainsAccessibleAndLargeTextCanScrollIntoView() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Companion.FontScale(2f)) {
                OnboardingScreen(onGetStarted = {}, onSkip = {})
            }
        }

        val skip = compose.onNodeWithText(context.getString(R.string.onboarding_skip))
        skip.assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun workbenchSelectionReturnsTheEquipmentCombinationShownByTheAccessiblePicker() {
        val bodies = BuiltInEquipmentCatalog.cameraBodies()
        val lenses = BuiltInEquipmentCatalog.lenses()
        val films = BuiltInEquipmentCatalog.filmStocks()
        val selected = AtomicReference<CaptureRecipe?>()
        compose.setContent {
            WorkbenchScreen(
                catalog = BuiltInEquipmentCatalog,
                selectedRecipe = null,
                onRecipeSelected = selected::set,
            )
        }

        val nextBodyLabel = context.getString(
            R.string.workbench_next_item,
            context.getString(R.string.workbench_body),
        )
        compose.onNodeWithContentDescription(nextBodyLabel).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.workbench_use_selected_setup))
            .performClick()

        assertEquals(
            CaptureRecipe(bodies[1].id, lenses[0].id, films[0].id),
            selected.get(),
        )
    }

    @Test
    fun settingsExposeDefaultsAndEmitPreferenceChanges() {
        val instantDevelopment = AtomicReference<Boolean?>()
        val geotagging = AtomicReference<Boolean?>()
        val shutterSound = AtomicReference<Boolean?>()
        compose.setContent {
            SettingsScreen(
                preferences = UserPreferences(languageTag = "en"),
                permissionState = PermissionState(),
                onInstantDevelopmentChanged = instantDevelopment::set,
                onGeotaggingChanged = geotagging::set,
                onShutterSoundChanged = shutterSound::set,
                onLanguageChanged = {},
                onRequestPermission = {},
                onOpenAppSettings = {},
            )
        }

        val instantLabel = context.getString(R.string.settings_instant_development)
        val geotagLabel = context.getString(R.string.settings_geotagging)
        val shutterLabel = context.getString(R.string.settings_shutter_sound)
        compose.onNodeWithContentDescription(instantLabel).assertIsOff().performClick()
        compose.onNodeWithContentDescription(geotagLabel).assertIsOff().performClick()
        compose.onNodeWithContentDescription(shutterLabel).assertIsOn().performClick()

        assertEquals(true, instantDevelopment.get())
        assertEquals(true, geotagging.get())
        assertEquals(false, shutterSound.get())
    }

    @Test
    fun albumSearchFiltersByCaptureYearAndGroupAndFavoriteChipsAreOperable() {
        val repository = testRepository()
        val first = photo("search-first", "Summer field", year = 2024, favorite = true, body = "body-01")
        val second = photo("search-second", "Winter station", year = 2025, body = "body-02")
        repository.upsert(first)
        repository.upsert(second)

        compose.setContent { AlbumLabScreen(repository = repository) }

        val searchLabel = context.getString(R.string.album_search)
        compose.onNodeWithText(searchLabel).performTextInput("2024")
        compose.onNodeWithText(first.title, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(second.title, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(searchLabel).performTextClearance()
        compose.onNodeWithText(context.getString(R.string.album_filter_favorites)).performClick()
        compose.onNodeWithText(first.title, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(second.title, useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithText(context.getString(R.string.album_filter_all)).performClick()
        compose.onNodeWithText(context.getString(R.string.album_group_body)).performClick()
        compose.onAllNodesWithText("body-01", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("body-02", useUnmergedTree = true).assertCountEquals(1)

        repository.remove(first.id)
        repository.remove(second.id)
    }

    @Test
    fun albumDeletionRequiresConfirmationAndMakesGalleryScopeExplicit() {
        val repository = testRepository()
        val capture = photo("delete-capture", "Keep this until confirmed", year = 2024)
        repository.upsert(capture)

        compose.setContent { AlbumLabScreen(repository = repository) }

        compose.onNodeWithText(capture.title, useUnmergedTree = true).performClick()
        compose.onNodeWithText(context.getString(R.string.album_delete)).performClick()
        compose.onNodeWithText(context.getString(R.string.album_delete_explanation)).assertExists()
        val galleryScope = compose.onNodeWithContentDescription(context.getString(R.string.album_delete_gallery_copy))
        galleryScope.assertExists()
        galleryScope.assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText(context.getString(R.string.album_cancel)).performClick()

        assertEquals(listOf(capture.id), repository.all().map(AlbumPhoto::id))
        repository.remove(capture.id)
    }

    private fun testRepository(): AlbumMetadataRepository {
        context.getSharedPreferences("theia_album_metadata", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        return AlbumMetadataRepository(context)
    }

    private fun photo(
        id: String,
        title: String,
        year: Int,
        favorite: Boolean = false,
        body: String = "body-01",
    ): AlbumPhoto {
        val capturedAt = Calendar.getInstance().apply {
            clear()
            set(year, Calendar.JULY, 15, 12, 0, 0)
        }.timeInMillis
        return AlbumPhoto(
            id = id,
            capturedAtEpochMillis = capturedAt,
            originalUri = "content://app.theia.test/media/$id",
            developedUri = "content://app.theia.test/developed/$id",
            cameraBodyId = body,
            lensId = "lens-01",
            filmStockId = "film-01",
            rollId = "roll-$id",
            isFavorite = favorite,
            title = title,
        )
    }
}
