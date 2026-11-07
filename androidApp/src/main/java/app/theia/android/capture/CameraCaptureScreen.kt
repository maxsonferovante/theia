package app.theia.android.capture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Range
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.theia.android.R
import app.theia.domain.model.CaptureRecipe
import com.google.common.util.concurrent.ListenableFuture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CameraGold = Color(0xFFEAAE35)
private val CameraDeepSky = Color(0xFF182344)
private val CameraIvory = Color(0xFFE7D7B7)
private val CameraStone = Color(0xFFB6A58C)

/**
 * Native CameraX still-capture surface. The caller opens this screen only when
 * the user enters the camera flow; that is when the contextual permission is
 * requested. Captured originals are immediately published to MediaStore.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CameraCaptureScreen(
    recipe: CaptureRecipe,
    onPhotoSaved: (Uri, CaptureRecipe) -> Unit,
    onOpenVideo: () -> Unit = {},
    externalShutterRequest: Int = 0,
    onShutterAvailabilityChanged: (Boolean) -> Unit = {},
    remainingExposures: Int? = null,
    latestPhotoVisible: Boolean = true,
    shutterSoundEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shutterSound = remember(context) { MediaActionSound() }
    DisposableEffect(shutterSound) { onDispose { shutterSound.release() } }
    LaunchedEffect(shutterSound, shutterSoundEnabled) {
        if (shutterSoundEnabled) shutterSound.load(MediaActionSound.SHUTTER_CLICK)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    var permissionsGranted by remember(context) {
        mutableStateOf(requiredPermissions.all { context.hasPermission(it) })
    }
    var permissionWasDenied by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraSession by remember { mutableStateOf<CameraSession?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var visualEffectError by remember { mutableStateOf<String?>(null) }
    var retryCamera by remember { mutableIntStateOf(0) }
    var captureInProgress by remember { mutableStateOf(false) }
    var captureMessage by remember { mutableStateOf<Int?>(null) }
    var flashChoice by remember { mutableStateOf(FlashChoice.AUTO) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var timerCountdown by remember { mutableIntStateOf(0) }
    var gridEnabled by remember { mutableStateOf(false) }
    var cameraControlsExpanded by remember { mutableStateOf(false) }
    var latestPhoto by remember { mutableStateOf<Uri?>(null) }
    var focusMessage by remember { mutableStateOf<Int?>(null) }
    var zoomRatio by remember(cameraSession) {
        mutableFloatStateOf(cameraSession?.camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f)
    }
    var exposureIndex by remember(cameraSession) {
        mutableIntStateOf(cameraSession?.camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: 0)
    }
    val cameraZoomState = cameraSession?.camera?.cameraInfo?.zoomState?.value
    val exposureRange = cameraSession?.camera?.cameraInfo?.exposureState?.exposureCompensationRange
    val flashAvailable = cameraSession?.camera?.cameraInfo?.hasFlashUnit() == true
    val coroutineScope = rememberCoroutineScope()
    val switchCameraDescription = stringResource(
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            R.string.camera_switch_to_front
        } else {
            R.string.camera_switch_to_back
        },
    )
    val takePhotoDescription = stringResource(R.string.camera_take_photo)
    val switchStateDescription = stringResource(
        if (lensFacing == CameraSelector.LENS_FACING_BACK) R.string.camera_back_camera_active else R.string.camera_front_camera_active,
    )
    val savingStateDescription = stringResource(R.string.camera_status_saving)
    val countdownStatus = stringResource(R.string.camera_timer_countdown, timerCountdown)
    val focusHint = stringResource(R.string.camera_tap_to_focus)
    val zoomLabel = stringResource(R.string.camera_zoom)
    val exposureLabel = stringResource(R.string.camera_exposure)
    val focusSavedMessage = stringResource(R.string.camera_focus_applied)
    val previewDescription = "${stringResource(R.string.camera_live_preview_description)}. $focusHint"
    val zoomStateDescription = stringResource(R.string.camera_zoom_value, String.format(Locale.getDefault(), "%.1f", zoomRatio))
    val exposureStateDescription = stringResource(R.string.camera_exposure_value, exposureIndex)
    val waitingStatus = stringResource(R.string.camera_status_starting)
    val cameraChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = CameraGold,
        selectedLabelColor = CameraDeepSky,
        containerColor = CameraDeepSky.copy(alpha = 0.72f),
        labelColor = CameraIvory,
    )

    SideEffect {
        onShutterAvailabilityChanged(permissionsGranted && cameraSession != null && cameraError == null && visualEffectError == null)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionsGranted = requiredPermissions.all { results[it] == true || context.hasPermission(it) }
        permissionWasDenied = !permissionsGranted
    }

    DisposableEffect(lifecycleOwner, requiredPermissions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsGranted = requiredPermissions.all(context::hasPermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(previewView, permissionsGranted, lensFacing, lifecycleOwner, retryCamera) {
        val view = previewView
        if (view == null || !permissionsGranted) {
            cameraSession = null
            return@LaunchedEffect
        }

        cameraSession?.let { old ->
            old.provider.unbind(old.preview, old.imageCapture)
            old.effect.release()
        }
        cameraSession = null
        cameraError = null
        visualEffectError = null

        try {
            val provider = ProcessCameraProvider.getInstance(context).await(ContextCompat.getMainExecutor(context))
            val requestedSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val selector = if (provider.hasCamera(requestedSelector)) {
                requestedSelector
            } else {
                val alternateFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
                val alternateSelector = CameraSelector.Builder().requireLensFacing(alternateFacing).build()
                if (!provider.hasCamera(alternateSelector)) {
                    error(context.getString(R.string.camera_unavailable))
                }
                lensFacing = alternateFacing
                return@LaunchedEffect
            }

            fun bind(prefer30Fps: Boolean): CameraSession {
                val previewBuilder = Preview.Builder()
                if (prefer30Fps) previewBuilder.setTargetFrameRate(Range(30, 30))
                val preview = previewBuilder
                    .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                    .also { it.surfaceProvider = view.surfaceProvider }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                            .build(),
                    )
                    .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                val effect = FilmPreviewEffect(recipe, includeVideoTarget = false) { _ ->
                    ContextCompat.getMainExecutor(context).execute {
                        visualEffectError = context.getString(R.string.camera_visual_effect_failed)
                    }
                }
                try {
                    val group = androidx.camera.core.UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(imageCapture)
                        .addEffect(effect)
                        .apply { view.viewPort?.let(::setViewPort) }
                        .build()
                    val camera = provider.bindToLifecycle(lifecycleOwner, selector, group)
                    return CameraSession(provider, preview, imageCapture, camera, effect)
                } catch (error: Exception) {
                    runCatching { provider.unbind(preview, imageCapture) }
                        .exceptionOrNull()
                        ?.let(error::addSuppressed)
                    effect.release()
                    throw error
                }
            }
            cameraSession = try {
                bind(prefer30Fps = true)
            } catch (preferredFailure: Exception) {
                try {
                    bind(prefer30Fps = false)
                } catch (fallbackFailure: Exception) {
                    fallbackFailure.addSuppressed(preferredFailure)
                    throw fallbackFailure
                }
            }
        } catch (error: Exception) {
            cameraError = error.localizedMessage ?: context.getString(R.string.camera_initialization_failed)
        }
    }

    DisposableEffect(cameraSession) {
        val session = cameraSession
        onDispose {
            session?.provider?.unbind(session.preview, session.imageCapture)
            session?.effect?.release()
        }
    }

    LaunchedEffect(cameraSession, flashChoice) {
        if (flashAvailable) cameraSession?.imageCapture?.flashMode = flashChoice.cameraXValue
    }

    LaunchedEffect(cameraSession, recipe) {
        cameraSession?.effect?.updateRecipe(recipe)
    }

    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(captureExecutor) {
        onDispose { captureExecutor.shutdown() }
    }

    fun focusAt(x: Float, y: Float) {
        val session = cameraSession ?: return
        val view = previewView ?: return
        runCatching {
            val point = view.meteringPointFactory.createPoint(x, y)
            session.camera.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                .addListener({ focusMessage = R.string.camera_focus_applied }, ContextCompat.getMainExecutor(context))
        }.onFailure { focusMessage = R.string.camera_focus_unavailable }
    }

    fun capturePhoto() {
        val session = cameraSession ?: return
        timerCountdown = 0
        captureInProgress = true
        captureMessage = null
        saveOriginal(
            context = context,
            imageCapture = session.imageCapture,
            executor = captureExecutor,
            onComplete = { result ->
                ContextCompat.getMainExecutor(context).execute {
                    captureInProgress = false
                    result.fold(
                        onSuccess = { uri ->
                            latestPhoto = uri
                            captureMessage = R.string.camera_photo_saved
                            if (shutterSoundEnabled) shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                            onPhotoSaved(uri, recipe)
                        },
                        onFailure = { error ->
                            captureMessage = R.string.camera_capture_failed
                            cameraError = error.localizedMessage
                        },
                    )
                }
            },
        )
    }

    fun triggerShutter() {
        if (!permissionsGranted || cameraSession == null || captureInProgress || timerCountdown > 0) return
        if (timerSeconds > 0) {
            timerCountdown = timerSeconds
            coroutineScope.launch {
                while (timerCountdown > 0) {
                    delay(1_000)
                    timerCountdown -= 1
                }
                capturePhoto()
            }
        } else capturePhoto()
    }

    var lastHandledExternalShutterRequest by remember { mutableIntStateOf(externalShutterRequest) }
    LaunchedEffect(externalShutterRequest) {
        if (externalShutterRequest > lastHandledExternalShutterRequest) {
            lastHandledExternalShutterRequest = externalShutterRequest
            triggerShutter()
        }
    }

    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize(), color = CameraDeepSky) {
            Box(Modifier.fillMaxSize()) {
                if (permissionsGranted) {
                    AndroidView(
                        factory = { viewContext ->
                            PreviewView(viewContext).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                setOnTouchListener { view, event ->
                                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                                        focusAt(event.x.coerceIn(0f, view.width.toFloat()), event.y.coerceIn(0f, view.height.toFloat()))
                                    }
                                    true
                                }
                                previewView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = previewDescription },
                        update = { previewView = it },
                    )
                }
                if (gridEnabled && permissionsGranted) {
                    Canvas(Modifier.fillMaxSize()) {
                        val line = Color.White.copy(alpha = 0.72f)
                        for (division in 1..2) {
                            val x = size.width * division / 3f
                            val y = size.height * division / 3f
                            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                            drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                    }
                }

                if (permissionsGranted && cameraSession != null) {
                    HorizonLevelOverlay(Modifier.align(Alignment.TopEnd).padding(top = 88.dp, end = 12.dp))
                }

                if (captureMessage != null || timerCountdown > 0 || focusMessage != null || remainingExposures != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(CameraDeepSky.copy(alpha = 0.62f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        captureMessage?.let {
                            Text(
                                text = stringResource(it),
                                color = CameraGold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        if (timerCountdown > 0) {
                            Text(countdownStatus, color = CameraGold, fontSize = 18.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        focusMessage?.let { Text(stringResource(it), color = CameraGold, fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        remainingExposures?.let { Text(stringResource(R.string.camera_roll_remaining, it), color = CameraIvory, fontSize = 14.sp) }
                    }
                }

                if (!permissionsGranted) {
                    PermissionPanel(
                        wasDenied = permissionWasDenied,
                        onRequest = {
                            requiredPermissions.forEach(context::markPermissionRequested)
                            permissionLauncher.launch(requiredPermissions.toTypedArray())
                        },
                        onOpenSettings = { context.openApplicationSettings() },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (cameraError != null || visualEffectError != null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(28.dp)
                            .background(CameraDeepSky.copy(alpha = 0.9f), RoundedCornerShape(16.dp)).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(cameraError ?: visualEffectError.orEmpty(), color = CameraIvory, textAlign = TextAlign.Center)
                        TextButton(onClick = { retryCamera++ }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = CameraIvory)) {
                            Text(stringResource(R.string.camera_retry))
                        }
                    }
                } else if (cameraSession == null) {
                    Text(waitingStatus, color = CameraIvory, modifier = Modifier.align(Alignment.Center))
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(CameraDeepSky.copy(alpha = 0.72f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (cameraControlsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 258.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    if (flashAvailable) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FlashChoice.entries.forEach { choice ->
                                FilterChip(
                                    selected = flashChoice == choice,
                                    onClick = { flashChoice = choice },
                                    label = { Text(stringResource(choice.labelResource())) },
                                    enabled = !captureInProgress,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    colors = cameraChipColors,
                                )
                            }
                        }
                    } else if (cameraSession != null) {
                        Text(stringResource(R.string.camera_flash_unavailable), color = CameraStone)
                    }

                    FlowRow(
                        Modifier.fillMaxWidth().testTag("camera-options"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0, 3, 10).forEach { seconds ->
                            FilterChip(
                                selected = timerSeconds == seconds,
                                onClick = { timerSeconds = seconds },
                                label = {
                                    Text(stringResource(when (seconds) {
                                        0 -> R.string.camera_timer_off
                                        3 -> R.string.camera_timer_three
                                        else -> R.string.camera_timer_ten
                                    }))
                                },
                                enabled = !captureInProgress && timerCountdown == 0,
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = cameraChipColors,
                            )
                        }
                        FilterChip(
                            selected = gridEnabled,
                            onClick = { gridEnabled = !gridEnabled },
                            label = { Text(stringResource(R.string.camera_grid)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = cameraChipColors,
                        )
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(zoomLabel, color = CameraIvory)
                        if (cameraZoomState != null && cameraZoomState.maxZoomRatio > cameraZoomState.minZoomRatio) {
                            Slider(
                                value = zoomRatio.coerceIn(cameraZoomState.minZoomRatio, cameraZoomState.maxZoomRatio),
                                onValueChange = { ratio ->
                                    zoomRatio = ratio
                                    cameraSession?.camera?.cameraControl?.setZoomRatio(ratio)
                                },
                                valueRange = cameraZoomState.minZoomRatio..cameraZoomState.maxZoomRatio,
                                enabled = !captureInProgress,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                    contentDescription = zoomLabel
                                    stateDescription = zoomStateDescription
                                },
                            )
                        } else {
                            Text(stringResource(R.string.camera_control_unavailable), color = CameraStone)
                        }
                    }

                    if (exposureRange != null && exposureRange.lower < exposureRange.upper) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(exposureLabel, color = CameraIvory)
                            Slider(
                                value = exposureIndex.coerceIn(exposureRange.lower, exposureRange.upper).toFloat(),
                                onValueChange = { value ->
                                    exposureIndex = value.roundToInt()
                                    cameraSession?.camera?.cameraControl?.setExposureCompensationIndex(exposureIndex)
                                },
                                valueRange = exposureRange.lower.toFloat()..exposureRange.upper.toFloat(),
                                steps = (exposureRange.upper - exposureRange.lower - 1).coerceAtLeast(0),
                                enabled = !captureInProgress,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                    contentDescription = exposureLabel
                                    stateDescription = exposureStateDescription
                                },
                            )
                        }
                    } else if (cameraSession != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(exposureLabel, color = CameraIvory)
                            Spacer(Modifier.size(10.dp))
                            Text(stringResource(R.string.camera_exposure_unavailable), color = CameraStone)
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { focusAt((previewView?.width ?: 0) / 2f, (previewView?.height ?: 0) / 2f) },
                            enabled = cameraSession != null,
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = CameraIvory),
                        ) {
                            Text(stringResource(R.string.camera_focus_center))
                        }
                        if (latestPhoto != null && latestPhotoVisible) {
                            TextButton(onClick = { context.openLatestPhoto(latestPhoto!!) }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = CameraIvory)) {
                                Text(stringResource(R.string.camera_view_latest))
                            }
                        }
                        TextButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                                captureMessage = null
                            },
                            enabled = cameraSession != null && cameraError == null && visualEffectError == null && !captureInProgress,
                            modifier = Modifier.semantics {
                                contentDescription = switchCameraDescription
                                stateDescription = switchStateDescription
                            }.heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = CameraIvory),
                        ) { Text(switchCameraDescription) }
                    }

                    }
                    } else {
                        TextButton(
                            onClick = { cameraControlsExpanded = true },
                            enabled = permissionsGranted && cameraSession != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = CameraIvory),
                        ) { Text(stringResource(R.string.camera_controls_show)) }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        ) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text(stringResource(R.string.camera_mode_photo)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = cameraChipColors,
                        )
                        Spacer(Modifier.size(8.dp))
                        FilterChip(
                            selected = false,
                            onClick = onOpenVideo,
                            label = { Text(stringResource(R.string.camera_mode_video)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = cameraChipColors,
                        )
                    }

                    if (cameraControlsExpanded) {
                        TextButton(
                            onClick = { cameraControlsExpanded = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = CameraIvory),
                        ) { Text(stringResource(R.string.camera_controls_hide)) }
                    }

                    IconButton(
                        onClick = { triggerShutter() },
                        enabled = permissionsGranted && cameraSession != null && cameraError == null && visualEffectError == null && !captureInProgress && timerCountdown == 0,
                        modifier = Modifier
                            .size(76.dp)
                            .background(CameraGold, CircleShape)
                            .semantics {
                                contentDescription = takePhotoDescription
                                if (timerCountdown > 0) stateDescription = countdownStatus
                                else if (captureInProgress) stateDescription = savingStateDescription
                            },
                    ) {
                        if (captureInProgress) Text("…", color = CameraDeepSky, fontSize = 24.sp)
                        else Box(Modifier.size(58.dp).background(CameraIvory, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPanel(
    wasDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .background(CameraDeepSky.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.camera_permission_explanation),
            color = CameraIvory,
            textAlign = TextAlign.Center,
        )
        if (wasDenied) {
            Text(
                text = stringResource(R.string.camera_permission_denied),
                color = CameraIvory,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onRequest, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.camera_request_permission))
        }
        if (wasDenied) TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = CameraIvory)) {
            Text(stringResource(R.string.camera_open_settings))
        }
    }
}

private data class CameraSession(
    val provider: ProcessCameraProvider,
    val preview: Preview,
    val imageCapture: ImageCapture,
    val camera: androidx.camera.core.Camera,
    val effect: FilmPreviewEffect,
)

private enum class FlashChoice(val cameraXValue: Int) {
    AUTO(ImageCapture.FLASH_MODE_AUTO),
    ON(ImageCapture.FLASH_MODE_ON),
    OFF(ImageCapture.FLASH_MODE_OFF),
}

@Composable
private fun FlashChoice.labelResource(): Int = when (this) {
    FlashChoice.AUTO -> R.string.camera_flash_auto
    FlashChoice.ON -> R.string.camera_flash_on
    FlashChoice.OFF -> R.string.camera_flash_off
}

private suspend fun ListenableFuture<ProcessCameraProvider>.await(executor: Executor): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    if (continuation.isActive) continuation.resumeWith(Result.success(get()))
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            },
            executor,
        )
    }

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.markPermissionRequested(permission: String) {
    getSharedPreferences("theia_permission_requests", Context.MODE_PRIVATE)
        .edit()
        .putBoolean(permission, true)
        .apply()
}

private fun Context.openApplicationSettings() {
    startActivity(
        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.openLatestPhoto(uri: Uri) {
    runCatching {
        startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(uri, "image/jpeg")
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun saveOriginal(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onComplete: (Result<Uri>) -> Unit,
) {
    val resolver = context.contentResolver
    val name = "Theia_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
    val temporaryFile = try {
        java.io.File.createTempFile("theia-original-", ".jpg", context.cacheDir)
    } catch (failure: Exception) {
        onComplete(Result.failure(failure))
        return
    }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(temporaryFile).build()
    try {
        imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val savedUri = publishOriginal(context, name, temporaryFile)
                    onComplete(Result.success(savedUri))
                } catch (failure: Exception) {
                    onComplete(Result.failure(failure))
                } finally {
                    temporaryFile.delete()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                temporaryFile.delete()
                onComplete(Result.failure(exception))
            }
        },
        )
    } catch (failure: Exception) {
        temporaryFile.delete()
        onComplete(Result.failure(failure))
    }
}

private fun publishOriginal(context: Context, displayName: String, temporaryFile: java.io.File): Uri {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Théia")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            put(MediaStore.Images.Media.TITLE, displayName.removeSuffix(".jpg"))
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetDirectory = java.io.File(pictures, "Théia")
            check(targetDirectory.exists() || targetDirectory.mkdirs()) {
                context.getString(R.string.camera_gallery_unavailable)
            }
            put(MediaStore.Images.Media.DATA, java.io.File(targetDirectory, displayName).absolutePath)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: error(context.getString(R.string.camera_gallery_unavailable))
    try {
        resolver.openOutputStream(uri, "w")?.use { output ->
            temporaryFile.inputStream().use { input -> input.copyTo(output) }
        } ?: error(context.getString(R.string.camera_gallery_unavailable))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null) > 0) { context.getString(R.string.camera_gallery_unavailable) }
        }
        return uri
    } catch (failure: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        throw failure
    }
}
