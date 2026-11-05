package app.theia.android

import android.net.Uri
import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.os.LocaleList
import android.app.LocaleManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.theia.android.capture.CameraCaptureScreen
import app.theia.android.capture.VideoCaptureScreen
import app.theia.android.capture.writeRecipeMetadata
import app.theia.android.album.AlbumLabScreen
import app.theia.android.album.AlbumMediaKind
import app.theia.android.album.AlbumMetadataRepository
import app.theia.android.album.AlbumPhoto
import app.theia.android.settings.AndroidUserPreferencesStore
import app.theia.android.settings.OnboardingScreen
import app.theia.android.settings.SettingsScreen
import app.theia.android.settings.openApplicationPermissionSettings
import app.theia.android.workbench.RecipePickerSheet
import app.theia.android.roll.AndroidFilmRollRepository
import app.theia.android.roll.AndroidFilmRollService
import app.theia.android.geotag.AndroidGeotagService
import app.theia.android.geotag.AndroidPermissionPort
import app.theia.domain.catalog.BuiltInEquipmentCatalog
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmRollCapacity
import app.theia.domain.model.PermissionState
import app.theia.domain.model.PermissionStatus
import app.theia.domain.model.UserPreferences
import app.theia.domain.port.DevicePermission
import androidx.core.content.ContextCompat
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred

private enum class AppDestination { ONBOARDING, CAMERA, VIDEO, ALBUM, SETTINGS }

class MainActivity : ComponentActivity() {
    var volumeShutterEnabled = false
    var volumeShutterRequests by mutableIntStateOf(0)
        private set

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolumeShutter = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (volumeShutterEnabled && isVolumeShutter && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            volumeShutterRequests++
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val brandNavy = Color.rgb(24, 35, 68)
        window.setBackgroundDrawable(ColorDrawable(brandNavy))
        window.statusBarColor = brandNavy
        window.navigationBarColor = brandNavy
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent { TheiaAppScreen() }
    }
}

