package app.theia.android.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ArtworkPalette = listOf(
    Color(0xFFEAAE35), // solar gold
    Color(0xFFA86D1F), // antique gold
    Color(0xFFE7D7B7), // ivory
    Color(0xFF8E482B), // terracotta
    Color(0xFF5E321E), // dark bronze
    Color(0xFF4B5743), // muted olive
    Color(0xFF182344), // deep sky
    Color(0xFF2D2D57), // cosmic violet
    Color(0xFF6D5A82), // nebula lilac
    Color(0xFFB6A58C), // ruin stone
    Color(0xFF121522), // near-black
)

/**
 * Draws an original, stable vector illustration for a catalog entry.
 * The artwork key selects a repeatable composition and palette; the
 * description is exposed to screen readers as the image's accessible label.
 */
@Composable
fun CatalogArtworkCard(
    artworkKey: String,
    visualDescription: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(shape)
            .background(Color(0xFF182344))
            .semantics { contentDescription = visualDescription },
    ) {
        val seed = artworkKey.stableSeed()
        val colors = artworkColors(seed)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(colors[0], colors[1], colors[2]),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        drawAmbientStars(seed, colors[3])
        when {
            artworkKey.startsWith("artwork.body.") -> drawCameraBody(seed, colors)
            artworkKey.startsWith("artwork.lens.") -> drawLens(seed, colors)
            artworkKey.startsWith("artwork.film.") -> drawFilmPrint(seed, colors)
            else -> drawCollectionMotif(seed, colors)
        }
        drawFrame(colors[4])
    }
}

private fun String.stableSeed(): Int = fold(0x811C9DC5.toInt()) { hash, char ->
    (hash xor char.code) * 0x01000193
}

private fun artworkColors(seed: Int): List<Color> {
    val start = (seed and Int.MAX_VALUE) % ArtworkPalette.size
    return (0..4).map { ArtworkPalette[(start + it * 2) % ArtworkPalette.size] }
}

private fun DrawScope.drawAmbientStars(seed: Int, color: Color) {
    val count = 6 + (seed and 3)
    repeat(count) { index ->
        val x = size.width * (0.1f + stableFraction(seed, index * 3) * 0.8f)
        val y = size.height * (0.08f + stableFraction(seed, index * 3 + 1) * 0.38f)
        val radius = size.minDimension * (0.006f + stableFraction(seed, index * 3 + 2) * 0.009f)
        drawCircle(color.copy(alpha = 0.55f), radius, Offset(x, y))
    }
}

private fun DrawScope.drawCameraBody(seed: Int, colors: List<Color>) {
    val w = size.width
    val h = size.height
    val bodyWidth = w * (0.55f + stableFraction(seed, 12) * 0.12f)
    val bodyHeight = h * (0.38f + stableFraction(seed, 13) * 0.06f)
    val left = (w - bodyWidth) / 2f
    val top = h * 0.34f

    drawRoundRect(
        color = colors[3],
        topLeft = Offset(left, top),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.045f),
    )
    drawRoundRect(
        color = colors[2].copy(alpha = 0.9f),
        topLeft = Offset(left + bodyWidth * 0.08f, top - bodyHeight * 0.13f),
        size = Size(bodyWidth * 0.34f, bodyHeight * 0.18f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.025f),
    )

    val lensCenter = Offset(w * 0.51f, top + bodyHeight * 0.52f)
    val lensRadius = bodyHeight * (0.4f + stableFraction(seed, 14) * 0.06f)
    drawCircle(colors[4], lensRadius * 1.27f, lensCenter)
    drawCircle(colors[0], lensRadius, lensCenter, style = Stroke(width = h * 0.018f))
    drawCircle(colors[1].copy(alpha = 0.85f), lensRadius * 0.67f, lensCenter)
    drawCircle(colors[2].copy(alpha = 0.82f), lensRadius * 0.34f, lensCenter)
    drawCircle(colors[0].copy(alpha = 0.7f), lensRadius * 0.1f, lensCenter + Offset(-lensRadius * 0.2f, -lensRadius * 0.2f))

    repeat(3) { index ->
        val dialX = left + bodyWidth * (0.16f + index * 0.13f)
        drawCircle(colors[0], h * (0.023f + index * 0.004f), Offset(dialX, top + bodyHeight * 0.18f))
    }
    drawLine(colors[2].copy(alpha = 0.8f), Offset(left + bodyWidth * 0.73f, top + bodyHeight * 0.18f), Offset(left + bodyWidth * 0.9f, top + bodyHeight * 0.18f), h * 0.014f)
}

private fun DrawScope.drawLens(seed: Int, colors: List<Color>) {
    val w = size.width
    val h = size.height
    val centerX = w * (0.5f + (stableFraction(seed, 20) - 0.5f) * 0.12f)
    val centerY = h * 0.57f
    val barrelWidth = w * (0.28f + stableFraction(seed, 21) * 0.1f)
    val barrelHeight = h * (0.3f + stableFraction(seed, 22) * 0.12f)
    val left = centerX - barrelWidth / 2f
    val top = centerY - barrelHeight / 2f

    drawRoundRect(
        color = colors[3],
        topLeft = Offset(left, top),
        size = Size(barrelWidth, barrelHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.035f),
    )
    repeat(4) { index ->
        val x = left + barrelWidth * (0.16f + index * 0.22f)
        drawRoundRect(
            color = if (index % 2 == 0) colors[0] else colors[4],
            topLeft = Offset(x, top + barrelHeight * 0.12f),
            size = Size(barrelWidth * 0.09f, barrelHeight * 0.76f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.012f),
        )
    }
    val glassCenter = Offset(centerX, top + barrelHeight * 0.52f)
    val glassRadius = barrelHeight * 0.47f
    drawCircle(colors[2], glassRadius, glassCenter)
    drawCircle(colors[1], glassRadius * 0.78f, glassCenter)
    drawCircle(colors[6], glassRadius * 0.56f, glassCenter)
    drawCircle(colors[0].copy(alpha = 0.9f), glassRadius * 0.3f, glassCenter + Offset(glassRadius * 0.16f, -glassRadius * 0.15f))
    drawCircle(colors[2].copy(alpha = 0.8f), glassRadius * 0.1f, glassCenter + Offset(-glassRadius * 0.2f, -glassRadius * 0.24f))

    val flare = Path().apply {
        moveTo(centerX - w * 0.32f, h * 0.78f)
        lineTo(centerX - w * 0.04f, top + barrelHeight * 0.28f)
        lineTo(centerX + w * 0.33f, h * 0.78f)
    }
    drawPath(flare, colors[2].copy(alpha = 0.16f))
}

