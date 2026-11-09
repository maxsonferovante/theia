package app.theia.domain.catalog

import app.theia.domain.model.ResolvedVisualParameters
import kotlin.math.floor
import kotlin.math.min

/**
 * Offline optical transform for packed ARGB images. Coordinates and radii are
 * relative to the rendered frame; sampling clamps at its edges.
 */
object OpticalImageRenderer {
    /** Vignette curve shared with the real-time Android shader. Radius is 4 * normalized distance squared. */
    fun vignetteFactor(radiusSquared: Double, amount: Double): Double {
        val transition = ((radiusSquared - 0.18) / (0.75 - 0.18)).coerceIn(0.0, 1.0)
        val smoothFalloff = transition * transition * (3.0 - 2.0 * transition)
        return 1.0 - amount * smoothFalloff
    }

    fun render(pixels: IntArray, width: Int, height: Int, profile: ResolvedVisualParameters): IntArray {
        val result = IntArray(pixels.size)
        renderRows(pixels, width, height, profile, 0, height, result)
        return result
    }

    /** Renders a disjoint row range into a caller-owned output buffer for safe parallel CPU work. */
    fun renderRows(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: ResolvedVisualParameters,
        firstRow: Int,
        lastRowExclusive: Int,
        output: IntArray,
    ) {
        require(width > 0 && height > 0 && pixels.size == width * height && output.size == pixels.size)
        require(firstRow in 0..height && lastRowExclusive in firstRow..height)
        require(profile.schemaVersion == BuiltInFilmVisualProfiles.RENDERING_SCHEMA_V1) { "Unsupported optical rendering schema ${profile.schemaVersion}" }
        if (profile.radialSoftness == 0.0 && profile.chromaticAberration == 0.0 && profile.opticalFlare == 0.0) {
            pixels.copyInto(output, firstRow * width, firstRow * width, lastRowExclusive * width)
            return
        }
        val shortSide = min(width, height).toDouble()
        val glowRadius = shortSide * 0.015

        for (y in firstRow until lastRowExclusive) for (x in 0 until width) {
            val index = y * width + x
            val nx = (x + 0.5) / width - 0.5
            val ny = (y + 0.5) / height - 0.5
            val radius2 = 4.0 * (nx * nx + ny * ny)
            val baseX = (nx * (1.0 + radius2 * profile.distortion) + 0.5) * width - 0.5
            val baseY = (ny * (1.0 + radius2 * profile.distortion) + 0.5) * height - 0.5
            val center = sampleLinear(pixels, width, height, baseX, baseY)
            var red = ((center ushr 16) and 255).toInt()
            var green = ((center ushr 8) and 255).toInt()
            var blue = (center and 255).toInt()

            if (profile.chromaticAberration > 0.0) {
                val fringeX = nx * radius2 * profile.chromaticAberration * width
                val fringeY = ny * radius2 * profile.chromaticAberration * height
                red = (sampleNearest(pixels, width, height, baseX + fringeX, baseY + fringeY) ushr 16) and 255
                blue = sampleNearest(pixels, width, height, baseX - fringeX, baseY - fringeY) and 255
            }

            if (profile.radialSoftness > 0.0) {
                val blurMix = (profile.radialSoftness * radius2).coerceIn(0.0, 0.65)
                if (blurMix > 0.001) {
                    val blurRadius = shortSide * 0.006 * profile.radialSoftness * min(radius2, 1.0)
                    val right = sampleNearest(pixels, width, height, baseX + blurRadius, baseY)
                    val left = sampleNearest(pixels, width, height, baseX - blurRadius, baseY)
                    val down = sampleNearest(pixels, width, height, baseX, baseY + blurRadius)
                    val up = sampleNearest(pixels, width, height, baseX, baseY - blurRadius)
                    val redBlur = (((right ushr 16) and 255) + ((left ushr 16) and 255) +
                        ((down ushr 16) and 255) + ((up ushr 16) and 255)) / 4.0
                    val greenBlur = (((right ushr 8) and 255) + ((left ushr 8) and 255) +
                        ((down ushr 8) and 255) + ((up ushr 8) and 255)) / 4.0
                    val blueBlur = ((right and 255) + (left and 255) + (down and 255) + (up and 255)) / 4.0
                    red = mix(red, redBlur, blurMix)
                    green = mix(green, greenBlur, blurMix)
                    blue = mix(blue, blueBlur, blurMix)
                }
            }

            if (profile.opticalFlare > 0.0) {
                val right = sampleNearest(pixels, width, height, baseX + glowRadius, baseY)
                val left = sampleNearest(pixels, width, height, baseX - glowRadius, baseY)
                val down = sampleNearest(pixels, width, height, baseX, baseY + glowRadius)
                val up = sampleNearest(pixels, width, height, baseX, baseY - glowRadius)
                val redGlow = (((right ushr 16) and 255) + ((left ushr 16) and 255) +
                    ((down ushr 16) and 255) + ((up ushr 16) and 255)) * 0.25
                val greenGlow = (((right ushr 8) and 255) + ((left ushr 8) and 255) +
                    ((down ushr 8) and 255) + ((up ushr 8) and 255)) * 0.25
                val blueGlow = ((right and 255) + (left and 255) + (down and 255) + (up and 255)) * 0.25
                val redGain = ((redGlow - 190.0) / 65.0).coerceIn(0.0, 1.0) * profile.opticalFlare * 25.0
                val greenGain = ((greenGlow - 190.0) / 65.0).coerceIn(0.0, 1.0) * profile.opticalFlare * 25.0
                val blueGain = ((blueGlow - 190.0) / 65.0).coerceIn(0.0, 1.0) * profile.opticalFlare * 25.0
                red = (red + redGain).toInt().coerceIn(0, 255)
                green = (green + greenGain).toInt().coerceIn(0, 255)
                blue = (blue + blueGain).toInt().coerceIn(0, 255)
            }

            output[index] = (pixels[index] and -0x1000000) or (red shl 16) or (green shl 8) or blue
        }
    }

