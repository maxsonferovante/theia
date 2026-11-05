package app.theia.android.capture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Range
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.DynamicRange
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.theia.android.R
import app.theia.domain.catalog.BuiltInVideoStyles
import app.theia.domain.model.CaptureRecipe
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

private val VideoNavy = Color(0xFF182344)
private val VideoViolet = Color(0xFF2D2D57)
private val VideoGold = Color(0xFFEAAE35)
private val VideoIvory = Color(0xFFE7D7B7)
private const val MAX_RECORDING_DURATION_MILLIS = 60_000L
private val videoMetadataExecutor = Executors.newSingleThreadExecutor { task ->
    Thread(task, "theia-video-metadata").apply { isDaemon = true }
}

/** Video capture view with 1080p/30 preferred and lower-quality device fallback. */
@Composable
fun VideoCaptureScreen(
    recipe: CaptureRecipe,
    onVideoSaved: (VideoCaptureResult, CaptureRecipe) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    var cameraGranted by remember(context) { mutableStateOf(cameraPermissions.all(context::hasRuntimePermission)) }
    var cameraDenied by remember { mutableStateOf(false) }
    var microphoneGranted by remember(context) { mutableStateOf(context.hasRuntimePermission(Manifest.permission.RECORD_AUDIO)) }
    var audioDenied by remember { mutableStateOf(false) }
    var includeAudio by remember { mutableStateOf(false) }
    var selectedVideoStyleId by remember { mutableStateOf(BuiltInVideoStyles.EQUIPMENT_RECIPE_ID) }
    var highResolutionSupported by remember { mutableStateOf(false) }
    var highResolutionEnabled by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var session by remember { mutableStateOf<VideoCameraSession?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var visualEffectError by remember { mutableStateOf<String?>(null) }
    var retryCamera by remember { mutableIntStateOf(0) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var recordedMillis by remember { mutableLongStateOf(0L) }
    var captureMessage by remember { mutableStateOf<String?>(null) }
    val audioLabel = stringResource(R.string.video_include_audio)
    val audioState = stringResource(if (includeAudio) R.string.video_audio_on else R.string.video_audio_off)
    val recordingStatus = stringResource(R.string.video_recording_status)
    val previewDescription = stringResource(R.string.video_live_preview_description)

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        cameraGranted = cameraPermissions.all { results[it] == true || context.hasRuntimePermission(it) }
        cameraDenied = !cameraGranted
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneGranted = granted || context.hasRuntimePermission(Manifest.permission.RECORD_AUDIO)
        includeAudio = microphoneGranted
        audioDenied = !microphoneGranted
    }

    DisposableEffect(lifecycleOwner, cameraPermissions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = cameraPermissions.all(context::hasRuntimePermission)
                microphoneGranted = context.hasRuntimePermission(Manifest.permission.RECORD_AUDIO)
                if (!microphoneGranted) includeAudio = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(previewView, cameraGranted, lensFacing, lifecycleOwner, recipe, selectedVideoStyleId, highResolutionEnabled, retryCamera) {
        val view = previewView
        if (view == null || !cameraGranted) {
            session = null
            return@LaunchedEffect
        }
        session?.let { old ->
            old.provider.unbind(old.preview, old.videoCapture)
            old.effect.release()
        }
        session = null
        cameraError = null
        visualEffectError = null
        try {
            val provider = ProcessCameraProvider.getInstance(context).await(ContextCompat.getMainExecutor(context))
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            check(provider.hasCamera(selector)) { context.getString(R.string.video_camera_unavailable) }
            val supportsUhd = Recorder.getVideoCapabilities(provider.getCameraInfo(selector))
                .getSupportedQualities(DynamicRange.SDR)
                .contains(Quality.UHD)
            highResolutionSupported = supportsUhd
            val requestUhd = highResolutionEnabled && supportsUhd
            if (!supportsUhd && highResolutionEnabled) highResolutionEnabled = false
            fun bind(prefer30Fps: Boolean, preferUhd: Boolean): VideoCameraSession {
                val previewBuilder = Preview.Builder()
                if (prefer30Fps) previewBuilder.setTargetFrameRate(Range(30, 30))
                val preview = previewBuilder
                    .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                    .also { it.surfaceProvider = view.surfaceProvider }
                val capture = buildVideoCapture(prefer30Fps, preferUhd)
                val effect = FilmPreviewEffect(
                    recipe,
                    includeVideoTarget = true,
                    videoStyleId = selectedVideoStyleId,
                    onProcessingError = { _ ->
                        ContextCompat.getMainExecutor(context).execute {
                            visualEffectError = context.getString(R.string.video_visual_effect_failed)
                        }
                    },
                )
                try {
                    val group = androidx.camera.core.UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(capture)
                        .addEffect(effect)
                        .apply { view.viewPort?.let(::setViewPort) }
                        .build()
                    provider.bindToLifecycle(lifecycleOwner, selector, group)
                    return VideoCameraSession(provider, preview, capture, effect)
                } catch (error: Exception) {
                    runCatching { provider.unbind(preview, capture) }
                        .exceptionOrNull()
                        ?.let(error::addSuppressed)
                    effect.release()
                    throw error
                }
            }
            session = if (requestUhd) {
                try {
                    bind(prefer30Fps = true, preferUhd = true)
                } catch (uhdFailure: Exception) {
                    try {
                        bind(prefer30Fps = true, preferUhd = false).also {
                            highResolutionEnabled = false
                            captureMessage = context.getString(R.string.video_resolution_fallback)
                        }
                    } catch (fhdFailure: Exception) {
                        try {
                            bind(prefer30Fps = false, preferUhd = false).also {
                                highResolutionEnabled = false
                                captureMessage = context.getString(R.string.video_resolution_fallback)
                            }
                        } catch (fallbackFailure: Exception) {
                            fallbackFailure.addSuppressed(fhdFailure)
                            fallbackFailure.addSuppressed(uhdFailure)
                            throw fallbackFailure
                        }
                    }
                }
            } else {
                try {
                    bind(prefer30Fps = true, preferUhd = false)
                } catch (preferredFailure: Exception) {
                    try {
                        bind(prefer30Fps = false, preferUhd = false).also {
                            captureMessage = context.getString(R.string.video_resolution_fallback)
                        }
                    } catch (fallbackFailure: Exception) {
                        fallbackFailure.addSuppressed(preferredFailure)
                        throw fallbackFailure
                    }
                }
            }
        } catch (error: Exception) {
            cameraError = error.localizedMessage ?: context.getString(R.string.video_camera_start_failed)
        }
    }

    DisposableEffect(session) {
        val current = session
        onDispose {
            activeRecording?.stop()
            current?.provider?.unbind(current.preview, current.videoCapture)
            current?.effect?.release()
        }
    }

    LaunchedEffect(activeRecording) {
        if (activeRecording != null) {
            val remaining = (MAX_RECORDING_DURATION_MILLIS - recordedMillis).coerceAtLeast(0)
            delay(remaining)
            activeRecording?.stop()
        }
    }

    MaterialTheme {
        Surface(modifier.fillMaxSize(), color = VideoNavy, contentColor = VideoIvory) {
            Box(Modifier.fillMaxSize()) {
                if (cameraGranted) {
                    AndroidView(
                        factory = { viewContext -> PreviewView(viewContext).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView = this
                        } },
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = previewDescription },
                        update = { previewView = it },
                    )
                }
                Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .background(VideoNavy.copy(alpha = 0.76f)).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.video_capture_title), color = VideoIvory, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.video_capture_description), color = VideoIvory, textAlign = TextAlign.Center, fontSize = 13.sp)
                    activeRecording?.let {
                        Text(
                            formatDuration(recordedMillis),
                            color = VideoGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.semantics {
                                contentDescription = "$recordingStatus ${formatDuration(recordedMillis)}"
                                stateDescription = recordingStatus
                            },
                        )
                    }
                    captureMessage?.let { Text(it, color = VideoGold, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                }

                when {
                    !cameraGranted -> PermissionCard(
                        body = stringResource(R.string.video_camera_permission_explanation),
                        denied = cameraDenied,
                        onGrant = {
                            cameraPermissions.forEach(context::markPermissionRequested)
                            cameraPermissionLauncher.launch(cameraPermissions.toTypedArray())
                        },
                        onOpenSettings = { context.openApplicationSettings() },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    cameraError != null || visualEffectError != null -> Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(cameraError ?: visualEffectError.orEmpty(), color = VideoIvory, textAlign = TextAlign.Center)
                        TextButton(onClick = { retryCamera++ }, enabled = activeRecording == null, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.video_retry_camera))
                        }
                    }
                    session == null -> Text(stringResource(R.string.video_camera_starting), color = VideoIvory, modifier = Modifier.align(Alignment.Center))
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 480.dp)
                        .background(VideoNavy.copy(alpha = 0.85f))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.video_style_label), color = VideoIvory, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val styleChoices = listOf(
                            BuiltInVideoStyles.EQUIPMENT_RECIPE_ID to R.string.video_style_recipe,
                        ) + BuiltInVideoStyles.presets.map { preset ->
                            val resource = when (preset.localizationKey) {
                                "video_style_cinematic_warm" -> R.string.video_style_cinematic_warm
                                "video_style_moonlit_monochrome" -> R.string.video_style_moonlit_monochrome
                                else -> null
                            }
                            preset.id to resource
                        }.filter { (_, nameResource) -> nameResource != null }
                        styleChoices.forEach { (styleId, nameResource) ->
                            FilterChip(
                                selected = selectedVideoStyleId == styleId,
                                onClick = { if (activeRecording == null) selectedVideoStyleId = styleId },
                                enabled = activeRecording == null,
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(stringResource(requireNotNull(nameResource)), maxLines = 1) },
                            )
                        }
                    }
                    if (highResolutionSupported) {
                        Text(stringResource(R.string.video_resolution_label), color = VideoIvory, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !highResolutionEnabled,
                                onClick = {
                                    if (activeRecording == null) {
                                        highResolutionEnabled = false
                                        captureMessage = null
                                    }
                                },
                                enabled = activeRecording == null,
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(stringResource(R.string.video_resolution_1080p)) },
                            )
                            FilterChip(
                                selected = highResolutionEnabled,
                                onClick = {
                                    if (activeRecording == null) {
                                        highResolutionEnabled = true
                                        captureMessage = null
                                    }
                                },
                                enabled = activeRecording == null,
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(stringResource(R.string.video_resolution_4k)) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = includeAudio,
                                role = Role.Checkbox,
                                enabled = activeRecording == null,
                                onValueChange = { checked ->
                                    captureMessage = null
                                    if (!checked) {
                                        includeAudio = false
                                        audioDenied = false
                                    } else if (microphoneGranted) {
                                        includeAudio = true
                                    } else {
                                        context.markPermissionRequested(Manifest.permission.RECORD_AUDIO)
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription = audioLabel
                                stateDescription = audioState
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = includeAudio,
                            onCheckedChange = null,
                            enabled = activeRecording == null,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                        Text(audioLabel, color = VideoIvory)
                    }
                    if (audioDenied) {
                        Text(stringResource(R.string.video_microphone_denied), color = VideoIvory, textAlign = TextAlign.Center)
                        TextButton(onClick = { context.openApplicationSettings() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.video_open_settings))
                        }
                    }
                    if (onBack != null && activeRecording == null) {
                        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.video_back))
                        }
                    }
                    Button(
                        enabled = session != null && cameraError == null && visualEffectError == null,
                        onClick = {
                            val current = session ?: return@Button
                            captureMessage = null
                            val recording = activeRecording
                            if (recording != null) {
                                recording.stop()
                            } else {
                                recordedMillis = 0L
                                startVideoRecording(
                                    context = context,
                                    capture = current.videoCapture,
                                    executor = ContextCompat.getMainExecutor(context),
                                    recipe = recipe,
                                    videoStyleId = selectedVideoStyleId,
                                    withAudio = includeAudio && microphoneGranted,
                                    onRecording = { activeRecording = it },
                                    onDuration = { recordedMillis = it.coerceAtMost(MAX_RECORDING_DURATION_MILLIS) },
                                    onResult = { result ->
                                        activeRecording = null
                                        when (result) {
                                            is VideoCaptureOutcome.Saved -> {
                                                captureMessage = context.getString(R.string.video_saved)
                                                onVideoSaved(result.video, recipe)
                                            }
                                            is VideoCaptureOutcome.Failed -> captureMessage = context.getString(R.string.video_failed, result.reason)
                                        }
                                    },
                                )
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            if (activeRecording != null) stateDescription = recordingStatus
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(if (activeRecording == null) stringResource(R.string.video_start) else stringResource(R.string.video_stop))
                    }
                }
            }
        }
    }
}

