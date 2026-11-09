package app.theia.android.album

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import android.graphics.Matrix
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.produceState
import androidx.core.net.toUri
import android.graphics.BitmapFactory
import app.theia.android.R
import app.theia.android.roll.AndroidFilmRollService
import app.theia.android.geotag.AndroidGeotagService
import app.theia.android.geotag.GeotagLabPanel
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.UserPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AlbumNavy = Color(0xFF182344)
private val AlbumViolet = Color(0xFF2D2D57)
private val AlbumGold = Color(0xFFEAAE35)
private val AlbumIvory = Color(0xFFE7D7B7)
private val AlbumStone = Color(0xFFB6A58C)

private enum class AlbumFilter { ALL, FAVORITES, DEVELOPED, ORIGINALS }
private enum class AlbumGrouping { DATE, ROLL, FILM, BODY }

/** Local album and photo lab; callers provide navigation and a repository backed by app-private metadata. */
@Composable
fun AlbumLabScreen(
    repository: AlbumMetadataRepository,
    rollService: AndroidFilmRollService? = null,
    geotagService: AndroidGeotagService? = null,
    userPreferences: UserPreferences? = null,
    selectedRecipe: CaptureRecipe? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var photos by remember(repository) { mutableStateOf(repository.all()) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AlbumFilter.ALL) }
    var selectedPhoto by remember { mutableStateOf<AlbumPhoto?>(null) }
    var viewerPhoto by remember { mutableStateOf<AlbumPhoto?>(null) }
    var deleteCandidate by remember { mutableStateOf<AlbumPhoto?>(null) }
    var pendingGalleryDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
    var grouping by remember { mutableStateOf(AlbumGrouping.DATE) }
    var visibleRollPhotoIds by remember(rollService) {
        mutableStateOf<Set<String>?>(if (rollService == null) null else emptySet())
    }

    fun refresh() { photos = repository.all() }

    val galleryDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val photoId = pendingGalleryDeletionId
        pendingGalleryDeletionId = null
        if (result.resultCode == Activity.RESULT_OK && photoId != null) {
            repository.remove(photoId)
            selectedPhoto = null
            refresh()
        } else if (result.resultCode != Activity.RESULT_CANCELED) {
            Toast.makeText(context, context.getString(R.string.album_delete_gallery_failed), Toast.LENGTH_LONG).show()
        }
    }

    val availablePhotos = photos.filter { photo ->
        photo.rollId == null || visibleRollPhotoIds == null || photo.id in visibleRollPhotoIds.orEmpty()
    }
    val visible = availablePhotos.filter { photo ->
        val matchesFilter = when (filter) {
            AlbumFilter.ALL -> true
            AlbumFilter.FAVORITES -> photo.isFavorite
            AlbumFilter.DEVELOPED -> photo.developedUri != null
            AlbumFilter.ORIGINALS -> photo.developedUri == null
        }
        val query = search.trim().lowercase(Locale.ROOT)
        val matchesSearch = query.isEmpty() || listOf(
            photo.title,
            photo.notes,
            photo.cameraBodyId,
            photo.lensId,
            photo.filmStockId,
            photo.rollId.orEmpty(),
            dateLabel(photo.capturedAtEpochMillis),
        ).any { it.lowercase(Locale.ROOT).contains(query) }
        matchesFilter && matchesSearch
    }
    val grouped = visible.sortedByDescending { it.capturedAtEpochMillis }.groupBy { photo ->
        when (grouping) {
            AlbumGrouping.DATE -> dateGroupLabel(photo.capturedAtEpochMillis)
            AlbumGrouping.ROLL -> photo.rollId ?: context.getString(R.string.album_group_unassigned)
            AlbumGrouping.FILM -> photo.filmStockId
            AlbumGrouping.BODY -> photo.cameraBodyId
        }
    }

    MaterialTheme {
        Surface(modifier.fillMaxSize(), color = AlbumNavy, contentColor = AlbumIvory) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.album_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.album_lab_title), color = AlbumIvory, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.album_count, visible.size), color = AlbumStone, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.album_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlbumFilter.values().forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(stringResource(option.labelResource())) },
                        )
                    }
                }
                Text(stringResource(R.string.album_group_by), color = AlbumStone, fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlbumGrouping.values().forEach { option ->
                        FilterChip(
                            selected = grouping == option,
                            onClick = { grouping = option },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(stringResource(option.labelResource())) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.testTag("album-photo-list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (rollService != null) {
                        item(key = "roll-panel") {
                            AlbumRollPanel(
                                service = rollService,
                                selectedRecipe = selectedRecipe,
                                onVisiblePhotoIdsChanged = { ids ->
                                    if (visibleRollPhotoIds != ids) {
                                        visibleRollPhotoIds = ids
                                        refresh()
                                    }
                                },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    if (visible.isEmpty()) {
                        item(key = "empty") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.album_empty), color = AlbumStone)
                            }
                        }
                    } else {
                        grouped.forEach { (month, monthPhotos) ->
                            item(key = "month:$month") {
                                Text(month, color = AlbumGold, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp).semantics { heading() })
                            }
                            items(monthPhotos, key = { it.id }) { photo ->
                                AlbumPhotoRow(
                                    photo = photo,
                                    onOpen = { selectedPhoto = photo },
                                    onView = { viewerPhoto = photo },
                                    onFavorite = {
                                        repository.update(photo.copy(isFavorite = !photo.isFavorite))
                                        refresh()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedPhoto?.let { photo ->
        PhotoDetailDialog(
            photo = photo,
            geotagService = geotagService,
            userPreferences = userPreferences,
            onDismiss = { selectedPhoto = null },
            onToggleFavorite = {
                repository.update(photo.copy(isFavorite = !photo.isFavorite))
                refresh()
                selectedPhoto = photo.copy(isFavorite = !photo.isFavorite)
            },
            onShare = { sharePhoto(context, photo) },
            onDelete = { deleteCandidate = photo },
            onSaveMetadata = { title, notes ->
                val updated = photo.copy(title = title, notes = notes)
                repository.update(updated)
                refresh()
                selectedPhoto = updated
            },
            onViewMedia = { viewerPhoto = photo },
        )
    }

    viewerPhoto?.let { photo ->
        FullScreenMediaViewer(photo = photo, onClose = { viewerPhoto = null })
    }

    deleteCandidate?.let { photo ->
        DeletePhotoDialog(
            photo = photo,
            onDismiss = { deleteCandidate = null },
            onConfirm = { deleteDevelopedCopy ->
                val galleryUris = listOfNotNull(photo.originalUri, photo.developedUri)
                    .distinct()
                    .map(Uri::parse)
                if (!deleteDevelopedCopy) {
                    repository.remove(photo.id)
                    selectedPhoto = null
                    refresh()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && galleryUris.isNotEmpty()) {
                    runCatching {
                        val request = MediaStore.createDeleteRequest(context.contentResolver, galleryUris)
                        pendingGalleryDeletionId = photo.id
                        galleryDeleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.album_delete_gallery_failed), Toast.LENGTH_LONG).show()
                    }
                } else {
                    val copyDeleted = galleryUris.all { uri ->
                        runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
                    }
                    if (copyDeleted) {
                        repository.remove(photo.id)
                        selectedPhoto = null
                        refresh()
                    } else {
                        Toast.makeText(context, context.getString(R.string.album_delete_gallery_failed), Toast.LENGTH_LONG).show()
                    }
                }
                deleteCandidate = null
            },
        )
    }
}

@Composable
private fun AlbumPhotoRow(photo: AlbumPhoto, onOpen: () -> Unit, onView: () -> Unit, onFavorite: () -> Unit) {
    val itemTitle = photo.title.ifBlank { stringResource(R.string.album_untitled) }
    val itemKind = stringResource(if (photo.mediaKind == AlbumMediaKind.VIDEO) R.string.album_media_video else R.string.album_media_photo)
    val status = if (photo.mediaKind == AlbumMediaKind.VIDEO) {
        stringResource(R.string.album_video_duration, formatDuration(photo.durationMillis ?: 0L))
    } else if (photo.developedUri == null) {
        stringResource(R.string.album_original_only)
    } else {
        stringResource(R.string.album_developed)
    }
    val itemDescription = stringResource(
        R.string.album_accessible_item,
        itemTitle,
        itemKind,
        dateLabel(photo.capturedAtEpochMillis),
        status,
    )
    Row(
        Modifier.fillMaxWidth()
            .background(AlbumViolet, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = itemDescription
                role = Role.Button
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumImage(
            uri = photo.developedUri ?: photo.originalUri,
            isVideo = photo.mediaKind == AlbumMediaKind.VIDEO,
            description = stringResource(if (photo.mediaKind == AlbumMediaKind.VIDEO) R.string.album_video_thumbnail_description else R.string.album_image_description),
            modifier = Modifier.size(84.dp).clickable(role = Role.Button, onClick = onView),
        )
        Column(Modifier.weight(1f)) {
            Text(itemTitle, color = AlbumIvory, fontWeight = FontWeight.Medium)
            Text(dateLabel(photo.capturedAtEpochMillis), color = AlbumStone, fontSize = 12.sp)
            Text(status, color = AlbumGold, fontSize = 12.sp)
        }
        val favoriteLabel = stringResource(if (photo.isFavorite) R.string.album_remove_favorite else R.string.album_add_favorite, itemTitle)
        val favoriteState = stringResource(if (photo.isFavorite) R.string.album_is_favorite else R.string.album_is_not_favorite)
        IconButton(
            onClick = onFavorite,
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = favoriteLabel
                stateDescription = favoriteState
            },
        ) { Text(if (photo.isFavorite) "★" else "☆", color = AlbumGold, fontSize = 23.sp) }
    }
}

@Composable
private fun FullScreenMediaViewer(photo: AlbumPhoto, onClose: () -> Unit) {
    val selectedUri = photo.developedUri ?: photo.originalUri
    var resetRequest by remember(selectedUri) { mutableIntStateOf(0) }
    val photoDescription = stringResource(R.string.album_viewer_description)
    val closeDescription = stringResource(R.string.album_viewer_close)
    val resetZoomDescription = stringResource(R.string.album_viewer_reset_zoom)
    val viewerLabel = if (photo.mediaKind == AlbumMediaKind.VIDEO) stringResource(R.string.album_media_video) else stringResource(R.string.album_media_photo)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF121522), contentColor = AlbumIvory) {
            Box(Modifier.fillMaxSize()) {
                if (photo.mediaKind == AlbumMediaKind.VIDEO) {
                    AndroidView(
                        factory = { viewContext ->
                            VideoView(viewContext).apply {
                                setMediaController(android.widget.MediaController(viewContext).also { it.setAnchorView(this) })
                                setVideoURI(Uri.parse(selectedUri))
                                setOnPreparedListener { start() }
                            }
                        },
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = viewerLabel },
                        onRelease = { it.stopPlayback() },
                    )
                } else {
                    ZoomablePhoto(
                        uri = selectedUri,
                        description = photoDescription,
                        resetRequest = resetRequest,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (photo.mediaKind == AlbumMediaKind.PHOTO) {
                        TextButton(
                            onClick = { resetRequest++ },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = resetZoomDescription },
                        ) {
                            Text(stringResource(R.string.album_viewer_reset_zoom), color = AlbumIvory)
                        }
                    }
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = closeDescription },
                    ) {
                        Text(stringResource(R.string.album_close), color = AlbumIvory)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(uri: String, description: String, resetRequest: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var scale by remember(uri) { mutableStateOf(1f) }
    var offsetX by remember(uri) { mutableStateOf(0f) }
    var offsetY by remember(uri) { mutableStateOf(0f) }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { decodeViewerBitmap(context, uri.toUri()) }.getOrNull() }
    }
    fun resetZoom() { scale = 1f; offsetX = 0f; offsetY = 0f }
    LaunchedEffect(resetRequest) { resetZoom() }
    val gestures = Modifier
        .pointerInput(uri) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 8f)
                offsetX += pan.x
                offsetY += pan.y
                if (scale <= 1f) resetZoom()
            }
        }
        .pointerInput(uri) {
            detectTapGestures(onDoubleTap = { resetZoom() })
        }
    if (bitmap != null) {
        Image(
            bitmap!!.asImageBitmap(),
            contentDescription = description,
            modifier = modifier.then(gestures).graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            },
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    } else {
        Box(modifier.then(gestures).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Text("…", color = AlbumIvory)
        }
    }
}

