package app.theia.android

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityAccessibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchNavigationKeepsVideoHeadingAndAudioToggleAccessible() {
        compose.activity.getSharedPreferences("theia_user_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithText(compose.activity.getString(R.string.onboarding_skip)).performClick()
        val shutter = compose.onNodeWithContentDescription(compose.activity.getString(R.string.camera_take_photo))
        shutter.assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(compose.activity.getString(R.string.camera_permission_explanation))
            .assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.camera_request_permission))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)

        val timerLabel = compose.activity.getString(R.string.camera_timer_three)
        compose.onNodeWithText(timerLabel)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performScrollTo()
            .performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        val gridLabel = compose.activity.getString(R.string.camera_grid)
        compose.onNodeWithText(gridLabel)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performScrollTo()
            .performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))

        compose.onNodeWithText(compose.activity.getString(R.string.open_album))
            .assertHeightIsAtLeast(48.dp)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.album_lab_title))
            .assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.album_rolls_title))
            .assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.album_roll_start))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(compose.activity.getString(R.string.album_back))
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        compose.onNodeWithText(compose.activity.getString(R.string.open_video))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performScrollTo()
            .performClick()

        compose.onNodeWithText(compose.activity.getString(R.string.video_capture_title))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.video_include_audio))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assertIsOff()
    }
}
