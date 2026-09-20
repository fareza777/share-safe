package com.sharesafe.app.core.detect

import com.sharesafe.app.core.model.SensitiveKind

/** A regex hit over OCR text, expressed as a character range into the joined text. */
data class SensitiveMatch(
    val kind: SensitiveKind,
    val range: IntRange,
)

/**
 * Pure, offline heuristics for the data that actually leaks from phone screenshots:
 * Indonesian and international phone numbers, emails, payment cards, KTP/NIK, NPWP and passport
 * numbers, bank accounts (IBAN included), one-time codes, dates of birth, map coordinates,
 * network addresses, licence plates, API tokens and long digit runs.
 *
 * Every pattern is deliberately precision-first: a false box in the editor costs the user a tap,
 * and a missed one costs them their data, so loose shapes (bare dates, bare plates) are gated on a
 * keyword and the noisiest category of all — street addresses — starts switched off.
 *
 * Everything here is plain Kotlin so it can be unit tested on the JVM without Android.
 */
object SensitivePatterns {

    /** Guard against pathological OCR output causing super-linear regex work. */
    const val MAX_SCAN_LENGTH = 200_000

    private val emailRegex = Regex(
        "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
        RegexOption.IGNORE_CASE,
    )

    /** +62 812-3456-7890, 0812 3456 7890, (021) 555 1234, +1 (415) 555-0132. */
    private val phoneRegex = Regex(
        "(?<![\\d])(?:\\+\\d{1,3}[\\s.-]?)?(?:\\(\\d{2,4}\\)|\\d{2,4})[\\s.-]\\d{3,4}[\\s.-]\\d{3,5}(?!\\d)",
    )

    /** Indonesian mobile numbers are often written without separators: 08123456789, +6281234567890. */
    private val compactPhoneRegex = Regex(
        "(?<![\\d])(?:\\+62|62|0)8\\d{7,11}(?![\\d])",
    )

    private val cardCandidateRegex = Regex("(?<![\\d])(?:\\d[ -]?){13,19}(?!\\d)")

    /** KTP / NIK: exactly 16 digits, optionally grouped as 1234 5678 9012 3456. */
    private val nikRegex = Regex("(?<![\\d])(?:\\d{4}[\\s-]){3}\\d{4}(?![\\d])")
    private val nikCompactRegex = Regex("(?<![\\d])\\d{16}(?![\\d])")

    /** NPWP: 09.123.456.7-012.000 and the newer 16-digit form. */
    private val npwpRegex = Regex("(?<![\\d])\\d{2}\\.\\d{3}\\.\\d{3}\\.\\d[-.]\\d{3}\\.\\d{3}(?![\\d])")

    /**
     * Passport numbers are only one letter plus seven digits, so the keyword is mandatory — the
     * raw shape alone would match half of the order ids in a screenshot.
     */
    private val passportContextRegex = Regex(
        "(?i)\\b(?:passport|paspor)\\b[^\\n]{0,16}?\\b([A-Za-z]\\d{7})\\b",
    )

    /** Date of birth, again keyword-gated so plain dates stay untouched. */
    private val dobContextRegex = Regex(
        "(?i)\\b(?:tanggal lahir|tgl\\.?\\s*lahir|lahir|date of birth|d\\.?o\\.?b\\.?|birthday)\\b" +
            "[^\\n]{0,24}?(\\d{1,2}\\s*[-/.\\s]\\s*(?:\\d{1,2}|[A-Za-z]{3,9})\\s*[-/.\\s]\\s*\\d{2,4})",
    )

    /**
     * Map coordinates with enough decimals to be a real location. Screenshots of rides, delivery
     * apps and chat location bubbles carry these, and they reveal a home address.
     */
    private val coordinateRegex = Regex(
        "(?<![\\d.])-?\\d{1,3}\\.\\d{4,},\\s*-?\\d{1,3}\\.\\d{4,}(?![\\d.])",
    )