@Composable
private fun PhotoDetailDialog(
    photo: AlbumPhoto,
    geotagService: AndroidGeotagService?,
    userPreferences: UserPreferences?,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSaveMetadata: (String, String) -> Unit,
    onViewMedia: () -> Unit,
) {
    var title by remember(photo.id) { mutableStateOf(photo.title) }
    var notes by remember(photo.id) { mutableStateOf(photo.notes) }
    val thumbnailDescription = stringResource(
        if (photo.mediaKind == AlbumMediaKind.VIDEO) R.string.album_video_thumbnail_description
        else R.string.album_image_description,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_detail_title), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AlbumImage(
                    uri = photo.developedUri ?: photo.originalUri,
                    isVideo = photo.mediaKind == AlbumMediaKind.VIDEO,
                    description = thumbnailDescription,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clickable(role = Role.Button, onClick = onViewMedia)
                        .semantics {
                            contentDescription = thumbnailDescription
                        },
                )
                Text(dateLabel(photo.capturedAtEpochMillis), color = AlbumStone)
                if (photo.durationMillis != null) Text(stringResource(R.string.album_video_duration, formatDuration(photo.durationMillis)), color = AlbumStone)
                Text(stringResource(R.string.album_recipe, photo.cameraBodyId, photo.lensId, photo.filmStockId), color = AlbumStone)
                photo.rollId?.let { Text(stringResource(R.string.album_roll, it), color = AlbumStone) }
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.album_field_title)) }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.album_field_notes)) })
                if (photo.mediaKind == AlbumMediaKind.PHOTO && geotagService != null && userPreferences != null) {
                    GeotagLabPanel(
                        service = geotagService,
                        preferences = userPreferences,
                        selectedPhoto = Uri.parse(photo.originalUri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onToggleFavorite, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (photo.isFavorite) stringResource(R.string.album_unfavorite) else stringResource(R.string.album_favorite))
                }
                TextButton(onClick = onShare, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.album_share)) }
                Button(onClick = { onSaveMetadata(title, notes); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.album_save))
                }
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.album_delete)) }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.album_close)) }
            }
        },
    )
}

