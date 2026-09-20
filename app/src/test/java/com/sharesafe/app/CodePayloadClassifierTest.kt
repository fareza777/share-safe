package com.sharesafe.app

import com.sharesafe.app.core.detect.CodeFormat
import com.sharesafe.app.core.detect.CodePayloadClassifier
import com.sharesafe.app.core.detect.CodePayloadKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodePayloadClassifierTest {

    private fun classify(format: CodeFormat, raw: String?) =
        CodePayloadClassifier.classify(format, raw)

    @Test
    fun recognizesQrisPaymentPayloads() {
        // A real QRIS payload always opens with the EMVCo tag.
        val payload = classify(
            CodeFormat.OTHER,
            "00020101021226610014ID.CO.QRIS.WWW01189360000915000000000215",
        )
        assertEquals(CodePayloadKind.QRIS_PAYMENT, payload.kind)
        assertTrue("QRIS is a payment request, always worth hiding", payload.containsPersonalData)
        assertEquals("QRIS payment", CodePayloadClassifier.label(payload))
    }

    @Test
    fun recognizesWifiCredentials() {
        val payload = classify(CodeFormat.OTHER, "WIFI:T:WPA;S:Rumah-5G;P:rahasia123;;")
        assertEquals(CodePayloadKind.WIFI, payload.kind)
        assertTrue(payload.containsPersonalData)
        assertEquals("WiFi access", CodePayloadClassifier.label(payload))
    }

    @Test
    fun recognizesContactCardsAndEmailLinks() {
        assertEquals(
            CodePayloadKind.CONTACT,
            classify(CodeFormat.CONTACT, "MECARD:N:Budi;TEL:08123456789;;").kind,
        )
        assertEquals(CodePayloadKind.EMAIL, classify(CodeFormat.OTHER, "mailto:budi@example.com").kind)
        assertEquals(CodePayloadKind.PHONE, classify(CodeFormat.PHONE, "tel:+6281234567890").kind)
        assertEquals(CodePayloadKind.SMS, classify(CodeFormat.OTHER, "smsto:08123456789:hallo").kind)
        assertEquals(CodePayloadKind.LOCATION, classify(CodeFormat.OTHER, "geo:-6.2088,106.8456").kind)
    }

    @Test
    fun plainLinksStayNonPersonal() {
        val payload = classify(CodeFormat.URL, "https://example.com/promo")
        assertEquals(CodePayloadKind.URL, payload.kind)
        assertFalse(payload.containsPersonalData)
        assertEquals("Link", CodePayloadClassifier.label(payload))
    }

    @Test
    fun linksCarryingPersonalDataAreFlagged() {
        val payload = classify(CodeFormat.URL, "https://t.co/abc?phone=081234567890")
        assertEquals(CodePayloadKind.URL, payload.kind)
        assertTrue(payload.containsPersonalData)
        assertEquals("Link with personal data", CodePayloadClassifier.label(payload))
    }

    @Test
    fun unknownAndMissingPayloadsAreSafe() {
        assertEquals(CodePayloadKind.TEXT, classify(CodeFormat.OTHER, null).kind)
        assertEquals(CodePayloadKind.TEXT, classify(CodeFormat.OTHER, "   ").kind)
        assertFalse(classify(CodeFormat.OTHER, null).containsPersonalData)
        assertEquals(CodePayloadKind.TEXT, classify(CodeFormat.OTHER, "ORDER-123").kind)
    }
}
