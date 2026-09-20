package com.sharesafe.app

import com.sharesafe.app.core.detect.ChatHeuristics
import com.sharesafe.app.core.detect.ChatPreset
import com.sharesafe.app.core.detect.TextSpan
import com.sharesafe.app.core.model.Detection
import com.sharesafe.app.core.model.DetectionOrigin
import com.sharesafe.app.core.model.NormRect
import com.sharesafe.app.core.model.SensitiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chat Privacy Mode is geometry plus a threshold, so it is tested against rectangles rather than
 * against a screenshot: the interesting questions are "did the box land on the real text" and "did
 * a face in the middle of the picture get mistaken for a profile picture".
 */
class ChatPrivacyModeTest {

    private fun span(text: String, rect: NormRect) = TextSpan(text = text, bounds = rect)

    /** A WhatsApp header: avatar left, "Budi Santoso" beside it, both just under the status bar. */
    private val headerName = span("Budi Santoso", NormRect(0.19f, 0.045f, 0.55f, 0.075f))
    private val messageBody = span("Halo, besok jadi?", NormRect(0.08f, 0.42f, 0.60f, 0.46f))

    @Test
    fun offPresetFindsNothing() {
        assertEquals(emptyList<Detection>(), ChatHeuristics.nameDetections(listOf(headerName), ChatPreset.OFF))
        assertEquals(emptyList<ChatHeuristics.AvatarHit>(), ChatHeuristics.avatarDetections(emptyList(), ChatPreset.OFF))
        assertFalse(ChatPreset.OFF.isChat)
        assertTrue(ChatPreset.WHATSAPP.isChat)
    }

    @Test
    fun nameDetectionTakesTheHeaderSpanOnly() {
        val found = ChatHeuristics.nameDetections(listOf(headerName, messageBody), ChatPreset.WHATSAPP)
        assertEquals(1, found.size)
        val detection = found.first()
        assertEquals(SensitiveKind.NAME, detection.kind)
        assertEquals(DetectionOrigin.TEXT_PATTERN, detection.origin)
        assertEquals("Budi Santoso", detection.label)
        assertTrue(detection.enabled)
    }

    @Test
    fun nameBoxCoversTheTextItCameFrom() {
        val bounds = ChatHeuristics.nameDetections(listOf(headerName), ChatPreset.WHATSAPP).first().bounds
        assertTrue(bounds.containsRect(headerName.bounds))
        // Padding stays a margin, not a bar across the screen.
        assertTrue(bounds.width < headerName.bounds.width * 1.5f)
    }

    @Test
    fun oneCharacterSpanIsNotAName() {
        // The back chevron and the status icons are single glyphs lost inside the header band.
        val chevron = span("<", NormRect(0.02f, 0.045f, 0.05f, 0.075f))
        assertTrue(ChatHeuristics.nameDetections(listOf(chevron), ChatPreset.WHATSAPP).isEmpty())
    }

    @Test
    fun eachPresetPutsTheNameSomewhereDifferent() {
        val bands = ChatPreset.entries.filter { it.isChat }.map { ChatHeuristics.layoutFor(it).nameBand }
        assertEquals(bands.size, bands.distinctBy { listOf(it.left, it.top, it.right, it.bottom) }.size)
    }

    @Test
    fun dmPresetExpectsTheAvatarOnTheRight() {
        val dm = ChatHeuristics.layoutFor(ChatPreset.DM)
        assertTrue(dm.avatarBand.left > 0.5f)
        val whatsapp = ChatHeuristics.layoutFor(ChatPreset.WHATSAPP)
        assertTrue(whatsapp.avatarBand.right < 0.5f)
    }

    @Test
    fun headerFaceBecomesAnAvatarAndReplacesThePlainFace() {
        val face = NormRect(0.06f, 0.045f, 0.14f, 0.10f)
        val hits = ChatHeuristics.avatarDetections(listOf(face), ChatPreset.WHATSAPP)
        assertEquals(1, hits.size)
        assertEquals(0, hits.first().faceIndex)
        assertEquals(SensitiveKind.AVATAR, hits.first().detection.kind)
        assertEquals(DetectionOrigin.FACE, hits.first().detection.origin)
    }