@Composable
private fun DeletePhotoDialog(photo: AlbumPhoto, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var deleteDevelopedCopy by remember(photo.id) { mutableStateOf(false) }
    val galleryDeleteLabel = stringResource(R.string.album_delete_gallery_copy)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_delete_title), modifier = Modifier.semantics { heading() }) },
        text = {
            Column {
                Text(stringResource(R.string.album_delete_explanation))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = deleteDevelopedCopy,
                        onCheckedChange = { deleteDevelopedCopy = it },
                        enabled = photo.originalUri.isNotBlank(),
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = galleryDeleteLabel },
                    )
                    Text(stringResource(R.string.album_delete_gallery_copy))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteDevelopedCopy) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.album_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.album_cancel))
            }
        },
    )
}

@Composable
private fun AlbumImage(uri: String, isVideo: Boolean, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (isVideo) decodeVideoThumbnail(context, uri.toUri()) else decodeAlbumPreview(context, uri.toUri())
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = description, modifier = modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    } else {
        Box(modifier.background(AlbumNavy, RoundedCornerShape(12.dp)).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Text("✦", color = AlbumGold, fontSize = 24.sp)
        }
    }
}

private fun decodeAlbumPreview(context: android.content.Context, uri: Uri): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val decodedBounds = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
        bounds.outWidth > 0 && bounds.outHeight > 0
    } ?: false
    if (!decodedBounds) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 1200 || bounds.outHeight / sampleSize > 1200) sampleSize *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } ?: return null
    return applyExifOrientation(context, uri, decoded)
}

