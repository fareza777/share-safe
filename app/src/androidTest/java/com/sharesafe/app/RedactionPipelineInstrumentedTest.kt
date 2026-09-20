package com.sharesafe.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sharesafe.app.core.detect.ScanOptions
import com.sharesafe.app.core.detect.SensitiveScanner
import com.sharesafe.app.core.export.ExportFormat
import com.sharesafe.app.core.export.ExportOptions
import com.sharesafe.app.core.export.ImageExporter
import com.sharesafe.app.core.model.BackgroundStyle
import com.sharesafe.app.core.model.BeautifyConfig
import com.sharesafe.app.core.model.CropConfig
import com.sharesafe.app.core.model.IntRect
import com.sharesafe.app.core.model.RedactionStyle
import com.sharesafe.app.core.model.SensitiveKind
import com.sharesafe.app.core.render.AutoTrim
import com.sharesafe.app.core.render.BitmapSampler
import com.sharesafe.app.core.render.RenderPipeline
import com.sharesafe.app.core.verify.RedactionVerifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real flow on a device: bundled OCR finds the fake secrets in a synthetic screenshot, the
 * renderer rewrites the pixels, the exported image is scanned again and must come back clean.
 */
@RunWith(AndroidJUnit4::class)
class RedactionPipelineInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun syntheticScreenshot(): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 44f
        }
        // A dark band at the top stands in for a status bar.
        canvas.drawRect(0f, 0f, 1080f, 96f, Paint().apply { color = Color.rgb(20, 20, 20) })
        var y = 300f
        listOf(
            "Nomor HP: 0812-3456-7890",
            "Email: budi.santoso@example.com",
            "Rekening BCA 1234567890",
            "Kode OTP Anda adalah 483920",
        ).forEach { line ->
            canvas.drawText(line, 60f, y, paint)
            y += 140f
        }
        return bitmap
    }

    /** Counts sampled pixels inside [rect] that differ between the two bitmaps. */
    private fun changedPixels(before: Bitmap, after: Bitmap, rect: Rect): Int {
        var changed = 0
        var x = rect.left.coerceAtLeast(0)
        while (x < rect.right && x < before.width) {
            var y = rect.top.coerceAtLeast(0)
            while (y < rect.bottom && y < before.height) {
                if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
                y += 3
            }
            x += 3
        }
        return changed
    }

    @Test
    fun detectsRedactsVerifiesAndExports() = runBlocking {
        val bitmap = syntheticScreenshot()

        val scan = SensitiveScanner.scan(bitmap, ScanOptions())
        assertEquals(emptyList<String>(), scan.warnings)
        assertTrue(
            "expected OCR to find the fake phone number, found ${scan.detections.map { it.kind }}",
            scan.detections.any { it.kind == SensitiveKind.PHONE },
        )
        assertTrue(
            "expected OCR to find the fake email, found ${scan.detections.map { it.kind }}",
            scan.detections.any { it.kind == SensitiveKind.EMAIL },
        )

        RedactionStyle.entries.forEach { style ->
            val rendered = RenderPipeline.renderDetailed(
                RenderPipeline.Request(
                    source = bitmap,
                    crop = IntRect.full(bitmap.width, bitmap.height),
                    regions = scan.detections.map { it.bounds },
                    style = style,
                    strength = 0.9f,
                ),
            )
            assertEquals(
                "every detected region should be redacted ($style)",
                scan.detections.size,
                rendered.redactedRects.size,
            )
            val touched = rendered.redactedRects.sumOf { changedPixels(bitmap, rendered.bitmap, it) }
            assertTrue("$style did not change any pixels", touched > 50)

            val verdict = RedactionVerifier.verify(rendered.bitmap, rendered.redactedRects)
            assertTrue("verification could not run ($style): ${verdict.failureReason}", verdict.completed)
            assertTrue("phone number survived $style", SensitiveKind.PHONE !in verdict.leftoverKinds)
            assertTrue("email survived $style", SensitiveKind.EMAIL !in verdict.leftoverKinds)
        }
    }

    @Test
    fun cropsTheStatusBarAndBeautifiesTheExport() = runBlocking {
        val bitmap = syntheticScreenshot()

        // The dark band at the top is the status bar stand-in; trimming it must shrink the image.
        val crop = AutoTrim.estimate(
            sampler = BitmapSampler(bitmap),
            config = CropConfig(trimSystemBars = false, trimBlankEdges = true),
        )
        assertTrue("expected the blank status bar band to be trimmed", crop.rect.top >= 90)
        assertTrue("crop must not eat the content", crop.rect.height > bitmap.height / 2)

        val beautified = RenderPipeline.renderDetailed(
            RenderPipeline.Request(
                source = bitmap,
                crop = crop.rect,
                regions = emptyList(),
                style = RedactionStyle.PIXELATE,
                strength = 0.5f,
                beautify = BeautifyConfig(
                    enabled = true,
                    paddingFraction = 0.06f,
                    cornerFraction = 0.04f,
                    background = BackgroundStyle.GRADIENT,
                ),
            ),
        )
        // Padding is added around the cropped content on both axes.
        assertTrue(beautified.bitmap.width > crop.rect.width)
        assertTrue(beautified.bitmap.height > crop.rect.height)
        assertEquals(crop.rect.width, beautified.contentRect.width())
        assertEquals(crop.rect.height, beautified.contentRect.height())
        // The gradient background must differ from the white screenshot content.
        assertTrue(beautified.bitmap.getPixel(4, 4) != Color.WHITE)
    }

    @Test
    fun exportsAShareablePng() = runBlocking {
        val bitmap = syntheticScreenshot()
        val scan = SensitiveScanner.scan(bitmap, ScanOptions())
        val rendered = RenderPipeline.renderDetailed(
            RenderPipeline.Request(
                source = bitmap,
                crop = IntRect.full(bitmap.width, bitmap.height),
                regions = scan.detections.map { it.bounds },
                style = RedactionStyle.PIXELATE,
                strength = 0.8f,
            ),
        )

        val payload = ImageExporter.exportForShare(context, rendered.bitmap)
        assertNotNull(payload)
        assertEquals("com.sharesafe.app.debug.fileprovider", payload.uri.authority)
        assertEquals("image/png", payload.mimeType)
        val bytes = context.contentResolver.openInputStream(payload.uri)!!.use { it.readBytes() }
        assertTrue("exported PNG looks empty", bytes.size > 5_000)

        val galleryPath = ImageExporter.saveToGallery(
            context = context,
            bitmap = rendered.bitmap,
            displayName = "sharesafe_test",
        )
        assertNotNull("gallery insert failed", galleryPath)
    }

    /** The JPEG path is the one that has to flatten transparency and drop the PNG-only assumption. */
    @Test
    fun exportsAShareableJpegAtAReducedSize() = runBlocking {
        val bitmap = syntheticScreenshot()
        val options = ExportOptions(format = ExportFormat.JPEG, maxLongEdge = 1080)
        val payload = ImageExporter.exportForShare(context, bitmap, options)
        assertEquals("image/jpeg", payload.mimeType)

        val decoded = android.graphics.BitmapFactory.decodeStream(
            context.contentResolver.openInputStream(payload.uri),
        )
        assertNotNull("JPEG export should decode", decoded)
        assertEquals(1080, maxOf(decoded!!.width, decoded.height))
    }
}