data class VideoCaptureResult(
    val uri: Uri,
    val capturedAtEpochMillis: Long,
    val durationMillis: Long,
    val widthPixels: Int,
    val heightPixels: Int,
    /** Average encoded frame rate measured from the saved file, or null if it could not be read. */
    val framesPerSecond: Int?,
    val includesAudio: Boolean,
    val videoStyleId: String,
)

private data class VideoCameraSession(
    val provider: ProcessCameraProvider,
    val preview: Preview,
    val videoCapture: VideoCapture<Recorder>,
    val effect: FilmPreviewEffect,
)

private sealed interface VideoCaptureOutcome {
    data class Saved(val video: VideoCaptureResult) : VideoCaptureOutcome
    data class Failed(val reason: String) : VideoCaptureOutcome
}

@Composable
private fun PermissionCard(
    body: String,
    denied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(VideoViolet.copy(alpha = 0.96f), RoundedCornerShape(20.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(body, color = VideoIvory, textAlign = TextAlign.Center)
        if (denied) Text(stringResource(R.string.video_permission_denied), color = VideoIvory, textAlign = TextAlign.Center)
        Button(onClick = onGrant, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.video_allow_camera))
        }
        if (denied) TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.video_open_settings))
        }
    }
}

private fun measureEncodedFrameRate(context: Context, uri: Uri): Int? {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: return null
        extractor.selectTrack(videoTrack)
        var firstFrameTimeUs = -1L
        var lastFrameTimeUs = -1L
        var frameCount = 0L
        while (extractor.sampleTrackIndex >= 0) {
            val sampleTimeUs = extractor.sampleTime
            if (sampleTimeUs >= 0) {
                if (firstFrameTimeUs < 0) firstFrameTimeUs = sampleTimeUs
                lastFrameTimeUs = sampleTimeUs
                frameCount += 1
            }
            if (!extractor.advance()) break
        }
        val sampleSpanUs = lastFrameTimeUs - firstFrameTimeUs
        if (frameCount < 2 || sampleSpanUs <= 0) return null
        (((frameCount - 1) * 1_000_000.0) / sampleSpanUs)
            .roundToInt()
            .takeIf { it in 1..240 }
    } catch (_: Exception) {
        null
    } finally {
        extractor.release()
    }
}

