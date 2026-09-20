package com.sharesafe.app

import com.sharesafe.app.core.model.CropConfig
import com.sharesafe.app.core.model.CropMethod
import com.sharesafe.app.core.render.AutoTrim
import com.sharesafe.app.core.render.PixelSampler
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeSampler(
    override val width: Int,
    override val height: Int,
    private val colorAt: (Int, Int) -> Int,
) : PixelSampler {
    override fun argb(x: Int, y: Int): Int = colorAt(x, y)
}

private fun gray(value: Int): Int = (0xFF shl 24) or (value shl 16) or (value shl 8) or value

class AutoTrimTest {

    private fun screenshotWithBorders(
        width: Int = 1000,
        height: Int = 2000,
        topBorder: Int = 60,
        bottomBorder: Int = 40,
    ): PixelSampler = FakeSampler(width, height) { x, y ->
        when {
            y < topBorder -> gray(240)
            y >= height - bottomBorder -> gray(240)
            else -> {
                // Content rows average around 60, far from the border colour.
                if ((x + y) % 2 == 0) gray(20) else gray(100)
            }
        }
    }

    @Test
    fun trimsUniformTopAndBottomBorders() {
        val sampler = screenshotWithBorders()
        val result = AutoTrim.estimate(
            sampler = sampler,
            config = CropConfig(trimSystemBars = false, trimBlankEdges = true),
        )
        assertEquals(60, result.rect.top)
        assertEquals(2000 - 40, result.rect.bottom)
        assertEquals(0, result.rect.left)
        assertEquals(1000, result.rect.right)
        assertEquals(CropMethod.BLANK_EDGES, result.method)
    }

    @Test
    fun leavesContentAloneWhenThereAreNoBorders() {
        val sampler = FakeSampler(800, 1600) { x, y ->
            if ((x + y) % 3 == 0) gray(30) else gray(200)
        }
        val result = AutoTrim.estimate(
            sampler = sampler,
            config = CropConfig(trimSystemBars = false, trimBlankEdges = true),
        )
        assertEquals(0, result.rect.top)
        assertEquals(1600, result.rect.bottom)
        assertEquals(CropMethod.NONE, result.method)
    }

    @Test
    fun systemBarInsetsTrimExactlyTheRequestedRows() {
        val sampler = screenshotWithBorders(topBorder = 0, bottomBorder = 0)
        val result = AutoTrim.estimate(
            sampler = sampler,
            config = CropConfig(trimSystemBars = true, trimBlankEdges = false),
            statusBarPx = 90,
            navBarPx = 120,
        )
        assertEquals(90, result.rect.top)
        assertEquals(1880, result.rect.bottom)
        assertEquals(CropMethod.SYSTEM_BARS, result.method)
    }

    @Test
    fun insetsAreCappedSoWeirdScreenshotsAreNotGutted() {
        val sampler = screenshotWithBorders(topBorder = 0, bottomBorder = 0)
        val result = AutoTrim.estimate(
            sampler = sampler,
            config = CropConfig(trimSystemBars = true, trimBlankEdges = false),
            statusBarPx = 900,
            navBarPx = 900,
        )
        assertEquals((2000 * 0.12f).toInt(), result.rect.top)
        assertEquals(2000 - (2000 * 0.10f).toInt(), result.rect.bottom)
    }

    @Test
    fun uniformEdgeTrimNeverEatsMoreThanFivePercent() {
        // A fully uniform image must still keep a usable crop.
        val sampler = FakeSampler(1000, 2000) { _, _ -> gray(255) }
        val result = AutoTrim.estimate(
            sampler = sampler,
            config = CropConfig(trimSystemBars = false, trimBlankEdges = true),
        )
        assertEquals(100, result.rect.top)
        assertEquals(1900, result.rect.bottom)
        assertEquals(50, result.rect.left)
        assertEquals(950, result.rect.right)
    }

    @Test
    fun scalesDeviceInsetsOntoRescaledScreenshots() {
        assertEquals(100, AutoTrim.scaleInset(100, imageWidth = 1080, screenWidth = 1080))
        assertEquals(200, AutoTrim.scaleInset(100, imageWidth = 2160, screenWidth = 1080))
        assertEquals(50, AutoTrim.scaleInset(100, imageWidth = 540, screenWidth = 1080))
        assertEquals(0, AutoTrim.scaleInset(0, imageWidth = 1080, screenWidth = 1080))
    }

    @Test
    fun averageColorReflectsTheSampledPixels() {
        val sampler = FakeSampler(100, 100) { _, _ -> gray(120) }
        val color = AutoTrim.averageColor(sampler)
        assertEquals(120, (color shr 16) and 0xFF)
        assertEquals(120, (color shr 8) and 0xFF)
        assertEquals(120, color and 0xFF)
    }
}
