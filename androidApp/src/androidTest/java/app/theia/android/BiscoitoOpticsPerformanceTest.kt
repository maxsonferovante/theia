package app.theia.android

import android.os.Build
import android.os.SystemClock
import android.util.Log
import app.theia.domain.catalog.BuiltInFilmVisualProfiles
import app.theia.domain.catalog.OpticalImageRenderer
import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.LensId
import app.theia.android.roll.AndroidOpticalImageRenderer
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiscoitoOpticsPerformanceTest {
    @Test
    fun reportsOpticalStageTimingForARepeatable1080pChart() {
        val width = 1_920
        val height = 1_080
        val chart = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val grid = x % 120 < 2 || y % 120 < 2
            val gradient = (x * 255 / width + y * 80 / height).coerceIn(0, 255)
            val color = if (grid) 24 else gradient
            (0xff shl 24) or (color shl 16) or (color shl 8) or color
        }
        val profile = requireNotNull(BuiltInFilmVisualProfiles.resolve(
            CaptureRecipe(CameraBodyId("body-helia"), LensId("lens-biscoito-32"), FilmStockId("film-apricot-noon")),
        ))
        val neutral = profile.copy(radialSoftness = 0.0, chromaticAberration = 0.0, opticalFlare = 0.0)

        runBlocking {
            AndroidOpticalImageRenderer.render(IntArray(320 * 180) { chart[it] }, 320, 180, profile)
        }
        val baselineMs = measureMedianMs(3) { OpticalImageRenderer.render(chart, width, height, neutral) }
        val opticalMs = measureMedianMs(3) { runBlocking { AndroidOpticalImageRenderer.render(chart, width, height, profile) } }
        val output = runBlocking { AndroidOpticalImageRenderer.render(chart, width, height, profile) }

        assertEquals(chart.size, output.size)
        Log.i(
            TAG,
            "model=${Build.MODEL}; api=${Build.VERSION.SDK_INT}; image=${width}x${height}; " +
                "baseline_copy_median_ms=$baselineMs; optical_stage_median_ms=$opticalMs; " +
                "optical_stage_runs=3; scope=android_parallel_offline_optical_stage",
        )
    }

    private fun measureMedianMs(iterations: Int, action: () -> IntArray): Long {
        val measurements = LongArray(iterations) {
            val startedAt = SystemClock.elapsedRealtimeNanos()
            action()
            (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000
        }
        return measurements.sorted()[measurements.size / 2]
    }

    private companion object {
        const val TAG = "BiscoitoOpticsPerformance"
    }
}
