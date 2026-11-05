package app.theia.android.album

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import app.theia.android.R
import app.theia.android.roll.AndroidFilmRollService
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmRollCapacity
import app.theia.domain.model.FilmRollId
import app.theia.domain.model.FilmRollRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RollViolet = Color(0xFF2D2D57)
private val RollGold = Color(0xFFEAAE35)
private val RollIvory = Color(0xFFE7D7B7)
private val RollStone = Color(0xFFB6A58C)

/** Local roll controls. `onVisiblePhotoIdsChanged` lets the album keep undeveloped originals hidden. */
@Composable
fun AlbumRollPanel(
    service: AndroidFilmRollService,
    selectedRecipe: CaptureRecipe?,
    onVisiblePhotoIdsChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var records by remember(service) { mutableStateOf(emptyList<FilmRollRecord>()) }
    var activeRollId by remember(service) { mutableStateOf<FilmRollId?>(null) }
    var capacity by remember { mutableStateOf(FilmRollCapacity.EXPOSURES_24) }
    var earlyCandidate by remember { mutableStateOf<FilmRollRecord?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var revealRollId by remember { mutableStateOf<FilmRollId?>(null) }
    var knownDevelopedRollIds by remember { mutableStateOf<Set<FilmRollId>?>(null) }
    val context = LocalContext.current
    val systemAnimationsEnabled = rememberSystemAnimationsEnabled(context)
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val state = withContext(Dispatchers.IO) {
            Triple(service.allRolls(), service.activeRoll()?.roll?.id, service.visiblePhotoIds())
        }
        val newlyDeveloped = state.first.filter { it.roll.developedAtEpochMillis != null }.mapTo(mutableSetOf()) { it.roll.id }
        val previouslyDeveloped = knownDevelopedRollIds
        if (previouslyDeveloped == null) {
            val recent = state.first.firstOrNull {
                val developedAt = it.roll.developedAtEpochMillis
                developedAt != null && System.currentTimeMillis() - developedAt in 0..RECENT_REVEAL_WINDOW_MILLIS
            }
            revealRollId = recent?.roll?.id
        } else {
            revealRollId = (newlyDeveloped - previouslyDeveloped).firstOrNull()
        }
        knownDevelopedRollIds = newlyDeveloped
        records = state.first
        activeRollId = state.second
        onVisiblePhotoIdsChanged(state.third)
    }

    LaunchedEffect(service) { runCatching { refresh() }.onFailure { errorMessage = it.localizedMessage } }
    LaunchedEffect(revealRollId) {
        if (revealRollId != null) {
            delay(REVEAL_DURATION_MILLIS)
            revealRollId = null
        }
    }

    fun runRollAction(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            errorMessage = null
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onFailure { errorMessage = it.localizedMessage ?: "" }
            runCatching { refresh() }.onFailure { errorMessage = it.localizedMessage ?: "" }
            busy = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().background(RollViolet, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.album_rolls_title), color = RollGold, fontSize = 18.sp, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.album_rolls_fixed_recipe), color = RollStone, fontSize = 13.sp)
        Text(stringResource(R.string.album_roll_instant_note), color = RollStone, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilmRollCapacity.values().forEach { option ->
                FilterChip(
                    selected = capacity == option,
                    onClick = { capacity = option },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = {
                        Text(stringResource(if (option == FilmRollCapacity.EXPOSURES_24) R.string.album_roll_capacity_24 else R.string.album_roll_capacity_36))
                    },
                )
            }
        }
        if (selectedRecipe == null) {
            Text(stringResource(R.string.album_roll_choose_recipe), color = RollStone)
        } else {
            Text(
                stringResource(R.string.album_roll_recipe, selectedRecipe.bodyId.value, selectedRecipe.lensId.value, selectedRecipe.filmStockId.value),
                color = RollIvory,
                fontSize = 13.sp,
            )
            Button(
                onClick = { runRollAction { service.createRoll(selectedRecipe, capacity) } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.album_roll_start)) }
        }
        errorMessage?.takeIf(String::isNotBlank)?.let {
            Text(stringResource(R.string.album_roll_action_failed, it), color = RollIvory)
        }
        if (records.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                records.forEach { record ->
                    RollCard(
                        record = record,
                        active = activeRollId == record.roll.id,
                        busy = busy,
                        revealed = revealRollId == record.roll.id,
                        animateReveal = systemAnimationsEnabled,
                        onActivate = { runRollAction { service.setActiveRoll(record.roll.id) } },
                        onDevelopFull = { runRollAction { service.developRoll(record.roll.id, allowEarly = false) } },
                        onDevelopEarly = { earlyCandidate = record },
                    )
                }
            }
        }
    }

    earlyCandidate?.let { record ->
        AlertDialog(
            onDismissRequest = { earlyCandidate = null },
            title = { Text(stringResource(R.string.album_roll_early_title), modifier = Modifier.semantics { heading() }) },
            text = { Text(stringResource(R.string.album_roll_early_warning, record.roll.photoIds.size, record.roll.capacity.exposures)) },
            confirmButton = {
                Button(onClick = {
                    earlyCandidate = null
                    runRollAction { service.developRoll(record.roll.id, allowEarly = true) }
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.album_roll_develop_early)) }
            },
            dismissButton = {
                TextButton(onClick = { earlyCandidate = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.album_roll_cancel))
                }
            },
        )
    }
}

