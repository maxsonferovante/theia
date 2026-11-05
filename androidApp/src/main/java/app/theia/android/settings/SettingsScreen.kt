package app.theia.android.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.theia.android.R
import app.theia.domain.model.PermissionState
import app.theia.domain.model.PermissionStatus
import app.theia.domain.model.UserPreferences
import app.theia.domain.port.DevicePermission

private val SettingsSolarGold = Color(0xFFEAAE35)
private val SettingsDeepSky = Color(0xFF182344)
private val SettingsCosmicViolet = Color(0xFF2D2D57)
private val SettingsIvory = Color(0xFFE7D7B7)
private val SettingsStone = Color(0xFFB6A58C)

/** Preferences and permission recovery; onboarding is intentionally omitted. */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    permissionState: PermissionState,
    onInstantDevelopmentChanged: (Boolean) -> Unit,
    onGeotaggingChanged: (Boolean) -> Unit,
    onShutterSoundChanged: (Boolean) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onRequestPermission: (DevicePermission) -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize(), color = SettingsDeepSky, contentColor = SettingsIvory) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back_to_preview)) }
                    Text(
                        text = stringResource(R.string.settings_title),
                        color = SettingsIvory,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Text(stringResource(R.string.settings_intro), color = SettingsStone, fontSize = 14.sp)

                SettingsSection(title = stringResource(R.string.settings_preferences)) {
                    PreferenceSwitch(
                        title = stringResource(R.string.settings_instant_development),
                        description = stringResource(R.string.settings_instant_development_description),
                        checked = preferences.instantDevelopment,
                        onCheckedChange = onInstantDevelopmentChanged,
                    )
                    PreferenceSwitch(
                        title = stringResource(R.string.settings_geotagging),
                        description = stringResource(R.string.settings_geotagging_description),
                        checked = preferences.geotaggingEnabled,
                        onCheckedChange = onGeotaggingChanged,
                    )
                    PreferenceSwitch(
                        title = stringResource(R.string.settings_shutter_sound),
                        description = stringResource(R.string.settings_shutter_sound_description),
                        checked = preferences.shutterSoundEnabled,
                        onCheckedChange = onShutterSoundChanged,
                    )
                    LanguagePicker(preferences.languageTag, onLanguageChanged)
                }

                SettingsSection(title = stringResource(R.string.settings_permissions)) {
                    Text(
                        text = stringResource(R.string.settings_permission_privacy_note),
                        color = SettingsStone,
                        fontSize = 13.sp,
                    )
                    PermissionRow(
                        title = stringResource(R.string.settings_camera_permission),
                        description = stringResource(R.string.settings_camera_permission_description),
                        permission = DevicePermission.CAMERA,
                        status = permissionState.camera,
                        onRequestPermission = onRequestPermission,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                    PermissionRow(
                        title = stringResource(R.string.settings_photos_permission),
                        description = stringResource(R.string.settings_photos_permission_description),
                        permission = DevicePermission.PHOTO_LIBRARY,
                        status = permissionState.photoLibrary,
                        onRequestPermission = onRequestPermission,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                    PermissionRow(
                        title = stringResource(R.string.settings_microphone_permission),
                        description = stringResource(R.string.settings_microphone_permission_description),
                        permission = DevicePermission.MICROPHONE,
                        status = permissionState.microphone,
                        onRequestPermission = onRequestPermission,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                    PermissionRow(
                        title = stringResource(R.string.settings_location_permission),
                        description = stringResource(R.string.settings_location_permission_description),
                        permission = DevicePermission.LOCATION,
                        status = permissionState.location,
                        onRequestPermission = onRequestPermission,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SettingsCosmicViolet, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = SettingsSolarGold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = SettingsIvory, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = SettingsStone, fontSize = 12.sp)
        }
        val state = stringResource(if (checked) R.string.settings_state_on else R.string.settings_state_off)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = title
                stateDescription = state
            },
        )
    }
}

@Composable
private fun LanguagePicker(languageTag: String, onLanguageChanged: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(stringResource(R.string.settings_language), color = SettingsIvory, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(stringResource(R.string.settings_language_description), color = SettingsStone, fontSize = 12.sp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LanguageOption("pt-BR", stringResource(R.string.settings_language_portuguese), languageTag, onLanguageChanged)
            LanguageOption("en", stringResource(R.string.settings_language_english), languageTag, onLanguageChanged)
        }
    }
}

@Composable
private fun LanguageOption(tag: String, label: String, selectedTag: String, onSelect: (String) -> Unit) {
    FilterChip(
        selected = selectedTag.equals(tag, ignoreCase = true),
        onClick = { onSelect(tag) },
        modifier = Modifier.heightIn(min = 48.dp),
        label = { Text(label) },
    )
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    permission: DevicePermission,
    status: PermissionStatus,
    onRequestPermission: (DevicePermission) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SettingsDeepSky.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = SettingsIvory, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(status.labelResource()), color = SettingsSolarGold, fontSize = 11.sp)
        }
        Text(description, color = SettingsStone, fontSize = 12.sp)
        if (status != PermissionStatus.GRANTED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onRequestPermission(permission) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.settings_permission_request))
                }
                if (status == PermissionStatus.DENIED || status == PermissionStatus.RESTRICTED) {
                    TextButton(onClick = onOpenAppSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.settings_permission_recovery))
                    }
                }
            }
        }
    }
}

private fun PermissionStatus.labelResource(): Int = when (this) {
    PermissionStatus.NOT_REQUESTED -> R.string.settings_permission_not_requested
    PermissionStatus.GRANTED -> R.string.settings_permission_granted
    PermissionStatus.DENIED -> R.string.settings_permission_denied
    PermissionStatus.RESTRICTED -> R.string.settings_permission_restricted
}