    /** Bank accounts and virtual accounts: 10-15 digits, usually grouped in 3-5 digit chunks. */
    private val accountRegex = Regex("(?<![\\d])(?:\\d{3,5}[\\s-]){2,3}\\d{3,5}(?![\\d])")
    private val accountCompactRegex = Regex("(?<![\\d])\\d{10,15}(?![\\d])")

    /** OTP / PIN / verification codes: a short digit run announced by a keyword. */
    private val otpContextRegex = Regex(
        "(?i)\\b(otp|kode|sandi|pin|password|passcode|code|kode verifikasi|verification|verifikasi|"
            + "token|kode otp|sandi otp|kode rahasia|one[- ]time)\\b" +
            "[^\\d\\n]{0,18}(\\d{4,8})\\b",
    )

    private val apiKeyRegexes = listOf(
        Regex("\\bAKIA[0-9A-Z]{16}\\b"),
        Regex("\\bAIza[0-9A-Za-z_\\-]{35}\\b"),
        Regex("\\bgh[pousr]_[A-Za-z0-9]{36,255}\\b"),
        Regex("\\bgithub_pat_[A-Za-z0-9_]{22,255}\\b"),
        Regex("\\bxox[baprs]-[A-Za-z0-9\\-]{20,255}\\b"),
        Regex("\\bsk_(?:live|test)_[A-Za-z0-9]{16,255}\\b"),
        Regex("\\beyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\b"),
        Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        Regex(
            "\\b(?:postgres(?:ql)?|mysql|mongodb(?:\\+srv)?|redis|amqps?)://[^\\s:/@]{1,64}:[^\\s@]{6,256}@",
            RegexOption.IGNORE_CASE,
        ),
    )

    private val assignedSecretRegex = Regex(
        "\\b(api[_ -]?key|access[_ -]?token|auth[_ -]?token|client[_ -]?secret|secret|password|"
            + "passphrase|wifi password|wpa|wpa2|psk|kata sandi|sandi)\\b" +
            "\\s*[:=]\\s*[\"']?([A-Za-z0-9_./+~$@!#%^&*=\\-]{8,256})",
        RegexOption.IGNORE_CASE,
    )

    private val bearerRegex = Regex("\\bBearer\\s+([A-Za-z0-9._~+/=\\-]{16,255})\\b", RegexOption.IGNORE_CASE)

    private val longNumberRegex = Regex("(?<![\\d])\\d{9,}(?![\\d])")

    /**
     * Local network addresses leak home/office topology: a screenshot of WiFi settings, a router
     * page or a terminal is enough. Spans are precise, so these run by default.
     */
    private val ipv4Regex = Regex(
        "(?<![\\d.])(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?![\\d.])",
    )
    private val ipv6Regex = Regex(
        "(?i)(?<![:0-9a-f])(?:[0-9a-f]{1,4}:){3,7}[0-9a-f]{1,4}(?![:0-9a-f])",
    )
    private val macRegex = Regex("(?i)(?<![0-9a-f])(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}(?![0-9a-f])")

    /** Indonesian plate (B 1234 XYZ). The three-part shape is specific enough to run by default. */
    private val plateRegex = Regex(
        "(?<![A-Za-z0-9])([A-Za-z]{1,2}\\s\\d{1,4}\\s[A-Za-z]{1,3})(?![A-Za-z0-9])",
    )
    private val plateContextRegex = Regex(
        "(?i)\\b(?:plat|no\\.?\\s*(?:pol|polisi)|nomor polisi|license plate)\\b[^\\n]{0,12}?" +
            "([A-Za-z]{1,2}\\s?\\d{1,4}\\s?[A-Za-z]{0,3})",
    )

