package com.sharesafe.app

import com.sharesafe.app.core.model.BeautifyConfig
import com.sharesafe.app.core.model.Detection
import com.sharesafe.app.core.model.DetectionOrigin
import com.sharesafe.app.core.model.IntRect
import com.sharesafe.app.core.model.ManualRegion
import com.sharesafe.app.core.model.NormRect
import com.sharesafe.app.core.model.RedactionRegion
import com.sharesafe.app.core.model.RedactionStyle
import com.sharesafe.app.core.model.SensitiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelGeometryTest {

    @Test
    fun translateClampsInsteadOfEscapingTheImage() {
        val rect = NormRect(0.1f, 0.1f, 0.3f, 0.3f)
        val moved = rect.translated(-0.5f, 0.95f)
        assertEquals(0f, moved.left, 0.0001f)
        assertEquals(0.2f, moved.width, 0.0001f)
        assertEquals(1f, moved.bottom, 0.0001f)
        assertEquals(0.2f, moved.height, 0.0001f)
    }

    @Test
    fun paddingStaysInsideTheImage() {
        val padded = NormRect(0.02f, 0.02f, 0.5f, 0.5f).padded(0.2f, 0.001f)
        assertTrue(padded.left >= 0f && padded.top >= 0f)
        assertTrue(padded.right <= 1f && padded.bottom <= 1f)
    }

    @Test
    fun intersectsAndUnionBehaveAsExpected() {
        val a = NormRect(0f, 0f, 0.5f, 0.5f)
        val b = NormRect(0.4f, 0.4f, 0.9f, 0.9f)
        val c = NormRect(0.6f, 0.6f, 0.9f, 0.9f)
        assertTrue(a.intersects(b))
        assertFalse(a.intersects(c))
        val union = a.union(b)
        assertEquals(0f, union.left, 0.0001f)
        assertEquals(0.9f, union.right, 0.0001f)
    }

    @Test
    fun minSizeKeepsDragsUsable() {
        val tiny = NormRect(0.5f, 0.5f, 0.5001f, 0.5001f).clampWithMinSize(0.05f)
        assertTrue(tiny.width >= 0.04f)
        assertTrue(tiny.height >= 0.04f)
    }

    @Test
    fun intRectValidityChecksBounds() {
        assertTrue(IntRect(0, 0, 100, 200).isValid(100, 200))
        assertFalse(IntRect(-1, 0, 100, 200).isValid(100, 200))
        assertFalse(IntRect(0, 0, 101, 200).isValid(100, 200))
        assertFalse(IntRect(10, 10, 10, 20).isValid(100, 200))
    }

    @Test
    fun redactionRegionsOnlyIncludeActiveDetections() {
        val detections = listOf(
            Detection("phone-1", SensitiveKind.PHONE, NormRect(0f, 0f, 0.1f, 0.1f), "+62", DetectionOrigin.TEXT_PATTERN),
            Detection(
                "email-1",
                SensitiveKind.EMAIL,
                NormRect(0.2f, 0.2f, 0.3f, 0.3f),
                "a@b.co",
                DetectionOrigin.TEXT_PATTERN,
                enabled = false,
            ),
        )
        val manual = listOf(ManualRegion("manual-1", NormRect(0.5f, 0.5f, 0.6f, 0.6f)))
        val regions = RedactionRegion.active(detections, manual)
        assertEquals(2, regions.size)
        assertTrue(regions.all { it.enabled })
        assertTrue(regions.any { it.bounds.left == 0f })
        assertTrue(regions.any { it.bounds.left == 0.5f })
    }

    @Test
    fun styleIdsRoundTripAndFallBackToTheDefault() {
        RedactionStyle.entries.forEach { style ->
            assertEquals(style, RedactionStyle.fromId(style.id))
        }
        assertEquals(RedactionStyle.DEFAULT, RedactionStyle.fromId("nonsense"))
        assertEquals(RedactionStyle.DEFAULT, RedactionStyle.fromId(null))
    }

    @Test
    fun beautifyDefaultsAreWithinSliderRanges() {
        val config = BeautifyConfig()
        assertTrue(config.paddingFraction <= BeautifyConfig.MAX_PADDING)
        assertTrue(config.cornerFraction <= BeautifyConfig.MAX_CORNER)
    }
}