private fun decodeViewerBitmap(context: android.content.Context, uri: Uri): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val decodedBounds = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
        bounds.outWidth > 0 && bounds.outHeight > 0
    } ?: false
    if (!decodedBounds) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 2400 || bounds.outHeight / sampleSize > 2400) sampleSize *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } ?: return null
    return applyExifOrientation(context, uri, decoded)
}

private fun applyExifOrientation(
    context: android.content.Context,
    uri: Uri,
    bitmap: android.graphics.Bitmap,
): android.graphics.Bitmap {
    val exif = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input -> ExifInterface(input) }
    }.getOrNull() ?: return bitmap
    val degrees = exif.rotationDegrees
    val flipped = exif.isFlipped
    if (degrees == 0 && !flipped) return bitmap

    val matrix = Matrix().apply {
        postRotate(degrees.toFloat())
        if (flipped) postScale(-1f, 1f)
    }
    val oriented = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (oriented !== bitmap) bitmap.recycle()
    return oriented
}

private fun sharePhoto(context: android.content.Context, photo: AlbumPhoto) {
    val uri = Uri.parse(photo.developedUri ?: photo.originalUri)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (photo.mediaKind == AlbumMediaKind.VIDEO) "video/mp4" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(
            Intent.EXTRA_TEXT,
            JSONObject()
                .put("version", 1)
                .put("recipe", JSONObject()
                    .put("body", photo.cameraBodyId)
                    .put("lens", photo.lensId)
                    .put("film", photo.filmStockId)
                ).toString(),
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.album_share_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, context.getString(R.string.album_share_failed), Toast.LENGTH_LONG).show() }
}

