package app.theia.android.geotag

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.theia.android.R
import app.theia.domain.model.UserPreferences
import app.theia.domain.port.LocalGeotaggedPhoto
import kotlinx.coroutines.launch
import java.util.Locale

/** Offline, device-local geotag controls and an approximate relative-position plot. */
@Composable
fun GeotagLabPanel(
    service: AndroidGeotagService,
    preferences: UserPreferences,
    selectedPhoto: Uri?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var entries by remember(service) { mutableStateOf<List<LocalGeotaggedPhoto>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        entries = service.photosWithGeotags()
    }

    LaunchedEffect(service) { refresh() }

    Column(
        modifier.fillMaxWidth().background(Color(0xFF2D2D57), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.geotag_lab_title),
            color = Color(0xFFEAAE35),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.geotag_lab_privacy), color = Color(0xFFE7D7B7), style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = {
                val uri = selectedPhoto
                if (uri == null) {
                    message = null
                    return@Button
                }
                scope.launch {
                    try {
                        val geotag = service.addToPhoto(preferences, uri)
                        message = if (geotag == null) "no-location" else "added"
                        refresh()
                    } catch (_: Exception) {
                        message = "failed"
                    }
                }
            },
            enabled = selectedPhoto != null && preferences.geotaggingEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.geotag_add_to_selected)) }

        if (message != null) {
            Text(
                stringResource(
                    when (message) {
                        "added" -> R.string.geotag_added
                        "removed" -> R.string.geotag_removed
                        "no-location" -> R.string.geotag_unavailable
                        else -> R.string.geotag_failed
                    },
                ),
                color = Color(0xFFE7D7B7),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (entries.isEmpty()) {
            Text(stringResource(R.string.geotag_empty), color = Color(0xFFB6A58C))
        } else {
            LocalGeotagPlot(entries, Modifier.fillMaxWidth().height(168.dp))
            entries.forEach { entry ->
                val coords = String.format(Locale.ROOT, "%.5f, %.5f", entry.geotag.latitude, entry.geotag.longitude)
                val coordinateLabel = stringResource(R.string.geotag_coordinates)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        coords,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "$coordinateLabel $coords" },
                        color = Color(0xFFE7D7B7),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = {
                        scope.launch {
                            runCatching { service.removeFromPhoto(Uri.parse(entry.photoLocation.value)) }
                                .onSuccess { message = "removed" }
                                .onFailure { message = "failed" }
                            refresh()
                        }
                    }) { Text(stringResource(R.string.geotag_remove)) }
                }
            }
        }
    }
}

@Composable
private fun LocalGeotagPlot(entries: List<LocalGeotaggedPhoto>, modifier: Modifier = Modifier) {
    val plotDescription = stringResource(R.string.geotag_plot_description, entries.size)
    val points = remember(entries) {
        val minLat = entries.minOf { it.geotag.latitude }
        val maxLat = entries.maxOf { it.geotag.latitude }
        val minLon = entries.minOf { it.geotag.longitude }
        val maxLon = entries.maxOf { it.geotag.longitude }
        entries.map { item ->
            val x = if (maxLon == minLon) 0.5f else ((item.geotag.longitude - minLon) / (maxLon - minLon)).toFloat()
            val y = if (maxLat == minLat) 0.5f else (1f - ((item.geotag.latitude - minLat) / (maxLat - minLat)).toFloat())
            x to y
        }
    }
    Canvas(
        modifier.semantics {
            contentDescription = plotDescription
        },
    ) {
        drawRoundRect(color = Color(0xFF182344), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()))
        val inset = 18.dp.toPx()
        for (step in 1..3) {
            val x = size.width * step / 4f
            val y = size.height * step / 4f
            drawLine(Color(0xFF4B5743), androidx.compose.ui.geometry.Offset(x, inset), androidx.compose.ui.geometry.Offset(x, size.height - inset), strokeWidth = 1.dp.toPx())
            drawLine(Color(0xFF4B5743), androidx.compose.ui.geometry.Offset(inset, y), androidx.compose.ui.geometry.Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
        }
        points.forEach { (x, y) ->
            drawCircle(
                color = Color(0xFFEAAE35),
                radius = 6.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(inset + x * (size.width - inset * 2), inset + y * (size.height - inset * 2)),
            )
        }
    }
}
