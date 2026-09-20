package com.sharesafe.app.core.detect

/**
 * Barcode family, kept independent of the ML Kit `Barcode` type constants so the classifier stays
 * plain Kotlin and can be unit tested on the JVM.
 */
enum class CodeFormat { URL, WIFI, EMAIL, PHONE, SMS, GEO, CONTACT, CALENDAR, OTHER }

/** What a decoded payload actually is, from the user's point of view. */
enum class CodePayloadKind {
    QRIS_PAYMENT,
    PAYMENT_LINK,
    TWO_FACTOR,
    WIFI,
    CONTACT,
    SOCIAL_PROFILE,
    EMAIL,
    PHONE,
    SMS,
    LOCATION,
    EVENT,
    URL,
    TEXT,
}

data class CodePayload(
    val kind: CodePayloadKind,
    /** True when the decoded payload itself carries data worth hiding. */
    val containsPersonalData: Boolean,
)

/**
 * Classifies the *contents* of a scanned code — a QR tile in a screenshot is not just a square, it
 * can be a QRIS payment request, a WiFi credential, a contact card or a link with a tracking id.
 * The decoded payload is inspected here and then dropped; nothing is stored or logged.
 */
object CodePayloadClassifier {

    /** Payload kinds that are sensitive by nature, whatever else they contain. */
    private val alwaysSensitive = setOf(
        CodePayloadKind.QRIS_PAYMENT,
        CodePayloadKind.PAYMENT_LINK,
        CodePayloadKind.TWO_FACTOR,
        CodePayloadKind.WIFI,
        CodePayloadKind.CONTACT,
        CodePayloadKind.SOCIAL_PROFILE,
        CodePayloadKind.EMAIL,
        CodePayloadKind.PHONE,
        CodePayloadKind.SMS,
        CodePayloadKind.LOCATION,
    )

    /** EMVCo payloads (QRIS, GoPay, OVO, ShopeePay…) always open with this tag. */
    private const val QRIS_PREFIX = "000201"

    /**
     * Deep links that move money or open a wallet. Unlike a plain URL these are actionable the
     * moment somebody screenshots them, so they are treated as sensitive on sight.
     */
    private val paymentPrefixes = listOf(
        "UPI://", "PAYPAL.ME/", "PAYPAL.COM/", "VENMO.COM/", "CASH.APP/", "REVOLUT.ME/",
        "DANA://", "GOPAY://", "GOJEK://", "OVO://", "SHOPEEPAY://", "LINKAJA://",
        "BITCOIN:", "ETHEREUM:", "WISE.COM/", "BUY.STRIPE.COM/",
    )

    /** 2FA enrolment links carry the shared secret in plain text. */
    private val twoFactorPrefixes = listOf("OTPAUTH://", "STEAM://", "2FAS://")

    /** Profile links that identify a person, even when the payload has no other personal data. */
    private val socialHosts = listOf(
        "INSTAGRAM.COM/", "FACEBOOK.COM/", "FB.COM/", "TIKTOK.COM/", "TWITTER.COM/",
        "X.COM/", "LINKEDIN.COM/IN/", "T.ME/", "WA.ME/", "SNAPCHAT.COM/", "THREADS.NET/",
    )

    fun classify(format: CodeFormat, rawValue: String?): CodePayload {
        val raw = rawValue?.trim().orEmpty()
        val upper = raw.uppercase()
        val kind = when {
            raw.startsWith(QRIS_PREFIX) -> CodePayloadKind.QRIS_PAYMENT
            twoFactorPrefixes.any { upper.startsWith(it) } -> CodePayloadKind.TWO_FACTOR
            paymentPrefixes.any { upper.contains(it) } -> CodePayloadKind.PAYMENT_LINK
            socialHosts.any { upper.contains(it) } -> CodePayloadKind.SOCIAL_PROFILE
            format == CodeFormat.WIFI || upper.startsWith("WIFI:") -> CodePayloadKind.WIFI
            format == CodeFormat.CONTACT ||
                upper.startsWith("MECARD:") ||
                upper.startsWith("BEGIN:VCARD") -> CodePayloadKind.CONTACT
            format == CodeFormat.EMAIL || upper.startsWith("MAILTO:") -> CodePayloadKind.EMAIL
            format == CodeFormat.PHONE || upper.startsWith("TEL:") -> CodePayloadKind.PHONE
            format == CodeFormat.SMS ||
                upper.startsWith("SMSTO:") ||
                upper.startsWith("SMS:") -> CodePayloadKind.SMS
            format == CodeFormat.GEO || upper.startsWith("GEO:") -> CodePayloadKind.LOCATION
            format == CodeFormat.CALENDAR || upper.startsWith("BEGIN:VEVENT") -> CodePayloadKind.EVENT
            format == CodeFormat.URL ||
                upper.startsWith("HTTP://") ||
                upper.startsWith("HTTPS://") ||
                upper.startsWith("WWW.") -> CodePayloadKind.URL

            else -> CodePayloadKind.TEXT
        }
        val personal = kind in alwaysSensitive ||
            (raw.isNotEmpty() && SensitivePatterns.findMatches(raw).isNotEmpty())
        return CodePayload(kind = kind, containsPersonalData = personal)
    }

    /** Short label for the detection chip, e.g. "QR · QRIS payment". */
    fun label(payload: CodePayload): String = when (payload.kind) {
        CodePayloadKind.QRIS_PAYMENT -> "QRIS payment"
        CodePayloadKind.PAYMENT_LINK -> "Payment link"
        CodePayloadKind.TWO_FACTOR -> "2FA secret"
        CodePayloadKind.WIFI -> "WiFi access"
        CodePayloadKind.SOCIAL_PROFILE -> "Social profile"
        CodePayloadKind.CONTACT -> "Contact card"
        CodePayloadKind.EMAIL -> "Email"
        CodePayloadKind.PHONE -> "Phone"
        CodePayloadKind.SMS -> "SMS message"
        CodePayloadKind.LOCATION -> "Location"
        CodePayloadKind.EVENT -> "Calendar event"
        CodePayloadKind.URL ->
            if (payload.containsPersonalData) "Link with personal data" else "Link"

        CodePayloadKind.TEXT ->
            if (payload.containsPersonalData) "Personal data" else "Code"
    }
}