    /**
     * Street addresses. Keyword-gated and the noisiest pattern here, which is why
     * [SensitiveKind.ADDRESS] starts switched off and only runs when the user turns it on.
     */
    private val addressRegex = Regex(
        "(?i)\\b(?:jl\\.?|jalan|perumahan|perum|kompleks?|blok|rt\\s*/?\\s*rw|kecamatan|kelurahan|"
            + "kel\\.|kabupaten|kode pos|kodepos|postal code|street)\\b[^\\n]{4,80}",
    )

    /** IBAN: country code, two check digits, then 4-character groups. */
    private val ibanRegex = Regex(
        "(?<![A-Za-z0-9])[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?![A-Za-z0-9])",
    )

    /** Kinds that win when two matches cover the same characters. */
    private val kindPriority = mapOf(
        SensitiveKind.CARD to 0,
        SensitiveKind.OTP to 1,
        SensitiveKind.SECRET to 2,
        SensitiveKind.PASSPORT to 3,
        SensitiveKind.PHONE to 4,
        SensitiveKind.EMAIL to 5,
        SensitiveKind.ID_NUMBER to 6,
        SensitiveKind.ACCOUNT_NUMBER to 7,
        SensitiveKind.DOB to 8,
        SensitiveKind.LOCATION to 9,
        SensitiveKind.NETWORK to 10,
        SensitiveKind.PLATE to 11,
        SensitiveKind.ADDRESS to 12,
        SensitiveKind.LONG_NUMBER to 13,
    )

