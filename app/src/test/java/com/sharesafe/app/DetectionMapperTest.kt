package com.sharesafe.app

import com.sharesafe.app.core.detect.DetectionMapper
import com.sharesafe.app.core.detect.SensitivePatterns
import com.sharesafe.app.core.detect.TextSpan
import com.sharesafe.app.core.model.NormRect
import com.sharesafe.app.core.model.SensitiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionMapperTest {

    private fun span(text: String, top: Float, bottom: Float): TextSpan = TextSpan(
        text = text,
        bounds = NormRect(0.1f, top, 0.9f, bottom),
    )

    @Test
    fun mapsEachMatchToItsOcrBox() {
        val spans = listOf(
            span("Nomor saya +62 812 3456 7890", 0.10f, 0.14f),
            span("email: budi@example.com", 0.20f, 0.24f),
        )
        val text = spans.joinToString(" ") { it.text }
        val matches = SensitivePatterns.findMatches(text)
        val detections = DetectionMapper.buildDetections(spans, matches)

        assertEquals(2, detections.size)
        assertEquals(listOf(SensitiveKind.PHONE, SensitiveKind.EMAIL), detections.map { it.kind })
        val phone = detections.first { it.kind == SensitiveKind.PHONE }
        assertTrue(phone.bounds.top <= 0.14f)
        assertTrue(phone.bounds.bottom >= 0.10f)
        assertEquals("+62 812 3456 7890", phone.label)
        assertTrue(detections.all { it.id.isNotBlank() })
    }

    @Test
    fun mergesOverlappingBoxesOfTheSameKind() {
        val spans = listOf(
            TextSpan("0812-3456-7890", NormRect(0.1f, 0.10f, 0.9f, 0.14f)),
            TextSpan("0812-3456-7891", NormRect(0.1f, 0.13f, 0.9f, 0.17f)),
        )
        val matches = SensitivePatterns.findMatches(spans.joinToString(" ") { it.text })
        val detections = DetectionMapper.buildDetections(spans, matches)
        assertEquals(1, detections.size)
        assertTrue(detections.first().bounds.bottom >= 0.16f)
    }

    @Test
    fun emptySpansProduceNoDetections() {
        assertTrue(DetectionMapper.buildDetections(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun paddedBoxesStayInsideTheImage() {
        val spans = listOf(TextSpan("budi@example.com", NormRect(0.0f, 0.0f, 1.0f, 1.0f)))
        val matches = SensitivePatterns.findMatches(spans.first().text)
        val detection = DetectionMapper.buildDetections(spans, matches).single()
        assertEquals(0f, detection.bounds.left, 0.0001f)
        assertEquals(1f, detection.bounds.right, 0.0001f)
    }

    @Test
    fun fallsBackToScanningWhenMatchesAreNotSupplied() {
        val spans = listOf(span("NIK 3174010101900001", 0.3f, 0.34f))
        val detections = DetectionMapper.buildDetections(spans, emptyList(), includeLongNumbers = true)
        assertEquals(listOf(SensitiveKind.ID_NUMBER), detections.map { it.kind })
    }
}
