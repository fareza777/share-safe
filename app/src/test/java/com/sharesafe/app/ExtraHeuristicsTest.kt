package com.sharesafe.app

import com.sharesafe.app.core.detect.CodeFormat
import com.sharesafe.app.core.detect.CodePayloadClassifier
import com.sharesafe.app.core.detect.CodePayloadKind
import com.sharesafe.app.core.detect.SensitivePatterns
import com.sharesafe.app.core.model.SensitiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The categories added on top of the original engine: networks, plates, addresses, IBAN. */
class ExtraHeuristicsTest {

    private fun kinds(text: String): List<SensitiveKind> =
        SensitivePatterns.findMatches(text).map { it.kind }

    @Test
    fun findsIPv4Addresses() {
        val found = kinds("Router: 192.168.1.24, gateway 10.0.0.1")
        assertEquals(listOf(SensitiveKind.NETWORK, SensitiveKind.NETWORK), found)
    }

    @Test
    fun findsMacAddresses() {
        assertEquals(listOf(SensitiveKind.NETWORK), kinds("MAC 3C:5A:B4:11:22:33"))
    }

    @Test
    fun ignoresClockTimesAndDates() {
        assertFalse(kinds("Meeting at 12:30:45 sharp").contains(SensitiveKind.NETWORK))
        assertFalse(kinds("2026:09:20 12:30:45").contains(SensitiveKind.NETWORK))
    }

    @Test
    fun findsLicencePlates() {
        val found = kinds("Kendaraan B 1234 XYZ terparkir")
        assertEquals(listOf(SensitiveKind.PLATE), found)
    }

    @Test
    fun doesNotFlagShortLetterDigitPairs() {
        assertTrue(kinds("R 20 unit").isEmpty())
    }

    @Test
    fun findsKeywordGatedAddresses() {
        val found = kinds("Alamat: Jl. Sudirman No. 12, Jakarta")
        assertEquals(listOf(SensitiveKind.ADDRESS), found)
    }

    @Test
    fun findsIbanAsAnAccountNumber() {
        assertEquals(listOf(SensitiveKind.ACCOUNT_NUMBER), kinds("IBAN GB82 WEST 1234 5698 7654 32"))
    }

    @Test
    fun addressDetectionIsOffByDefaultInTheEditor() {
        assertFalse(SensitiveKind.ADDRESS.defaultEnabled)
        assertTrue(SensitiveKind.NETWORK.defaultEnabled)
        assertTrue(SensitiveKind.PLATE.defaultEnabled)
    }

    @Test
    fun classifiesTwoFactorEnrolmentLinks() {
        val payload = CodePayloadClassifier.classify(
            CodeFormat.OTHER,
            "otpauth://totp/Example:me@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example",
        )
        assertEquals(CodePayloadKind.TWO_FACTOR, payload.kind)
        assertTrue(payload.containsPersonalData)
    }

    @Test
    fun classifiesPaymentAndSocialLinks() {
        assertEquals(
            CodePayloadKind.PAYMENT_LINK,
            CodePayloadClassifier.classify(CodeFormat.URL, "https://paypal.me/someone").kind,
        )
        assertEquals(
            CodePayloadKind.PAYMENT_LINK,
            CodePayloadClassifier.classify(CodeFormat.URL, "upi://pay?pa=someone@upi").kind,
        )
        assertEquals(
            CodePayloadKind.SOCIAL_PROFILE,
            CodePayloadClassifier.classify(CodeFormat.URL, "https://instagram.com/someone").kind,
        )
    }

    @Test
    fun keepsClassifyingQrisAndPlainLinks() {
        assertEquals(
            CodePayloadKind.QRIS_PAYMENT,
            CodePayloadClassifier.classify(
                CodeFormat.OTHER,
                "00020101021126610014COM.GO-JEK.WWW01189360091400000000000210G000000000303UMI",
            ).kind,
        )
        val plain = CodePayloadClassifier.classify(CodeFormat.URL, "https://example.com/promo")
        assertEquals(CodePayloadKind.URL, plain.kind)
        assertFalse(plain.containsPersonalData)
    }
}