private fun DrawScope.drawFilmPrint(seed: Int, colors: List<Color>) {
    val w = size.width
    val h = size.height
    val cardWidth = w * (0.47f + stableFraction(seed, 30) * 0.13f)
    val cardHeight = h * 0.76f
    val tilt = (stableFraction(seed, 31) - 0.5f) * 0.12f
    val left = (w - cardWidth) / 2f + tilt * w
    val top = (h - cardHeight) / 2f
    drawRoundRect(
        color = colors[2],
        topLeft = Offset(left, top),
        size = Size(cardWidth, cardHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.035f),
    )

    val imageLeft = left + cardWidth * 0.08f
    val imageTop = top + cardHeight * 0.08f
    val imageWidth = cardWidth * 0.84f
    val imageHeight = cardHeight * 0.66f
    val horizon = imageTop + imageHeight * (0.43f + stableFraction(seed, 32) * 0.18f)
    drawRect(
        brush = Brush.verticalGradient(listOf(colors[4], colors[0], colors[1]), imageTop, imageTop + imageHeight),
        topLeft = Offset(imageLeft, imageTop),
        size = Size(imageWidth, imageHeight),
    )
    drawCircle(colors[2].copy(alpha = 0.86f), imageHeight * (0.1f + stableFraction(seed, 33) * 0.06f), Offset(imageLeft + imageWidth * (0.28f + stableFraction(seed, 34) * 0.42f), imageTop + imageHeight * 0.33f))

    val ridge = Path().apply {
        moveTo(imageLeft, horizon)
        lineTo(imageLeft + imageWidth * 0.28f, horizon - imageHeight * 0.18f)
        lineTo(imageLeft + imageWidth * 0.55f, horizon + imageHeight * 0.03f)
        lineTo(imageLeft + imageWidth * 0.77f, horizon - imageHeight * 0.14f)
        lineTo(imageLeft + imageWidth, horizon + imageHeight * 0.04f)
        lineTo(imageLeft + imageWidth, imageTop + imageHeight)
        lineTo(imageLeft, imageTop + imageHeight)
        close()
    }
    drawPath(ridge, colors[3].copy(alpha = 0.9f))

    val holeRadius = h * 0.012f
    repeat(4) { index ->
        val x = left + cardWidth * (0.22f + index * 0.19f)
        drawCircle(colors[0], holeRadius, Offset(x, top + cardHeight * 0.86f))
    }
    drawRoundRect(
        color = colors[0].copy(alpha = 0.76f),
        topLeft = Offset(left + cardWidth * 0.23f, top + cardHeight * 0.93f),
        size = Size(cardWidth * 0.54f, h * 0.012f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.01f),
    )
}

private fun DrawScope.drawCollectionMotif(seed: Int, colors: List<Color>) {
    val w = size.width
    val h = size.height
    val center = Offset(w * 0.5f, h * 0.55f)
    val radius = size.minDimension * 0.24f
    drawCircle(colors[2].copy(alpha = 0.14f), radius * 1.55f, center)
    drawCircle(colors[0], radius, center)
    drawCircle(colors[4], radius * 0.74f, center)
    drawCircle(colors[3], radius * 0.47f, center + Offset(radius * 0.16f, -radius * 0.12f))
    drawCircle(colors[2], radius * 0.2f, center + Offset(-radius * 0.18f, -radius * 0.2f))

    val arc = Path().apply {
        moveTo(w * 0.15f, h * 0.82f)
        cubicTo(w * 0.34f, h * 0.7f, w * 0.65f, h * 0.95f, w * 0.86f, h * 0.77f)
    }
    drawPath(arc, colors[2].copy(alpha = 0.8f), style = Stroke(width = h * 0.025f))
    repeat(4) { index ->
        val angle = (index * 1.57f) + stableFraction(seed, 40 + index) * 0.3f
        val point = Offset(center.x + kotlin.math.cos(angle) * radius * 1.4f, center.y + kotlin.math.sin(angle) * radius * 1.4f)
        drawCircle(colors[(index + 1) % colors.size], h * 0.018f, point)
    }
}

private fun DrawScope.drawFrame(color: Color) {
    drawRoundRect(
        color = color.copy(alpha = 0.55f),
        topLeft = Offset(size.width * 0.035f, size.height * 0.05f),
        size = Size(size.width * 0.93f, size.height * 0.9f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.05f),
        style = Stroke(width = size.minDimension * 0.009f),
    )
}

private fun stableFraction(seed: Int, salt: Int): Float {
    val mixed = seed * 31 + salt * 0x45d9f3b
    return (mixed ushr 8 and 0x00ffffff) / 16_777_215f
}
