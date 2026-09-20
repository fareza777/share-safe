package com.sharesafe.app

import com.sharesafe.app.core.detect.SensitivePatterns
import com.sharesafe.app.core.model.SensitiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitivePatternsTest {

    private fun kindsIn(text: String, includeLongNumbers: Boolean = false): List<SensitiveKind> =
        SensitivePatterns.findMatches(text, includeLongNumbers).map { it.kind }

    @Test
    fun detectsIndonesianMobileNumbers() {
        assertTrue(SensitiveKind.PHONE in kindsIn("Telp: 0812-3456-7890"))
        assertTrue(SensitiveKind.PHONE in kindsIn("WA +62 812 3456 7890 ya"))
        assertTrue(SensitiveKind.PHONE in kindsIn("hubungi 08123456789"))
        assertTrue(SensitiveKind.PHONE in kindsIn("Call +1 (415) 555-0132 now"))
    }

    @Test
    fun ignoresPricesAndDates() {
        assertFalse(SensitiveKind.PHONE in kindsIn("Total: Rp 1.250.000"))
        assertFalse(SensitiveKind.PHONE in kindsIn("Tanggal 12/09/2026 jam 08:30"))
        assertTrue(kindsIn("Total: Rp 1.250.000").isEmpty())
    }

    @Test
    fun detectsEmailAddresses() {
        val matches = SensitivePatterns.findMatches("kirim ke budi.santoso@example.co.id besok")
        assertEquals(listOf(SensitiveKind.EMAIL), matches.map { it.kind })
        assertEquals("budi.santoso@example.co.id", "kirim ke budi.santoso@example.co.id besok".substring(
            matches.first().range.first,
            matches.first().range.last + 1,
        ))
    }

    @Test
    fun detectsPaymentCardsWithLuhn() {
        assertTrue(SensitiveKind.CARD in kindsIn("4111 1111 1111 1111"))
        // 16 digits that fail Luhn are treated as an ID number, not a card.
        assertFalse(SensitiveKind.CARD in kindsIn("1234 5678 9012 3456"))
    }

    @Test
    fun detectsIndonesianIdentityNumbers() {
        assertTrue(SensitiveKind.ID_NUMBER in kindsIn("NIK 3174010101900001"))
        assertTrue(SensitiveKind.ID_NUMBER in kindsIn("NIK: 3174 0101 0190 0001"))
        assertTrue(SensitiveKind.ID_NUMBER in kindsIn("NPWP 09.123.456.7-012.000"))
    }

    @Test
    fun detectsPassportsOnlyWithAKeyword() {
        val matches = SensitivePatterns.findMatches("No. Paspor: A1234567")
        assertEquals(listOf(SensitiveKind.PASSPORT), matches.map { it.kind })
        val text = "No. Paspor: A1234567"
        assertEquals("A1234567", text.substring(matches.first().range.first, matches.first().range.last + 1))
        // The bare shape is far too common to flag on its own.
        assertTrue(kindsIn("Order A1234567 shipped").isEmpty())
    }

    @Test
    fun detectsDatesOfBirthOnlyWithAKeyword() {
        assertTrue(SensitiveKind.DOB in kindsIn("Tanggal lahir: 12/09/1990"))
        assertTrue(SensitiveKind.DOB in kindsIn("Date of birth 4 Jan 1985"))
        // A plain date without the keyword stays visible.
        assertFalse(SensitiveKind.DOB in kindsIn("Jadwal 12/09/2026 jam 08:30"))
    }

    @Test
    fun detectsMapCoordinates() {
        assertTrue(SensitiveKind.LOCATION in kindsIn("Lokasi: -6.2088, 106.8456"))
        assertTrue(SensitiveKind.LOCATION in kindsIn("-7.2575,112.7521"))
        // Prices and version numbers must not look like coordinates.
        assertFalse(SensitiveKind.LOCATION in kindsIn("Total Rp 1.250.000"))
        assertFalse(SensitiveKind.LOCATION in kindsIn("versi 1.2, 3.4"))
    }

    @Test
    fun detectsBankAccountsWithoutFlaggingThemAsCards() {
        val kinds = kindsIn("Rekening BCA 1234567890 a/n Budi")
        assertEquals(listOf(SensitiveKind.ACCOUNT_NUMBER), kinds)
    }

    @Test
    fun detectsVerificationCodesByContext() {
        val matches = SensitivePatterns.findMatches("Kode OTP Anda adalah 483920. Jangan bagikan.")
        assertEquals(listOf(SensitiveKind.OTP), matches.map { it.kind })
        val text = "Kode OTP Anda adalah 483920. Jangan bagikan."
        assertEquals("483920", text.substring(matches.first().range.first, matches.first().range.last + 1))
    }

    @Test
    fun detectsSecretsAndTokens() {
        assertTrue(SensitiveKind.SECRET in kindsIn("password: hunter2Hunt"))
        assertTrue(SensitiveKind.SECRET in kindsIn("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abcdefgh.klmnopqr"))
        assertTrue(SensitiveKind.SECRET in kindsIn("key = sk_live_abcdefghijklmnop1234"))
        assertFalse(SensitiveKind.SECRET in kindsIn("password: ********"))
    }

    @Test
    fun longNumbersAreOptIn() {
        // 9 digits matches nothing by default: tracking numbers are not sensitive data.
        assertTrue(kindsIn("Resi 123456789").isEmpty())
        assertTrue(SensitiveKind.LONG_NUMBER in kindsIn("Resi 123456789", includeLongNumbers = true))
        // 10-15 digit runs stay classified as account numbers, which is the useful default.
        assertEquals(listOf(SensitiveKind.ACCOUNT_NUMBER), kindsIn("Resi 1234567890123"))
    }

    @Test
    fun luhnCheckMatchesReferenceImplementation() {
        assertTrue(SensitivePatterns.passesLuhn("4111111111111111"))
        assertTrue(SensitivePatterns.passesLuhn("5500005555555559"))
        assertFalse(SensitivePatterns.passesLuhn("4111111111111112"))
        assertFalse(SensitivePatterns.passesLuhn("12"))
    }

    @Test
    fun overlappingMatchesPreferTheMostSpecificKind() {
        // "0812345678" matches both the account and phone heuristics; phone must win.
        val kinds = kindsIn("0812345678")
        assertEquals(listOf(SensitiveKind.PHONE), kinds)
    }

    @Test
    fun blankAndHugeInputsAreSafe() {
        assertTrue(SensitivePatterns.findMatches("   ").isEmpty())
        val huge = "9".repeat(SensitivePatterns.MAX_SCAN_LENGTH + 1)
        assertTrue(SensitivePatterns.findMatches(huge).isEmpty())
    }
}