private fun monthLabel(epochMillis: Long): String = SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(epochMillis))
private fun dateGroupLabel(epochMillis: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochMillis))
private fun dateLabel(epochMillis: Long): String = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(epochMillis))
private fun formatDuration(durationMillis: Long): String = "${durationMillis / 60_000}:${((durationMillis / 1_000) % 60).toString().padStart(2, '0')}"

private fun decodeVideoThumbnail(context: android.content.Context, uri: Uri): android.graphics.Bitmap? {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val scale = maxOf(frame.width / 1200f, frame.height / 1200f, 1f)
        if (scale <= 1f) frame else android.graphics.Bitmap.createScaledBitmap(
            frame,
            (frame.width / scale).toInt().coerceAtLeast(1),
            (frame.height / scale).toInt().coerceAtLeast(1),
            true,
        )
    } finally {
        retriever.release()
    }
}

@Composable
private fun AlbumFilter.labelResource(): Int = when (this) {
    AlbumFilter.ALL -> R.string.album_filter_all
    AlbumFilter.FAVORITES -> R.string.album_filter_favorites
    AlbumFilter.DEVELOPED -> R.string.album_filter_developed
    AlbumFilter.ORIGINALS -> R.string.album_filter_originals
}

@Composable
private fun AlbumGrouping.labelResource(): Int = when (this) {
    AlbumGrouping.DATE -> R.string.album_group_date
    AlbumGrouping.ROLL -> R.string.album_group_roll
    AlbumGrouping.FILM -> R.string.album_group_film
    AlbumGrouping.BODY -> R.string.album_group_body
}