    @Test
    fun avatarBoxIsBiggerThanTheFaceInsideIt() {
        val face = NormRect(0.06f, 0.045f, 0.14f, 0.10f)
        val box = ChatHeuristics.avatarDetections(listOf(face), ChatPreset.WHATSAPP).first().detection.bounds
        assertTrue(box.containsRect(face))
        assertTrue(box.width > face.width * 1.4f)
        assertTrue(box.left >= 0f && box.right <= 1f)
    }

    @Test
    fun smallFaceOnTheEdgeOfAMessageListCountsAsAnAvatar() {
        val channelIcon = NormRect(0.03f, 0.55f, 0.11f, 0.60f)
        assertEquals(1, ChatHeuristics.avatarDetections(listOf(channelIcon), ChatPreset.TELEGRAM).size)
    }

    @Test
    fun aBigFaceInTheMiddleofThePhotoIsJustAFace() {
        val portrait = NormRect(0.30f, 0.35f, 0.72f, 0.70f)
        val hits = ChatHeuristics.avatarDetections(listOf(portrait), ChatPreset.WHATSAPP)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun aPhoneNumberInTheHeaderIsNotAlsoCalledAName() {
        val candidate = Detection(
            id = "name-1",
            kind = SensitiveKind.NAME,
            bounds = NormRect(0.19f, 0.045f, 0.50f, 0.075f),
            label = "+62 812-3456-7890",
            origin = DetectionOrigin.TEXT_PATTERN,
        )
        val phone = Detection(
            id = "phone-1",
            kind = SensitiveKind.PHONE,
            bounds = NormRect(0.20f, 0.046f, 0.49f, 0.074f),
            label = "+62 812-3456-7890",
            origin = DetectionOrigin.TEXT_PATTERN,
        )
        assertTrue(ChatHeuristics.dropCovered(listOf(candidate), listOf(phone)).isEmpty())
        assertTrue(ChatHeuristics.dropCovered(listOf(candidate), emptyList()).isNotEmpty())
    }

    @Test
    fun recognisesAWhatsappScreenshotWithoutBeingAsked() {
        val avatar = NormRect(0.05f, 0.045f, 0.13f, 0.10f)
        assertEquals(
            ChatPreset.WHATSAPP,
            ChatHeuristics.detectPreset(listOf(headerName, messageBody), listOf(avatar)),
        )
    }

    @Test
    fun recognisesAPresetOnlyWhenNameAndFaceAgree() {
        val avatar = NormRect(0.05f, 0.045f, 0.13f, 0.10f)
        // A header title with no profile picture around it could be any screen with a title.
        assertEquals(null, ChatHeuristics.detectPreset(listOf(headerName), emptyList()))
        // A face near the top with no name beside it is a photo, not a conversation.
        assertEquals(null, ChatHeuristics.detectPreset(emptyList(), listOf(avatar)))
        // Neither of them up there: a body-only screenshot is not a chat header.
        assertEquals(null, ChatHeuristics.detectPreset(listOf(messageBody), listOf(avatar)))
    }

    @Test
    fun atelegramHeaderTallerThanWhatsappWinsTheTie() {
        val tallAvatar = NormRect(0.05f, 0.03f, 0.13f, 0.125f)
        assertEquals(
            ChatPreset.TELEGRAM,
            ChatHeuristics.detectPreset(listOf(headerName), listOf(tallAvatar)),
        )
    }

    @Test
    fun anAvatarOnTheRightMeansADirectMessageLayout() {
        val avatar = NormRect(0.86f, 0.045f, 0.95f, 0.10f)
        assertEquals(
            ChatPreset.DM,
            ChatHeuristics.detectPreset(listOf(headerName), listOf(avatar)),
        )
    }

    @Test
    fun presetIdsSurviveAStoreRoundTrip() {
        ChatPreset.entries.forEach { preset ->
            assertEquals(preset, ChatPreset.fromId(preset.id))
        }
        assertEquals(ChatPreset.OFF, ChatPreset.fromId(null))
        assertEquals(ChatPreset.OFF, ChatPreset.fromId("nonsense"))
    }
}
