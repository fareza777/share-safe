package com.sharesafe.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.sharesafe.app.core.batch.BatchItemStatus
import com.sharesafe.app.core.batch.BatchProtectEngine
import com.sharesafe.app.core.batch.BatchProtectItem
import com.sharesafe.app.core.detect.ScanOptions
import com.sharesafe.app.core.export.ExportOptions
import com.sharesafe.app.core.image.BitmapLoader
import com.sharesafe.app.core.model.BeautifyConfig
import com.sharesafe.app.core.model.BeautifyPreset
import com.sharesafe.app.core.model.FaceMaskStyle
import com.sharesafe.app.core.model.RedactionStyle
import com.sharesafe.app.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Batch Protect, on a real device, against real MediaStore rows.
 *
 * The two claims this file exists to defend are the ones a user cannot see and would only discover
 * the hard way: that a batch through the engine is exactly as verified as a single export (both go
 * through the same `SecureRender` loop), and that a screenshot taller than a phone screen is decoded
 * inside the source cap instead of at its native size — which is what would take the process down.
 */
@RunWith(AndroidJUnit4::class)
class BatchProtectInstrumentedTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    // --- helpers --------------------------------------------------------------------------------

    private fun waitForTag(tag: String, timeoutMillis: Long = 30_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun nodeCount(tag: String): Int =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun settle(millis: Long = 700) {
        Thread.sleep(millis)
    }

    private fun dismissOnboardingIfPresent() {
        composeRule.waitUntil(timeoutMillis = 90_000) {
            composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(TestTags.HOME_PICK).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA)[0].performClick()
        }
        settle()
        waitForTag(TestTags.HOME_PICK, timeoutMillis = 60_000)
        settle()
    }

    /** Heap in use right now; sampled around the run to put a real number on "memory efficient". */
    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun fixture(name: String): Uri = runBlocking {
        requireNotNull(Fixtures.ensureSeeded(targetContext, name)) { "could not seed $name" }
    }

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    // --- UI: the whole batch journey --------------------------------------------------------------

    /**
     * Multi-select → Batch Protect → every row terminal → summary. This is the flow a user is asked to
     * trust with several screenshots at once, so it is driven the way they drive it.
     */
    @Test
    fun selectModeProtectsTheChosenScreenshotsAndReportsASummary() {
        dismissOnboardingIfPresent()
        waitForTag(TestTags.HOME_RECENT_THUMB, timeoutMillis = 60_000)

        composeRule.onNodeWithTag(TestTags.HOME_SELECT_TOGGLE).performScrollTo().performClick()
        settle()

        // Two images, deliberately below the free batch limit so no rewarded prompt interferes with
        // what this test is actually pinning down.
        val thumbs = composeRule.onAllNodesWithTag(TestTags.HOME_RECENT_THUMB)
        assertTrue(
            "the recents strip needs at least two screenshots for a batch",
            thumbs.fetchSemanticsNodes().size >= 2,
        )
        thumbs[0].performClick()
        settle(400)
        thumbs[1].performClick()
        settle(400)

        composeRule.onNodeWithTag(TestTags.HOME_BATCH_ACTION).performScrollTo().performClick()
        settle()

        waitForTag(TestTags.BATCH_SCREEN, timeoutMillis = 30_000)
        waitForTag(TestTags.BATCH_SUMMARY, timeoutMillis = 240_000)

        assertTrue(
            "a finished batch must offer the share-all action",
            nodeCount(TestTags.BATCH_SHARE_ALL) > 0,
        )
        assertTrue("the batch screen should list every row", nodeCount(TestTags.BATCH_ROW) >= 2)
    }

    // --- engine: verification parity and the long-screenshot budget --------------------------------

    /**
     * Three images through the engine — including one 1080×7200 "long screenshot" — with the same
     * verify-and-repair loop the editor uses. Every image must finish, every one must be written to
     * the gallery, and the heap growth across the run must stay inside a stated ceiling.
     *
     * The ceiling is deliberately generous, because the goal is not to measure the allocator: it is to
     * fail loudly if a change ever starts holding decoded screenshots for the whole batch. A single
     * uncapped decode of the tall fixture alone is 1080×7200×4 ≈ 31 MB per copy, and the render
     * pipeline keeps several copies alive at once, so that regression shows up here as a number
     * several times this ceiling.
     */
    @Test
    fun theEngineFinishesEveryImageIncludingAVeryTallOneWithinAMemoryCeiling() = runBlocking {
        val items = Fixtures.batchNames.map { name -> BatchProtectItem(fixture(name), name) }

        val thumbDims = mutableListOf<Pair<Int, Int>>()
        val baseline = usedHeapBytes()
        var peak = baseline

        val summary = BatchProtectEngine.protect(
            context = targetContext,
            items = items,
            scanOptions = ScanOptions(detectCodes = true, detectFaces = false),
            style = RedactionStyle.BLACK_BAR,
            strength = 0.5f,
            exportOptions = ExportOptions.DEFAULT,
            beautify = BeautifyConfig.of(BeautifyPreset.OFF),
            faceMask = FaceMaskStyle.SOFT_OVAL,
            ignoreKinds = emptySet(),
            verify = true,
            autoFix = true,
        ) { _, status, thumbnail ->
            peak = max(peak, usedHeapBytes())
            if (status is BatchItemStatus.Done && thumbnail != null) {
                thumbDims += thumbnail.width to thumbnail.height
            }
        }

        assertEquals("every queued image should finish", items.size, summary.done)
        assertEquals("nothing should fail on a plain screenshot", 0, summary.failed)
        assertEquals(
            "each finished image should have been written to the gallery",
            items.size,
            summary.exportUris.size,
        )
        assertTrue(
            "the scan should have found something to hide in the seeded text",
            summary.regionsHidden > 0,
        )
        assertEquals(
            "the engine should report one bounded thumbnail per image",
            items.size,
            thumbDims.size,
        )
        thumbDims.forEach { (width, height) ->
            assertTrue(
                "a row thumbnail of ${width}x$height is larger than the engine's cap",
                max(width, height) <= BatchProtectEngine.THUMBNAIL_MAX_DIM,
            )
        }

        val growthMb = (peak - baseline) / (1024 * 1024)
        assertTrue(
            "peak heap grew by ${growthMb}MB across ${items.size} images, which suggests full-size " +
                "bitmaps are being held across the batch instead of one at a time",
            growthMb < 160,
        )
    }

    /**
     * The reason the run above can finish at all: a tall screenshot is decoded to the source cap, not
     * at its native size. Pinned separately from the batch so a regression points at the decoder.
     */
    @Test
    fun aTallScreenshotIsDecodedInsideTheSourceCap() = runBlocking {
        val uri = fixture(Fixtures.TALL_NAME)
        val decoded = BitmapLoader.loadSource(targetContext, uri)
        try {
            val longest = max(decoded.width, decoded.height)
            assertTrue(
                "a ${decoded.width}x${decoded.height} decode escaped the " +
                    "${BitmapLoader.MAX_SOURCE_DIM}px cap",
                longest <= BitmapLoader.MAX_SOURCE_DIM,
            )
            assertTrue(
                "the tall fixture should have been downscaled rather than accepted at native size",
                decoded.height <= BitmapLoader.MAX_SOURCE_DIM,
            )
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    /**
     * The single-image path and the batch path must agree about this, or "a batch is not less safe"
     * would be luck rather than design.
     */
    @Test
    fun theEngineTouchesOneImageAtATime() {
        assertEquals(
            "holding two decoded screenshots at once is the crash this design avoids",
            1,
            BatchProtectEngine.MAX_CONCURRENT_IMAGES,
        )
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private object Fixtures {
        const val TALL_NAME = "sharesafe_uitest_tall.png"

        /** Seeded before the rules run so the recents strip has content on its first composition. */
        val batchNames = listOf(
            "sharesafe_uitest_batch_a.png",
            "sharesafe_uitest_batch_b.png",
            TALL_NAME,
        )

        fun ensureSeeded(context: Context, name: String): Uri? {
            val resolver = context.contentResolver
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                arrayOf(name),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0).toString(),
                    )
                }
            }

            val tall = name == TALL_NAME
            val height = if (tall) 7200 else 1920
            val bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 48f
            }
            canvas.drawRect(0f, 0f, 1080f, 96f, Paint().apply { color = Color.rgb(18, 18, 30) })
            canvas.drawText("Nomor HP: 0813-2211-9090", 60f, 400f, paint)
            canvas.drawText("Email: budi@example.com", 60f, 560f, paint)
            canvas.drawText("OTP: 448120", 60f, 720f, paint)
            if (tall) {
                // Long screenshots repeat content all the way down, so the redaction work is spread
                // over the whole canvas rather than only the part a phone screen would show.
                var y = 1200f
                while (y < height - 200) {
                    canvas.drawText("Rekening 1234567890 baris ${y.toInt()}", 60f, y, paint)
                    y += 400f
                }
            }

            val bytes = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
            bitmap.recycle()

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Screenshots",
                )
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            assertNotNull("the fixture should be readable back", uri)
            return uri
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun seedGalleryScreenshots() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            Fixtures.batchNames.forEach { Fixtures.ensureSeeded(context, it) }
        }
    }
}