    /**
     * All sensitive spans in [text]. Overlapping hits are resolved in favour of the most specific
     * kind (a card number is not reported as an account number, an OTP is not reported as a phone
     * number) so the editor never shows duplicated highlight boxes.
     */
    fun findMatches(
        text: String,
        includeLongNumbers: Boolean = false,
    ): List<SensitiveMatch> {
        if (text.isBlank() || text.length > MAX_SCAN_LENGTH) return emptyList()

        val matches = ArrayList<SensitiveMatch>(32)

        emailRegex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.EMAIL, it.range) }
        phoneRegex.findAll(text).forEach { match ->
            if (isPlausiblePhone(match.value)) {
                matches += SensitiveMatch(SensitiveKind.PHONE, match.range)
            }
        }
        compactPhoneRegex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.PHONE, it.range) }

        cardCandidateRegex.findAll(text).forEach { match ->
            val digits = match.value.filter(Char::isDigit)
            if (digits.length in 13..19 && passesLuhn(digits)) {
                matches += SensitiveMatch(SensitiveKind.CARD, match.range)
            }
        }

        npwpRegex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.ID_NUMBER, it.range) }
        nikRegex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.ID_NUMBER, it.range) }
        nikCompactRegex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.ID_NUMBER, it.range) }

        passportContextRegex.findAll(text).forEach { match ->
            val number = match.groups[1] ?: return@forEach
            matches += SensitiveMatch(SensitiveKind.PASSPORT, number.range)
        }
        dobContextRegex.findAll(text).forEach { match ->
            val date = match.groups[1] ?: return@forEach
            matches += SensitiveMatch(SensitiveKind.DOB, date.range)
        }
        coordinateRegex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.LOCATION, it.range) }

        ipv4Regex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.NETWORK, it.range) }
        ipv6Regex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.NETWORK, it.range) }
        macRegex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.NETWORK, it.range) }

        plateRegex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.PLATE, it.range) }
        plateContextRegex.findAll(text).forEach { match ->
            val plate = match.groups[1] ?: return@forEach
            matches += SensitiveMatch(SensitiveKind.PLATE, plate.range)
        }

        ibanRegex.findAll(text).forEach { match ->
            // A 13-19 digit run that satisfies Luhn is a card; the grouped IBAN shape is an account.
            val digits = match.value.filter(Char::isDigit)
            if (digits.length < 13 || !passesLuhn(digits)) {
                matches += SensitiveMatch(SensitiveKind.ACCOUNT_NUMBER, match.range)
            }
        }

        addressRegex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.ADDRESS, it.range) }

        accountRegex.findAll(text).forEach { match ->
            val digits = match.value.filter(Char::isDigit)
            if (digits.length in 10..16 && !passesLuhn(digits)) {
                matches += SensitiveMatch(SensitiveKind.ACCOUNT_NUMBER, match.range)
            }
        }
        accountCompactRegex.findAll(text).forEach { match ->
            val digits = match.value.filter(Char::isDigit)
            if (!passesLuhn(digits)) {
                matches += SensitiveMatch(SensitiveKind.ACCOUNT_NUMBER, match.range)
            }
        }

        otpContextRegex.findAll(text).forEach { match ->
            val digits = match.groups[2] ?: return@forEach
            matches += SensitiveMatch(SensitiveKind.OTP, digits.range)
        }

        apiKeyRegexes.forEach { regex ->
            regex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.SECRET, it.range) }
        }
        assignedSecretRegex.findAll(text).forEach { match ->
            val value = match.groups[2]?.value.orEmpty()
            if (looksLikeSecretValue(value)) {
                matches += SensitiveMatch(SensitiveKind.SECRET, match.range)
            }
        }
        bearerRegex.findAll(text).forEach { match ->
            if (looksLikeSecretValue(match.groups[1]?.value.orEmpty())) {
                matches += SensitiveMatch(SensitiveKind.SECRET, match.range)
            }
        }

        if (includeLongNumbers) {
            longNumberRegex.findAll(text).forEach { matches += SensitiveMatch(SensitiveKind.LONG_NUMBER, it.range) }
        }

        return resolveOverlaps(matches)
    }

    /** True when the text contains at least one sensate span — used by the export verifier. */
    fun containsSensitive(text: String, includeLongNumbers: Boolean = false): Boolean =
        findMatches(text, includeLongNumbers).isNotEmpty()

    fun passesLuhn(digits: String): Boolean {
        if (digits.length < 12) return false
        var sum = 0
        var alternate = false
        for (index in digits.length - 1 downTo 0) {
            var value = digits[index] - '0'
            if (alternate) {
                value *= 2
                if (value > 9) value -= 9
            }
            sum += value
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    private fun isPlausiblePhone(raw: String): Boolean {
        val digits = raw.filter(Char::isDigit)
        if (digits.length !in 9..15) return false
        // Anything that is a valid card number is reported as a card, not a phone.
        if (digits.length in 13..19 && passesLuhn(digits)) return false
        val hasPhoneSyntax = raw.startsWith("+") || raw.any { it == '(' || it == ')' || it == '-' || it == '.' }
        return hasPhoneSyntax
    }

    private fun looksLikeSecretValue(value: String): Boolean {
        if (value.length < 8) return false
        val normalized = value.lowercase().trim('*', '•', '-', '_', '.')
        if (normalized.isBlank() || normalized.toSet().size <= 3) return false
        if (PLACEHOLDERS.any { normalized.contains(it) }) return false
        return normalized.any(Char::isDigit) && normalized.any { it.isLetter() }
    }

    private fun resolveOverlaps(matches: List<SensitiveMatch>): List<SensitiveMatch> {
        if (matches.size < 2) return matches
        val ranked = matches.sortedWith(
            compareBy(
                { it.range.first },
                { -(it.range.last - it.range.first) },
                { kindPriority[it.kind] ?: Int.MAX_VALUE },
            ),
        )
        val kept = ArrayList<SensitiveMatch>(ranked.size)
        ranked.forEach { candidate ->
            val clashesWithBetter = kept.any { existing ->
                existing.range.first <= candidate.range.last && candidate.range.first <= existing.range.last
            }
            if (!clashesWithBetter) kept += candidate
        }
        return kept.sortedBy { it.range.first }
    }

    private val PLACEHOLDERS = listOf("example", "changeme", "placeholder", "yourkey", "your-key", "redacted")
}