    private fun mix(source: Int, sampled: Double, amount: Double): Int =
        (source * (1.0 - amount) + sampled * amount).toInt().coerceIn(0, 255)

    /** Bilinear packed RGB sample; channel values are packed in both halves to reuse two texture reads. */
    private fun sampleLinear(pixels: IntArray, width: Int, height: Int, x: Double, y: Double): Long {
        val cx = x.coerceIn(0.0, (width - 1).toDouble())
        val cy = y.coerceIn(0.0, (height - 1).toDouble())
        val x0 = floor(cx).toInt()
        val y0 = floor(cy).toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val fx = cx - x0
        val fy = cy - y0
        val topLeft = pixels[y0 * width + x0]
        val topRight = pixels[y0 * width + x1]
        val bottomLeft = pixels[y1 * width + x0]
        val bottomRight = pixels[y1 * width + x1]
        val red = interpolate(topLeft, topRight, bottomLeft, bottomRight, 16, fx, fy)
        val green = interpolate(topLeft, topRight, bottomLeft, bottomRight, 8, fx, fy)
        val blue = interpolate(topLeft, topRight, bottomLeft, bottomRight, 0, fx, fy)
        return (red shl 16) or (green shl 8) or blue or (red shl 48) or (green shl 40) or (blue shl 32)
    }

    private fun interpolate(
        topLeft: Int,
        topRight: Int,
        bottomLeft: Int,
        bottomRight: Int,
        shift: Int,
        fx: Double,
        fy: Double,
    ): Long {
        val top = ((topLeft ushr shift) and 255) * (1.0 - fx) + ((topRight ushr shift) and 255) * fx
        val bottom = ((bottomLeft ushr shift) and 255) * (1.0 - fx) + ((bottomRight ushr shift) and 255) * fx
        return (top * (1.0 - fy) + bottom * fy).toInt().coerceIn(0, 255).toLong()
    }

    private fun sampleNearest(pixels: IntArray, width: Int, height: Int, x: Double, y: Double): Int {
        val sampleX = floor(x + 0.5).toInt().coerceIn(0, width - 1)
        val sampleY = floor(y + 0.5).toInt().coerceIn(0, height - 1)
        return pixels[sampleY * width + sampleX]
    }
}