@Composable
private fun TheiaAppScreen() {
    val context = LocalContext.current
    val preferencesStore = remember(context) { AndroidUserPreferencesStore(context) }
    val albumRepository = remember(context) { AlbumMetadataRepository(context) }
    val rollRepository = remember(context) { AndroidFilmRollRepository(context) }
    val rollService = remember(context, albumRepository, rollRepository) {
        AndroidFilmRollService(context, albumRepository, rollRepository)
    }
    val coroutineScope = rememberCoroutineScope()
    var preferences by remember(preferencesStore) { mutableStateOf(preferencesStore.load()) }
    var hasCompletedOnboarding by remember(preferencesStore) { mutableStateOf(preferencesStore.hasCompletedOnboarding()) }
    var permissionRefresh by remember { mutableStateOf(0) }
    var pendingPermissionResult by remember { mutableStateOf<CompletableDeferred<PermissionStatus>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh++
        pendingPermissionResult?.complete(if (it) PermissionStatus.GRANTED else PermissionStatus.DENIED)
        pendingPermissionResult = null
    }
    val requestDevicePermission: suspend (DevicePermission) -> PermissionStatus = remember(context, permissionLauncher) {
        { permission ->
            val manifestPermission = permission.manifestPermission()
            if (ContextCompat.checkSelfPermission(context, manifestPermission) == PackageManager.PERMISSION_GRANTED) {
                PermissionStatus.GRANTED
            } else {
                context.getSharedPreferences("theia_permission_requests", Context.MODE_PRIVATE)
                    .edit().putBoolean(manifestPermission, true).apply()
                val result = CompletableDeferred<PermissionStatus>()
                pendingPermissionResult = result
                permissionLauncher.launch(manifestPermission)
                result.await()
            }
        }
    }
    val permissionPort = remember(context, requestDevicePermission) {
        AndroidPermissionPort(context, requestDevicePermission)
    }
    val geotagService = remember(context, permissionPort) { AndroidGeotagService(context, permissionPort) }
    val initialRecipe = remember {
        CaptureRecipe(
            bodyId = BuiltInEquipmentCatalog.cameraBodies().first().id,
            lensId = BuiltInEquipmentCatalog.lenses().first().id,
            filmStockId = BuiltInEquipmentCatalog.filmStocks().first().id,
        )
    }
    var recipe by remember { mutableStateOf(initialRecipe) }
    var recipeDraft by remember { mutableStateOf(initialRecipe) }
    var destination by remember { mutableStateOf(if (hasCompletedOnboarding) AppDestination.CAMERA else AppDestination.ONBOARDING) }
    var recipePickerVisible by remember { mutableStateOf(false) }
    var pendingRecipe by remember { mutableStateOf<CaptureRecipe?>(null) }
    var pendingRollExposureCount by remember { mutableIntStateOf(0) }
    var latestPhoto by remember { mutableStateOf<Uri?>(null) }
    var latestPhotoVisible by remember { mutableStateOf(false) }
    var remainingExposures by remember { mutableStateOf<Int?>(null) }
    var cameraShutterAvailable by remember { mutableStateOf(false) }

    SideEffect {
        (context as? MainActivity)?.volumeShutterEnabled =
            destination == AppDestination.CAMERA && cameraShutterAvailable && !recipePickerVisible && pendingRecipe == null
    }

    LaunchedEffect(destination, rollService) {
        if (destination == AppDestination.CAMERA) {
            val activeRoll = rollService.activeRoll()
            if (activeRoll != null) {
                recipe = activeRoll.recipe
                remainingExposures = activeRoll.roll.capacity.exposures - activeRoll.roll.photoIds.size
            } else {
                remainingExposures = null
            }
            val photoId = latestPhoto?.toString()
            latestPhotoVisible = photoId != null && photoId in rollService.visiblePhotoIds()
        }
    }

    BackHandler(destination != AppDestination.CAMERA) {
        if (destination == AppDestination.ONBOARDING) {
            preferencesStore.markOnboardingCompleted()
            hasCompletedOnboarding = true
        }
        destination = AppDestination.CAMERA
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = androidx.compose.ui.graphics.Color(0xFF182344),
        ) {
            when (destination) {
                AppDestination.ONBOARDING -> OnboardingScreen(
                    onGetStarted = {
                        preferencesStore.markOnboardingCompleted()
                        hasCompletedOnboarding = true
                        destination = AppDestination.CAMERA
                    },
                    onSkip = {
                        preferencesStore.markOnboardingCompleted()
                        hasCompletedOnboarding = true
                        destination = AppDestination.CAMERA
                    },
                )
                AppDestination.CAMERA -> Column(Modifier.fillMaxSize()) {
                    CameraEntryBar(
                        recipe = if (recipePickerVisible) recipeDraft else recipe,
                        onOpenRecipe = {
                            recipeDraft = recipe
                            recipePickerVisible = true
                        },
                        onOpenAlbum = { destination = AppDestination.ALBUM },
                        onOpenSettings = { destination = AppDestination.SETTINGS },
                    )
                    CameraCaptureScreen(
                        recipe = if (recipePickerVisible) recipeDraft else recipe,
                        externalShutterRequest = (context as? MainActivity)?.volumeShutterRequests ?: 0,
                        onShutterAvailabilityChanged = { cameraShutterAvailable = it },
                        remainingExposures = remainingExposures,
                        latestPhotoVisible = latestPhotoVisible,
                        shutterSoundEnabled = preferences.shutterSoundEnabled,
                        onOpenVideo = { destination = AppDestination.VIDEO },
                        onPhotoSaved = { uri, recipe ->
                            latestPhoto = uri
                            latestPhotoVisible = false
                            writeRecipeMetadata(context.contentResolver, uri, recipe)
                            val capturedAtEpochMillis = System.currentTimeMillis()
                            coroutineScope.launch {
                                runCatching {
                                    val active = rollService.activeRoll()
                                    if (active == null || active.recipe != recipe) {
                                        rollService.createRoll(recipe, FilmRollCapacity.EXPOSURES_24, capturedAtEpochMillis)
                                    }
                                    val recordedPhoto = rollService.recordCapture(
                                        originalUri = uri,
                                        recipe = recipe,
                                        capturedAtEpochMillis = capturedAtEpochMillis,
                                        developInstantly = preferences.instantDevelopment,
                                    )
                                    remainingExposures = rollService.remainingExposures()
                                    latestPhotoVisible = recordedPhoto != null &&
                                        recordedPhoto.id.value in rollService.visiblePhotoIds()
                                }.onFailure { failure ->
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.album_roll_action_failed,
                                            failure.localizedMessage ?: context.getString(R.string.album_rolls_title),
                                        ),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (recipePickerVisible) {
                        RecipePickerSheet(
                            catalog = BuiltInEquipmentCatalog,
                            selectedRecipe = recipeDraft,
                            onDismiss = {
                                recipeDraft = recipe
                                recipePickerVisible = false
                            },
                            onDraftChange = { recipeDraft = it },
                            onApply = { selected ->
                                if (selected == recipe) {
                                    recipeDraft = recipe
                                    recipePickerVisible = false
                                } else {
                                    coroutineScope.launch {
                                        val active = rollService.activeRoll()
                                        if (active != null && active.recipe != selected && active.roll.photoIds.isNotEmpty()) {
                                            pendingRecipe = selected
                                            pendingRollExposureCount = active.roll.photoIds.size
                                            recipeDraft = recipe
                                            recipePickerVisible = false
                                        } else {
                                            runCatching {
                                                if (active != null && active.recipe != selected) {
                                                    rollService.createRoll(selected, FilmRollCapacity.EXPOSURES_24)
                                                }
                                                rollService.remainingExposures()
                                            }.onSuccess {
                                                recipe = selected
                                                recipeDraft = selected
                                                remainingExposures = it
                                                recipePickerVisible = false
                                            }.onFailure { failure ->
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.album_roll_action_failed, failure.localizedMessage ?: context.getString(R.string.album_rolls_title)),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                AppDestination.VIDEO -> VideoCaptureScreen(
                    recipe = recipe,
                    onVideoSaved = { result, selectedRecipe ->
                        albumRepository.upsert(
                            AlbumPhoto(
                                id = result.uri.toString(),
                                capturedAtEpochMillis = result.capturedAtEpochMillis,
                                originalUri = result.uri.toString(),
                                cameraBodyId = selectedRecipe.bodyId.value,
                                lensId = selectedRecipe.lensId.value,
                                filmStockId = selectedRecipe.filmStockId.value,
                                mediaKind = AlbumMediaKind.VIDEO,
                                durationMillis = result.durationMillis,
                                notes = buildList {
                                    add("recipe=${selectedRecipe.id.value}")
                                    result.framesPerSecond?.let { add("fps=$it") }
                                    add("audio=${result.includesAudio}")
                                }.joinToString(";"),
                            ),
                        )
                    },
                    onBack = { destination = AppDestination.CAMERA },
                )

                AppDestination.ALBUM -> AlbumLabScreen(
                    repository = albumRepository,
                    rollService = rollService,
                    selectedRecipe = recipe,
                    geotagService = geotagService,
                    userPreferences = preferences,
                    onBack = { destination = AppDestination.CAMERA },
                    modifier = Modifier.fillMaxSize(),
                )

                AppDestination.SETTINGS -> {
                    permissionRefresh
                    SettingsScreen(
                        preferences = preferences,
                        permissionState = context.permissionState(),
                        onInstantDevelopmentChanged = { value -> preferences = preferences.copy(instantDevelopment = value).also(preferencesStore::save) },
                        onGeotaggingChanged = { value -> preferences = preferences.copy(geotaggingEnabled = value).also(preferencesStore::save) },
                        onShutterSoundChanged = { value -> preferences = preferences.copy(shutterSoundEnabled = value).also(preferencesStore::save) },
                        onLanguageChanged = { value ->
                            preferences = preferences.copy(languageTag = value).also(preferencesStore::save)
                            context.applyLanguage(value)
                            (context as? Activity)?.recreate()
                        },
                        onRequestPermission = { permission ->
                            context.getSharedPreferences("theia_permission_requests", Context.MODE_PRIVATE)
                                .edit().putBoolean(permission.manifestPermission(), true).apply()
                            permissionLauncher.launch(permission.manifestPermission())
                        },
                        onOpenAppSettings = { openApplicationPermissionSettings(context) },
                        modifier = Modifier.fillMaxSize(),
                        onBack = { destination = AppDestination.CAMERA },
                    )
                }
            }

            pendingRecipe?.let { nextRecipe ->
                AlertDialog(
                    onDismissRequest = { pendingRecipe = null },
                    title = { Text(stringResource(R.string.recipe_roll_switch_title)) },
                    text = {
                        val count = pluralStringResource(R.plurals.recipe_roll_switch_exposure_count, pendingRollExposureCount, pendingRollExposureCount)
                        Text(stringResource(R.string.recipe_roll_switch_message, count))
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                runCatching {
                                    val active = rollService.activeRoll()
                                    if (active != null && active.recipe != nextRecipe) {
                                        rollService.createRoll(nextRecipe, FilmRollCapacity.EXPOSURES_24)
                                    }
                                    rollService.remainingExposures()
                                }.onSuccess {
                                    recipe = nextRecipe
                                    recipeDraft = nextRecipe
                                    remainingExposures = it
                                    pendingRecipe = null
                                }.onFailure { failure ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.album_roll_action_failed, failure.localizedMessage ?: context.getString(R.string.album_rolls_title)),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }) { Text(stringResource(R.string.recipe_roll_switch_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingRecipe = null }) { Text(stringResource(R.string.recipe_roll_switch_cancel)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun CameraEntryBar(
    recipe: CaptureRecipe,
    onOpenRecipe: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val body = BuiltInEquipmentCatalog.cameraBodies().firstOrNull { it.id == recipe.bodyId }?.nameKey?.let(context::resolveCatalogKey)
    val lens = BuiltInEquipmentCatalog.lenses().firstOrNull { it.id == recipe.lensId }?.nameKey?.let(context::resolveCatalogKey)
    val film = BuiltInEquipmentCatalog.filmStocks().firstOrNull { it.id == recipe.filmStockId }?.nameKey?.let(context::resolveCatalogKey)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onOpenRecipe,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                contentDescription = context.getString(R.string.recipe_sheet_open_accessibility, body.orEmpty(), lens.orEmpty(), film.orEmpty())
            },
            colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor(0xFFE7D7B7)),
        ) {
            Column {
                Text(stringResource(R.string.recipe_sheet_current_label), color = ComposeColor(0xFFB6A58C), fontSize = 10.sp)
                Text(listOfNotNull(body, lens, film).joinToString(" · "), maxLines = 1, color = ComposeColor(0xFFE7D7B7))
            }
        }
        IconButton(onClick = onOpenAlbum, modifier = Modifier.size(48.dp).semantics { contentDescription = context.getString(R.string.open_album) }) {
            Text("▦", fontSize = 21.sp, color = ComposeColor(0xFFE7D7B7))
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp).semantics { contentDescription = context.getString(R.string.open_settings) }) {
            Text("⚙", fontSize = 21.sp, color = ComposeColor(0xFFE7D7B7))
        }
    }
}

private fun android.content.Context.resolveCatalogKey(key: String): String {
    val resourceName = key.replace('.', '_').replace('-', '_')
    val resourceId = resources.getIdentifier(resourceName, "string", packageName)
    return if (resourceId == 0) key.replace('_', ' ').replace('-', ' ') else getString(resourceId)
}

private fun DevicePermission.manifestPermission(): String = when (this) {
    DevicePermission.CAMERA -> Manifest.permission.CAMERA
    DevicePermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
    DevicePermission.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
    DevicePermission.PHOTO_LIBRARY -> if (android.os.Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

private fun android.content.Context.permissionState(): PermissionState = PermissionState(
    camera = permissionStatus(Manifest.permission.CAMERA),
    photoLibrary = permissionStatus(DevicePermission.PHOTO_LIBRARY.manifestPermission()),
    microphone = permissionStatus(Manifest.permission.RECORD_AUDIO),
    location = permissionStatus(Manifest.permission.ACCESS_COARSE_LOCATION),
)

private fun android.content.Context.permissionStatus(permission: String): PermissionStatus {
    if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return PermissionStatus.GRANTED
    val requested = getSharedPreferences("theia_permission_requests", Context.MODE_PRIVATE)
        .getBoolean(permission, false)
    if (!requested) return PermissionStatus.NOT_REQUESTED
    val shouldExplain = (this as? Activity)?.shouldShowRequestPermissionRationale(permission) == true
    return if (shouldExplain) PermissionStatus.DENIED else PermissionStatus.RESTRICTED
}

@Composable
private fun PlaceholderFeatureScreen(title: String, body: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.back_to_preview))
        }
    }
}

private fun Context.applyLanguage(languageTag: String) {
    val locale = java.util.Locale.forLanguageTag(languageTag)
    if (Build.VERSION.SDK_INT >= 33) {
        getSystemService(LocaleManager::class.java).applicationLocales = LocaleList(locale)
    } else {
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
