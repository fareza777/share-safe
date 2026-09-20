package com.sharesafe.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.sharesafe.app.ui.TestTags
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * The headline promise, on a real device: one tap on the newest screenshot must produce a verified,
 * repaired export, and the result must show up in the calendar history.
 */
@RunWith(AndroidJUnit4::class)
class AutoHistoryUiTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForTag(tag: String, timeoutMillis: Long = 90_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dismissOnboardingIfPresent() {
        composeRule.waitUntil(timeoutMillis = 90_000) {
            composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(TestTags.HOME_PICK).fetchSemanticsNodes().isNotEmpty()
        }
        val cta = composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA).fetchSemanticsNodes()
        if (cta.isNotEmpty()) {
            composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA)[0].performClick()
        }
        settle()
        waitForTag(TestTags.HOME_PICK)
        settle()
    }

    /** Lets a screen transition finish before the next interaction. */
    private fun settle(millis: Long = 700) {
        Thread.sleep(millis)
    }

    @Test
    fun oneTapProducesAVerifiedExportAndRecordsItInHistory() {
        dismissOnboardingIfPresent()

        // 1. One tap on the newest screenshot runs scan → redact → verify → repair. The newest
        //    thumbnail is the same entry point the big card uses; there is only one journey now.
        waitForTag(TestTags.HOME_RECENT_THUMB, timeoutMillis = 60_000)
        composeRule.onAllNodesWithTag(TestTags.HOME_RECENT_THUMB)[0].performClick()
        settle()
        waitForTag(TestTags.PREVIEW_VERIFY_BANNER, timeoutMillis = 120_000)

        // 2. The verdict must be a real one, not a spinner: either a clean pass or a warning.
        composeRule.waitUntil(timeoutMillis = 180_000) {
            composeRule.onAllNodesWithText("Verified clean", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Terverifikasi bersih", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Careful", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Hati-hati", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Saving records the export, and Done returns home.
        composeRule.onNodeWithTag(TestTags.PREVIEW_SAVE).performScrollTo().performClick()
        // Give the encode time to finish before navigating away: the save owns the bitmap until it
        // is written, and the app now serialises that with a mutex rather than racing it.
        settle(2_500)
        val done = composeRule.onAllNodesWithText("Done").fetchSemanticsNodes()
        if (done.isNotEmpty()) {
            composeRule.onAllNodesWithText("Done")[0].performClick()
        } else {
            composeRule.onAllNodesWithText("Selesai")[0].performClick()
        }
        settle()

        // 4. History now has the entry, with its own calendar and stats.
        waitForTag(TestTags.HOME_HISTORY_ENTRY, timeoutMillis = 60_000)
        composeRule.onNodeWithTag(TestTags.HOME_HISTORY_ENTRY).performClick()
        settle()
        waitForTag(TestTags.HISTORY_SCREEN, timeoutMillis = 30_000)
        waitForTag(TestTags.HISTORY_ITEM, timeoutMillis = 60_000)
        assertTrue(
            "the saved export should appear in history",
            composeRule.onAllNodesWithTag(TestTags.HISTORY_ITEM).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    companion object {
        private const val FIXTURE_NAME = "sharesafe_auto_uitest.png"

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
                textSize = 46f
            }
            canvas.drawRect(0f, 0f, 1080f, 90f, Paint().apply { color = Color.rgb(18, 18, 30) })
            canvas.drawText("NIK: 3174091205940007", 60f, 380f, paint)
            canvas.drawText("Nomor HP: 0812-3456-7890", 60f, 520f, paint)
            canvas.drawText("Email: budi@example.com", 60f, 660f, paint)
            canvas.drawText("Kode OTP: 882391", 60f, 800f, paint)
            canvas.drawText("Alamat: Jl. Sudirman No. 12", 60f, 940f, paint)
            canvas.drawText("IPv4: 192.168.1.24", 60f, 1080f, paint)

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
            ) { "could not insert the auto-flow fixture" }
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        }
    }
}

/**
 * Settings must survive everything a user can do to it: switching palettes, expanding cards, and
 * changing the language — which tears the Activity down and rebuilds its configuration.
 */
@RunWith(AndroidJUnit4::class)
class ThemeSwitchInstrumentedTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun openSettings() {
        composeRule.waitUntil(timeoutMillis = 90_000) {
            composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(TestTags.HOME_SETTINGS_ENTRY)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val cta = composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA).fetchSemanticsNodes()
        if (cta.isNotEmpty()) {
            composeRule.onAllNodesWithTag(TestTags.ONBOARDING_CTA)[0].performClick()
        }

        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithTag(TestTags.HOME_SETTINGS_ENTRY).fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(700)
        composeRule.onNodeWithTag(TestTags.HOME_SETTINGS_ENTRY).performClick()
        Thread.sleep(700)
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag(TestTags.SETTINGS_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun palettesAndAboutSurviveTheCardLayout() {
        openSettings()

        // Appearance is the one card that opens by default, so its palette is on screen already.
        listOf("ocean", "sunset", "forest", "mono", "violet").forEach { palette ->
            composeRule.onNodeWithTag("${TestTags.SETTINGS_PALETTE}-$palette").performClick()
            composeRule.waitForIdle()
            assertTrue(
                "settings should survive switching to the $palette palette",
                composeRule.onAllNodesWithTag(TestTags.SETTINGS_SCREEN).fetchSemanticsNodes().isNotEmpty(),
            )
        }

        // Cards are closed by default, so the About entry has to be opened the way a user would.
        composeRule.onNodeWithTag(TestTags.settingsSection("about")).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.SETTINGS_ABOUT_ENTRY).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag(TestTags.ABOUT_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "about should expose the share and rate actions",
            composeRule.onAllNodesWithTag(TestTags.ABOUT_SHARE).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag(TestTags.ABOUT_RATE).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * English is the shipped default, and choosing Indonesian must actually re-apply the locale
     * rather than needing a restart. The assertion is on Indonesian copy, which only appears if the
     * Activity was rebuilt with the new configuration.
     */
    @Test
    fun switchingLanguageRelabelsTheAppImmediately() {
        openSettings()

        composeRule.onNodeWithTag(TestTags.SETTINGS_LANGUAGE).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Bahasa Indonesia")[0].performClick()

        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText("Pengaturan").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the settings screen should still be the top screen in the new language",
            composeRule.onAllNodesWithTag(TestTags.SETTINGS_SCREEN).fetchSemanticsNodes().isNotEmpty(),
        )

        // And back, so the rest of the suite (and the user) is left in the default language.
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithTag(TestTags.SETTINGS_LANGUAGE).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.SETTINGS_LANGUAGE).performScrollTo()
        composeRule.onAllNodesWithText("English")[0].performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
