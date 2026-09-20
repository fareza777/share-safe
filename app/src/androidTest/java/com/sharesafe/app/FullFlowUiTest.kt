package com.sharesafe.app

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.sharesafe.app.ui.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * The whole product flow on a real device: the screenshot sitting in the gallery is protected from
 * the recents strip with no further choices, the preview reports its verdict, and the manual editor
 * is still exactly one tap away for the cases that need it.
 */
@RunWith(AndroidJUnit4::class)
class FullFlowUiTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForTag(tag: String, timeoutMillis: Long = 30_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun nodeCount(tag: String): Int =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    /**
     * A fresh install shows the onboarding sheet, which covers the home screen. Dismiss it the way
     * a user would before driving the flow.
     */
    private fun dismissOnboardingIfPresent() {
        // A cold start shows the splash and then, on a fresh install, the full-screen onboarding.
        // Either way the home screen is one tap away, and the skip button carries a stable tag.
        composeRule.waitUntil(timeoutMillis = 90_000) {
            composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(TestTags.HOME_PICK).fetchSemanticsNodes().isNotEmpty()
        }
        val cta = composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA).fetchSemanticsNodes()
        if (cta.isNotEmpty()) {
            composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA)[0].performClick()
        }
        // The home screen is only *guaranteed* to be the top-most screen once the shared-element
        // free transition has finished; clicking through an outgoing screen swallows the tap.
        settle()
        waitForTag(TestTags.HOME_PICK, timeoutMillis = 60_000)
        settle()
    }

    /** Lets a screen transition finish before the next interaction. */
    private fun settle(millis: Long = 700) {
        Thread.sleep(millis)
    }

    /**
     * Waits for a node to stop being disabled. Text matching is not good enough here: any wording
     * change in a locale could make a wait succeed before the work it waits for is actually done.
     */
    private fun waitForEnabled(tag: String, timeoutMillis: Long = 60_000) {
        composeRule.waitUntil(timeoutMillis) {
            val node = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()
                ?: return@waitUntil false
            node.config.getOrNull(SemanticsProperties.Disabled) == null
        }
    }

    /**
     * The home screen used to offer "pick a screenshot" and "do it for me" as two equally loud
     * cards that led to the same place. There is now one, and this pins that down.
     */
    @Test
    fun homeOffersExactlyOnePrimaryAction() {
        dismissOnboardingIfPresent()
        assertEquals(
            "home should present a single primary action",
            1,
            nodeCount(TestTags.HOME_PICK),
        )
        assertTrue(
            "the banner slot should be present on the home screen",
            nodeCount(TestTags.AD_BANNER) > 0,
        )
    }

    /**
     * Ads are allowed on chrome screens only. The two screens that render the user's own pixels must
     * stay clean, and this is the assertion that makes that a rule instead of an intention.
     */
    @Test
    fun theImageScreensCarryNoAds() {
        dismissOnboardingIfPresent()
        waitForTag(TestTags.HOME_RECENT_THUMB, timeoutMillis = 30_000)
        composeRule.onAllNodesWithTag(TestTags.HOME_RECENT_THUMB)[0].performClick()
        settle()
        waitForTag(TestTags.PREVIEW_VERIFY_BANNER, timeoutMillis = 120_000)
        assertEquals(
            "the preview must never show a banner",
            0,
            nodeCount(TestTags.AD_BANNER),
        )

        composeRule.onNodeWithTag(TestTags.PREVIEW_BACK_TO_EDIT).performScrollTo().performClick()
        settle()
        waitForTag(TestTags.EDITOR_STATUS, timeoutMillis = 30_000)
        assertEquals(
            "the editor must never show a banner",
            0,
            nodeCount(TestTags.AD_BANNER),
        )
    }

    @Test
    fun settingsScreenOpensAndChangesADetectionDefault() {
        dismissOnboardingIfPresent()
        waitForTag(TestTags.HOME_SETTINGS_ENTRY)
        composeRule.onNodeWithTag(TestTags.HOME_SETTINGS_ENTRY).performClick()
        waitForTag(TestTags.SETTINGS_SCREEN, timeoutMillis = 15_000)

        // Settings is a stack of closed cards now, so the detection switches are behind their
        // header. Opening it is part of the user journey, not a test workaround.
        composeRule.onNodeWithTag(TestTags.settingsSection("detection")).performScrollTo().performClick()
        composeRule.waitForIdle()

        // Locale independent: the row title always contains "number" in both shipped languages.
        val row = composeRule.onAllNodesWithText("number", substring = true).fetchSemanticsNodes()
        assertTrue("settings should expose the long-number default", row.isNotEmpty())
        composeRule.onAllNodesWithText("number", substring = true)[0].performClick()
        composeRule.waitForIdle()
        assertTrue(
            "settings screen should stay open after a toggle",
            composeRule.onAllNodesWithTag(TestTags.SETTINGS_SCREEN).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun tappingAThumbnailProtectsItAndTheEditorStaysReachable() {
        dismissOnboardingIfPresent()

        // 1. One tap on the newest screenshot runs scan, redaction, verification and repair.
        waitForTag(TestTags.HOME_RECENT_THUMB, timeoutMillis = 30_000)
        composeRule.onAllNodesWithTag(TestTags.HOME_RECENT_THUMB)[0].performClick()
        settle()
        waitForTag(TestTags.PREVIEW_VERIFY_BANNER, timeoutMillis = 120_000)
        assertTrue(
            "preview screen should offer Safe Share",
            composeRule.onAllNodesWithTag(TestTags.PREVIEW_SAFE_SHARE).fetchSemanticsNodes().isNotEmpty(),
        )

        // 2. The manual editor is one tap behind the preview, and it arrives already redacted.
        composeRule.onNodeWithTag(TestTags.PREVIEW_BACK_TO_EDIT).performScrollTo().performClick()
        settle()
        waitForTag(TestTags.EDITOR_STATUS, timeoutMillis = 30_000)
        waitForEnabled(TestTags.EDITOR_PREVIEW_SHARE, timeoutMillis = 90_000)
        assertTrue(
            "the editor should have redacted something automatically",
            composeRule.onAllNodesWithText("redacted", substring = true).fetchSemanticsNodes()
                .isNotEmpty() ||
                composeRule.onAllNodesWithText("disensor", substring = true).fetchSemanticsNodes()
                    .isNotEmpty(),
        )

        // 3. Continue to the export preview and wait for the verification banner.
        settle()
        composeRule.onNodeWithTag(TestTags.EDITOR_PREVIEW_SHARE).performClick()
        settle()
        waitForTag(TestTags.PREVIEW_VERIFY_BANNER, timeoutMillis = 120_000)

        // 4. Saving to the gallery must not crash and must keep the UI alive.
        composeRule.onNodeWithTag(TestTags.PREVIEW_SAVE).performScrollTo().performClick()
        settle()
        composeRule.waitForIdle()
        assertTrue(
            "app should stay on the preview screen after saving",
            composeRule.onAllNodesWithTag(TestTags.PREVIEW_SAVE).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    companion object {
        /**
         * Seeds a screenshot into MediaStore before the activity rule launches the app, so the
         * recents strip has content on its first composition.
         */
        @JvmStatic
        @BeforeClass
        fun seedGalleryScreenshot() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val resolver = context.contentResolver
            val alreadySeeded = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                arrayOf(FIXTURE_NAME),
                null,
            )?.use { cursor -> cursor.moveToFirst() } ?: false
            if (alreadySeeded) return

            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 48f
            }
            canvas.drawRect(0f, 0f, 1080f, 96f, Paint().apply { color = Color.rgb(18, 18, 30) })
            canvas.drawText("Nomor HP: 0812-3456-7890", 60f, 400f, paint)
            canvas.drawText("Email: siti@example.com", 60f, 560f, paint)
            canvas.drawText("OTP: 771245", 60f, 720f, paint)

            val bytes = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, FIXTURE_NAME)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Screenshots",
                )
            }
            val uri = requireNotNull(
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
            ) { "could not insert the fixture screenshot" }
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        }

        private const val FIXTURE_NAME = "sharesafe_uitest_screenshot.png"
    }
}