private fun buildVideoCapture(prefer30Fps: Boolean, preferUhd: Boolean = false): VideoCapture<Recorder> {
    val qualities = if (preferUhd) {
        listOf(Quality.UHD, Quality.FHD)
    } else {
        listOf(Quality.FHD, Quality.HD, Quality.SD)
    }
    val selector = QualitySelector.fromOrderedList(
        qualities,
        if (preferUhd) FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
        else FallbackStrategy.lowerQualityThan(Quality.FHD),
    )
    val recorder = Recorder.Builder().setQualitySelector(selector).build()
    return if (prefer30Fps) {
        VideoCapture.Builder(recorder).setTargetFrameRate(Range(30, 30)).build()
    } else {
        VideoCapture.withOutput(recorder)
    }
}

private fun startVideoRecording(
    context: Context,
    capture: VideoCapture<Recorder>,
    executor: Executor,
    recipe: CaptureRecipe,
    videoStyleId: String,
    withAudio: Boolean,
    onRecording: (Recording) -> Unit,
    onDuration: (Long) -> Unit,
    onResult: (VideoCaptureOutcome) -> Unit,
) {
    val capturedAt = System.currentTimeMillis()
    val displayName = "Theia_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(capturedAt))}.mp4"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(
            MediaStore.Video.Media.DESCRIPTION,
            org.json.JSONObject()
                .put("version", 1)
                .put("recipe", recipe.id.value)
                .put("body", recipe.bodyId.value)
                .put("lens", recipe.lensId.value)
                .put("film", recipe.filmStockId.value)
                .put("video_style", videoStyleId)
                .toString(),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Théia")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        } else {
            put(MediaStore.Video.Media.TITLE, displayName.removeSuffix(".mp4"))
            val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Théia")
            if (!dir.exists() && !dir.mkdirs()) {
                onResult(VideoCaptureOutcome.Failed(context.getString(R.string.video_storage_failed)))
                return
            }
            put(MediaStore.Video.Media.DATA, java.io.File(dir, displayName).absolutePath)
        }
    }
    try {
        val output = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values)
            .setDurationLimitMillis(MAX_RECORDING_DURATION_MILLIS)
            .build()
        var pending = capture.output.prepareRecording(context, output)
        if (withAudio) pending = pending.withAudioEnabled()
        val startedAtElapsed = SystemClock.elapsedRealtime()
        val active = pending.start(executor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> Unit
                is VideoRecordEvent.Status -> onDuration(event.recordingStats.recordedDurationNanos / 1_000_000L)
                is VideoRecordEvent.Finalize -> {
                    val uri = event.outputResults.outputUri
                    val duration = (event.recordingStats.recordedDurationNanos / 1_000_000L)
                        .coerceAtLeast((SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtMost(MAX_RECORDING_DURATION_MILLIS))
                        .coerceAtMost(MAX_RECORDING_DURATION_MILLIS)
                    val hasUsableOutput = event.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                        event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                    if (hasUsableOutput && uri != Uri.EMPTY) {
                        try {
                            val size = capture.resolutionInfo?.resolution
                            val video = VideoCaptureResult(
                                uri = uri,
                                capturedAtEpochMillis = capturedAt,
                                durationMillis = duration,
                                widthPixels = size?.width ?: 1920,
                                heightPixels = size?.height ?: 1080,
                                framesPerSecond = null,
                                includesAudio = withAudio,
                                videoStyleId = videoStyleId,
                            )
                            videoMetadataExecutor.execute {
                                val measuredFrameRate = measureEncodedFrameRate(context, uri)
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        check(context.contentResolver.update(uri, ContentValues().apply {
                                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                                        }, null, null) > 0) {
                                            context.getString(R.string.video_storage_failed)
                                        }
                                    }
                                    ContextCompat.getMainExecutor(context).execute {
                                        onResult(VideoCaptureOutcome.Saved(video.copy(framesPerSecond = measuredFrameRate)))
                                    }
                                } catch (failure: Exception) {
                                    runCatching { context.contentResolver.delete(uri, null, null) }
                                    ContextCompat.getMainExecutor(context).execute {
                                        onResult(VideoCaptureOutcome.Failed(
                                            failure.localizedMessage ?: context.getString(R.string.video_storage_failed),
                                        ))
                                    }
                                }
                            }
                        } catch (failure: Exception) {
                            runCatching { context.contentResolver.delete(uri, null, null) }
                            onResult(VideoCaptureOutcome.Failed(
                                failure.localizedMessage ?: context.getString(R.string.video_storage_failed),
                            ))
                        }
                    } else {
                        if (uri != Uri.EMPTY) runCatching { context.contentResolver.delete(uri, null, null) }
                        onResult(VideoCaptureOutcome.Failed(event.cause?.localizedMessage ?: context.getString(R.string.video_capture_failed)))
                    }
                }
            }
        }
        onRecording(active)
    } catch (error: SecurityException) {
        onResult(VideoCaptureOutcome.Failed(
            context.getString(if (withAudio) R.string.video_microphone_denied else R.string.video_storage_failed),
        ))
    } catch (error: Exception) {
        onResult(VideoCaptureOutcome.Failed(error.localizedMessage ?: context.getString(R.string.video_capture_failed)))
    }
}

private suspend fun ListenableFuture<ProcessCameraProvider>.await(executor: Executor): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                if (continuation.isActive) continuation.resumeWith(Result.success(get()))
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }, executor)
    }

private fun Context.hasRuntimePermission(permission: String): Boolean =
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

private fun formatDuration(durationMillis: Long): String =
    "${(durationMillis / 60_000).toString().padStart(2, '0')}:${((durationMillis / 1_000) % 60).toString().padStart(2, '0')}"
