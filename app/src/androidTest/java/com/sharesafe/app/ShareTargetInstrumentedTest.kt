package com.sharesafe.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.sharesafe.app.ui.TestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * ShareSafe registers as a share target. This drives the real entry point: an ACTION_SEND intent
 * carrying a content URI must land in the editor with the scan already running.
 */
@RunWith(AndroidJUnit4::class)
class ShareTargetInstrumentedTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Test
    fun anImageSharedFromAnotherAppOpensInTheEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = seedImage(context)

        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.waitUntil(timeoutMillis = 60_000) {
                composeRule.onAllNodesWithTag(TestTags.EDITOR_STATUS)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            // The editor must actually finish a scan of the shared image. The state is read from the
            // primary action, which stays disabled until the pass is over.
            composeRule.waitUntil(timeoutMillis = 60_000) {
                val node = composeRule.onAllNodesWithTag(TestTags.EDITOR_PREVIEW_SHARE)
                    .fetchSemanticsNodes().firstOrNull() ?: return@waitUntil false
                node.config.getOrNull(SemanticsProperties.Disabled) == null
            }
            assertTrue(
                "the shared image should be redactable without touching the home screen",
                composeRule.onAllNodesWithTag(TestTags.EDITOR_PREVIEW_SHARE)
                    .fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    private fun seedImage(context: Context): Uri {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 48f
        }
        canvas.drawText("Nomor HP: 0812-3456-7890", 60f, 400f, paint)
        canvas.drawText("Email: budi@example.com", 60f, 560f, paint)

        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "sharesafe_share_target_fixture.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Screenshots",
            )
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "could not insert the share fixture" }
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }
}