@Composable
internal fun rememberSystemAnimationsEnabled(context: Context): Boolean {
    val animationsEnabled = remember(context) { mutableStateOf(readSystemAnimationsEnabled(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                animationsEnabled.value = readSystemAnimationsEnabled(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return animationsEnabled.value
}

private fun readSystemAnimationsEnabled(context: Context): Boolean {
    val animatorDurationScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    val valueAnimatorEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    return systemAnimationsEnabledForPlatform(
        sdkInt = Build.VERSION.SDK_INT,
        animatorDurationScale = animatorDurationScale,
        valueAnimatorEnabled = valueAnimatorEnabled,
    )
}

internal fun systemAnimationsEnabledForPlatform(
    sdkInt: Int,
    animatorDurationScale: Float,
    valueAnimatorEnabled: Boolean,
): Boolean = if (sdkInt >= Build.VERSION_CODES.O) valueAnimatorEnabled else animatorDurationScale > 0f

@Composable
private fun RollCard(
    record: FilmRollRecord,
    active: Boolean,
    busy: Boolean,
    revealed: Boolean,
    animateReveal: Boolean,
    onActivate: () -> Unit,
    onDevelopFull: () -> Unit,
    onDevelopEarly: () -> Unit,
) {
    val count = record.roll.photoIds.size
    val full = count == record.roll.capacity.exposures
    val developed = record.roll.developedAtEpochMillis != null
    val remaining = (record.roll.capacity.exposures - count).coerceAtLeast(0)
    val revealAlpha by animateFloatAsState(
        targetValue = if (revealed && animateReveal) 1f else if (revealed) 1f else 0.82f,
        animationSpec = tween(if (animateReveal) 420 else 0),
        label = "roll reveal",
    )
    Card(
        modifier = Modifier.graphicsLayer { alpha = if (revealed && !animateReveal) 1f else revealAlpha },
        colors = CardDefaults.cardColors(containerColor = if (revealed) Color(0xFF26385B) else Color(0xFF182344)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(
                    R.string.album_roll_progress,
                    if (record.roll.capacity == FilmRollCapacity.EXPOSURES_24) 24 else 36,
                    count,
                    remaining,
                ),
                color = RollIvory,
            )
            Text(stringResource(R.string.album_roll_recipe, record.recipe.bodyId.value, record.recipe.lensId.value, record.recipe.filmStockId.value), color = RollStone, fontSize = 12.sp)
            when {
                revealed -> Text(
                    stringResource(R.string.album_roll_reveal_complete),
                    color = RollGold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                developed -> Text(stringResource(R.string.album_roll_developed_status), color = RollGold)
                active -> Text(stringResource(R.string.album_roll_active_status), color = RollGold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!developed && !active && !full) {
                    TextButton(onClick = onActivate, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.album_roll_activate))
                    }
                }
                if (!developed && full) {
                    Button(onClick = onDevelopFull, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.album_roll_develop_full))
                    }
                } else if (!developed && count > 0) {
                    TextButton(onClick = onDevelopEarly, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.album_roll_develop_early))
                    }
                }
            }
        }
    }
}

private const val REVEAL_DURATION_MILLIS = 2_800L
private const val RECENT_REVEAL_WINDOW_MILLIS = 20_000L
