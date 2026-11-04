package app.theia.android.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.theia.android.R
import java.util.Locale

/** Shows a device-local 3-axis horizon indicator when a rotation-vector sensor is available. */
@Composable
fun HorizonLevelOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sensorManager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember(sensorManager) { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var yaw by remember { mutableStateOf(0f) }
    var pitch by remember { mutableStateOf(0f) }
    var roll by remember { mutableStateOf(0f) }

    DisposableEffect(sensorManager, sensor) {
        if (sensor != null) {
            val listener = object : SensorEventListener {
                private val rotationMatrix = FloatArray(9)
                private val orientation = FloatArray(3)
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    yaw = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        } else {
            onDispose { }
        }
    }

    if (sensor == null) {
        Surface(modifier.widthIn(max = 340.dp), color = Color(0xCC182344), shape = RoundedCornerShape(10.dp)) {
            Text(
                stringResource(R.string.camera_horizon_unavailable),
                color = Color(0xFFE7D7B7),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        return
    }

    val axes = stringResource(
        R.string.camera_horizon_axes,
        String.format(Locale.getDefault(), "%.1f", yaw),
        String.format(Locale.getDefault(), "%.1f", pitch),
        String.format(Locale.getDefault(), "%.1f", roll),
    )
    val description = "${stringResource(R.string.camera_horizon_level)}. $axes"
    Surface(
        modifier.widthIn(max = 340.dp).semantics { contentDescription = description },
        color = Color(0xCC182344),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.camera_horizon_level), color = Color(0xFFEAAE35), style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 18.dp).graphicsLayer { rotationZ = roll }) {
                drawLine(Color(0xFFE7D7B7), start = androidx.compose.ui.geometry.Offset(0f, size.height / 2), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx())
                drawCircle(Color(0xFFEAAE35), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2))
            }
            Text(axes, color = Color(0xFFE7D7B7), style = MaterialTheme.typography.labelSmall)
        }
    }
}
