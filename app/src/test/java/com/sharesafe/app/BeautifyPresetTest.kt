package com.sharesafe.app

import com.sharesafe.app.core.model.BackgroundStyle
import com.sharesafe.app.core.model.BeautifyConfig
import com.sharesafe.app.core.model.BeautifyPreset
import com.sharesafe.app.core.model.FaceMaskStyle
import com.sharesafe.app.core.model.RedactionRegion
import com.sharesafe.app.core.model.SensitiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beautify presets are part of the product's promise rather than decoration: the framed version of a
 * redaction is what people actually post, so the values behind each look have to be predictable and
 * the free/premium split has to be exactly the one the Plus dialog describes.
 */
class BeautifyPresetTest {

    @Test
    fun theFreeLooksAreTheOnesAdvertisedAsFree() {
        val premium = BeautifyPreset.entries.filter { it.premium }.map { it.id }.toSet()
        assertEquals(setOf("aurora", "studio"), premium)
        assertFalse(BeautifyPreset.DEFAULT.premium)
    }

    @Test
    fun offReallyMeansNothingAdded() {
        assertEquals(0f, BeautifyPreset.OFF.padding, 0f)
        assertEquals(0f, BeautifyPreset.OFF.corner, 0f)
        assertEquals(0f, BeautifyPreset.OFF.shadow, 0f)
        assertEquals(BackgroundStyle.AUTO, BeautifyPreset.OFF.background)
    }

    @Test
    fun everyLookStaysWithinTheSlidersOwnLimits() {
        // The presets must not set a frame the sliders could never reach, or the user could not
        // take it back by hand.
        BeautifyPreset.entries.forEach { preset ->
            assertTrue(preset.id, preset.padding in 0f..BeautifyConfig.MAX_PADDING)
            assertTrue(preset.id, preset.corner in 0f..BeautifyConfig.MAX_CORNER)
            assertTrue(preset.id, preset.shadow in 0f..1f)
        }
    }

    @Test
    fun choosingALookAlsoTurnsBeautifyOnAndSaysSo() {
        BeautifyPreset.entries.forEach { preset ->
            val config = BeautifyConfig.of(preset)
            if (preset == BeautifyPreset.OFF) {
                assertFalse(config.enabled)
            } else {
                assertTrue(preset.id, config.enabled)
                assertEquals(preset.id, preset.padding, config.paddingFraction, 0f)
                assertEquals(preset.id, preset.corner, config.cornerFraction, 0f)
                assertEquals(preset.id, preset.background, config.background)
                assertEquals(preset.id, preset.shadow, config.shadow, 0f)
            }
        }
    }

    @Test
    fun thePremiumLooksAreTheWidestFrames() {
        val free = BeautifyPreset.entries.filter { !it.premium && it != BeautifyPreset.OFF }
        val premium = BeautifyPreset.entries.filter { it.premium }
        assertTrue(premium.minOf { it.padding } > free.maxOf { it.padding })
        assertTrue(premium.minOf { it.corner } > free.maxOf { it.corner })
    }

    @Test
    fun presetIdsSurviveAStoreRoundTrip() {
        BeautifyPreset.entries.forEach { preset ->
            assertEquals(preset, BeautifyPreset.fromId(preset.id))
        }
        val fallback = BeautifyPreset.fromId(null)
        assertEquals(BeautifyPreset.DEFAULT, fallback)
        assertEquals(BeautifyPreset.DEFAULT, BeautifyPreset.fromId("nonsense"))
    }

    @Test
    fun softOvalIsTheDefaultFaceCoverAndTheAlternativeIsOffered() {
        assertEquals(FaceMaskStyle.SOFT_OVAL, FaceMaskStyle.DEFAULT)
        assertTrue(FaceMaskStyle.BOX in FaceMaskStyle.entries)
        FaceMaskStyle.entries.forEach { style -> assertEquals(style, FaceMaskStyle.fromId(style.id)) }
        assertEquals(FaceMaskStyle.DEFAULT, FaceMaskStyle.fromId(null))
    }

    @Test
    fun avatarsAreTreatedAsFacesSoTheCoverStyleAppliesToThem() {
        assertTrue(SensitiveKind.AVATAR in RedactionRegion.FACE_LIKE_KINDS)
        assertTrue(SensitiveKind.FACE in RedactionRegion.FACE_LIKE_KINDS)
        assertFalse(SensitiveKind.PHONE in RedactionRegion.FACE_LIKE_KINDS)
    }
}
