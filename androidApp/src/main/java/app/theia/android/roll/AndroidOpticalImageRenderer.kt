package app.theia.android.roll

import app.theia.domain.catalog.OpticalImageRenderer
import app.theia.domain.model.ResolvedVisualParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Uses a small CPU pool so high-resolution still development does not block on one worker. */
internal object AndroidOpticalImageRenderer {
    suspend fun render(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: ResolvedVisualParameters,
    ): IntArray = coroutineScope {
        require(width > 0 && height > 0 && pixels.size == width * height)
        val workerCount = minOf(Runtime.getRuntime().availableProcessors().coerceAtLeast(1), 4, height)
        val rowsPerWorker = (height + workerCount - 1) / workerCount
        val result = IntArray(pixels.size)
        (0 until workerCount).mapNotNull { worker ->
            val firstRow = worker * rowsPerWorker
            val lastRow = minOf(firstRow + rowsPerWorker, height)
            if (firstRow >= lastRow) null else async(Dispatchers.Default) {
                OpticalImageRenderer.renderRows(pixels, width, height, profile, firstRow, lastRow, result)
            }
        }.awaitAll()
        result
    }
}
