package app.theia.domain.catalog

import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.LensId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class OpticalImageRendererTest {
    @Test
    fun biscoitoVignetteMatchesProgressivePreviewCurve() {
        val amount = biscoitoProfile().vignette

        assertEquals(1.0, OpticalImageRenderer.vignetteFactor(0.0, amount))
        assertTrue(OpticalImageRenderer.vignetteFactor(0.5, amount) in 0.72..1.0)
        assertEquals(1.0 - amount, OpticalImageRenderer.vignetteFactor(1.0, amount))
    }

    @Test
    fun biscoitoChangesEdgeDetailWhilePreservingImageDimensions() {
        val width = 48
        val height = 32
        val pixels = IntArray(width * height) { index ->
            if ((index % width) % 2 == 0) 0xff202020.toInt() else 0xffe0e0e0.toInt()
        }
        val profile = assertNotNull(BuiltInFilmVisualProfiles.resolve(
            CaptureRecipe(CameraBodyId("body-helia"), LensId("lens-biscoito-32"), FilmStockId("film-apricot-noon")),
        ))
        val rendered = OpticalImageRenderer.render(pixels, width, height, profile)
        assertEquals(pixels.size, rendered.size)
        assertNotEquals(pixels[2], rendered[2])
    }

    @Test
    fun existingLensesWithOnlyLegacyDistortionKeepTheirPreviousPhotoOutput() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (x % 8 == 0 || y % 8 == 0) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val wideProfile = assertNotNull(BuiltInFilmVisualProfiles.resolve(
            CaptureRecipe(CameraBodyId("body-helia"), LensId("lens-wide"), FilmStockId("film-apricot-noon")),
        )).copy(radialSoftness = 0.0, chromaticAberration = 0.0, opticalFlare = 0.0)

        val rendered = OpticalImageRenderer.render(pixels, width, height, wideProfile)

        assertEquals(pixels.toList(), rendered.toList())
    }

    @Test
    fun radialSoftnessPreservesCenterDetailAndReducesEdgeContrast() {
        val width = 512
        val height = 512
        val pixels = IntArray(width * height) { index ->
            if ((index % width) % 2 == 0) 0xff202020.toInt() else 0xffe0e0e0.toInt()
        }
        val profile = biscoitoProfile().copy(distortion = 0.0, chromaticAberration = 0.0, opticalFlare = 0.0)
        val rendered = OpticalImageRenderer.render(pixels, width, height, profile)
        fun gray(values: IntArray, x: Int) = values[(height / 2) * width + x] and 0xff

        assertEquals(gray(pixels, width / 2), gray(rendered, width / 2))
        assertTrue(abs(gray(rendered, 2) - gray(rendered, 3)) < abs(gray(pixels, 2) - gray(pixels, 3)))
        assertTrue(rendered.all { (it ushr 24) == 0xff }, "Edge sampling must not create transparent pixels")
    }

    @Test
    fun chromaticFringeGrowsAtHighContrastFrameEdges() {
        val width = 1_024
        val height = 1_024
        val pixels = IntArray(width * height) { index ->
            if (index % width < 950) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val profile = biscoitoProfile().copy(distortion = 0.0, radialSoftness = 0.0, opticalFlare = 0.0)
        val rendered = OpticalImageRenderer.render(pixels, width, height, profile)
        val edge = rendered[512 * width + 949]
        val red = (edge ushr 16) and 0xff
        val blue = edge and 0xff

        assertTrue(red > blue, "The radial fringe should separate color channels at the frame edge (red=$red, blue=$blue)")
        assertEquals(0xff000000.toInt(), rendered[512 * width + 512])
    }

    @Test
    fun flareRespondsToNearbyHighlightsAndLeavesDarkScenesUnchanged() {
        val width = 128
        val height = 128
        val center = (height / 2) * width + width / 2
        val darkScene = IntArray(width * height) { 0xff000000.toInt() }
        val highlightScene = darkScene.copyOf().apply {
            for (y in 0 until height) for (x in 0 until width) {
                if (x != width / 2 || y != height / 2) this[y * width + x] = 0xffffffff.toInt()
            }
        }
        val profile = biscoitoProfile().copy(distortion = 0.0, radialSoftness = 0.0, chromaticAberration = 0.0, opticalFlare = 1.0)
        val darkOutput = OpticalImageRenderer.render(darkScene, width, height, profile)
        val highlightOutput = OpticalImageRenderer.render(highlightScene, width, height, profile)

        assertEquals(darkScene[center], darkOutput[center])
        assertTrue((highlightOutput[center] and 0xff) > (highlightScene[center] and 0xff))
    }

    @Test
    fun distortionChangesRepeatableGridGeometryWithoutChangingOutputBounds() {
        val width = 256
        val height = 256
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (x % 32 == 0 || y % 32 == 0) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val profile = biscoitoProfile().copy(radialSoftness = 0.02, chromaticAberration = 0.0, opticalFlare = 0.0)
        val distorted = OpticalImageRenderer.render(pixels, width, height, profile)
        val reference = OpticalImageRenderer.render(pixels, width, height, profile.copy(distortion = 0.0))

        assertTrue(distorted.indices.any { distorted[it] != reference[it] })
        assertEquals(pixels.size, distorted.size)
        assertTrue(distorted.all { (it ushr 24) == 0xff })
    }

    @Test
    fun independentlyRenderedRowRangesMatchAFullFrame() {
        val width = 48
        val height = 32
        val pixels = IntArray(width * height) { index ->
            if ((index % width) % 2 == 0) 0xff202020.toInt() else 0xffe0e0e0.toInt()
        }
        val profile = biscoitoProfile()
        val expected = OpticalImageRenderer.render(pixels, width, height, profile)
        val rows = IntArray(pixels.size)
        val split = height / 2
        OpticalImageRenderer.renderRows(pixels, width, height, profile, 0, split, rows)
        OpticalImageRenderer.renderRows(pixels, width, height, profile, split, height, rows)

        assertEquals(expected.toList(), rows.toList())
    }

    private fun biscoitoProfile() = assertNotNull(BuiltInFilmVisualProfiles.resolve(
        CaptureRecipe(CameraBodyId("body-helia"), LensId("lens-biscoito-32"), FilmStockId("film-apricot-noon")),
    ))
}
