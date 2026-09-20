package com.sharesafe.app.core.model

/**
 * Geometry rule for the whole app: every region is stored as a **normalized** rectangle in the
 * coordinate space of the original source image (0f..1f). Preview bitmaps, detection bitmaps and
 * the full-resolution export are all derived from that single space, so zooming, cropping and
 * re-rendering can never drift out of sync.
 */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun isEmpty(): Boolean = width <= 0f || height <= 0f

    fun clampToUnit(): NormRect = NormRect(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f),
    )

    /** Keeps a dragged box inside the image and above a minimum usable size. */
    fun clampWithMinSize(minSize: Float = MIN_SIZE): NormRect {
        var l = left
        var t = top
        var r = right
        var b = bottom
        if (r - l < minSize) {
            val cx = ((l + r) / 2f).coerceIn(0f, 1f)
            l = (cx - minSize / 2f).coerceAtLeast(0f)
            r = (cx + minSize / 2f).coerceAtMost(1f)
        }
        if (b - t < minSize) {
            val cy = ((t + b) / 2f).coerceIn(0f, 1f)
            t = (cy - minSize / 2f).coerceAtLeast(0f)
            b = (cy + minSize / 2f).coerceAtMost(1f)
        }
        return NormRect(l, t, r, b)
    }

    fun union(other: NormRect): NormRect = NormRect(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom

    fun containsRect(other: NormRect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    fun intersects(other: NormRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** Grows the box by a fraction of its own size, so small text stays covered. */
    fun padded(fraction: Float, minFractionOfImage: Float = 0f): NormRect {
        val padX = (width * fraction).coerceAtLeast(minFractionOfImage)
        val padY = (height * fraction).coerceAtLeast(minFractionOfImage * 0.5f)
        return NormRect(left - padX, top - padY, right + padX, bottom + padY).clampToUnit()
    }

    /** Shifts the box in image space, keeping its size. */
    fun translated(dx: Float, dy: Float): NormRect {
        val clampedDx = dx.coerceIn(-left, 1f - right)
        val clampedDy = dy.coerceIn(-top, 1f - bottom)
        return NormRect(left + clampedDx, top + clampedDy, right + clampedDx, bottom + clampedDy)
    }

    fun area(): Float = width * height

    companion object {
        const val MIN_SIZE = 0.02f
        val NONE = NormRect(0f, 0f, 0f, 0f)
    }
}

/** Integer rectangle in source-image pixel space. */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun isValid(maxWidth: Int, maxHeight: Int): Boolean =
        left >= 0 && top >= 0 && right <= maxWidth && bottom <= maxHeight && width > 0 && height > 0

    companion object {
        fun full(width: Int, height: Int) = IntRect(0, 0, width, height)
    }
}

/** How a redacted region is rendered into the pixels. */
enum class RedactionStyle(val id: String) {
    BLUR("blur"),
    PIXELATE("pixelate"),
    BLACK_BAR("black"),
    TINT("tint");

    companion object {
        val DEFAULT = PIXELATE
        fun fromId(id: String?): RedactionStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Groups the detection chips in the editor toolbar. */
enum class KindGroup { TEXT, CODE, FACE, MANUAL }

/**
 * Everything ShareSafe knows how to find. [defaultEnabled] documents how aggressive the default
 * pass is: precise patterns are on, loose heuristics (any long digit run) start off.
 */
enum class SensitiveKind(val group: KindGroup, val defaultEnabled: Boolean) {
    PHONE(KindGroup.TEXT, true),
    /** Chat Privacy Mode: the contact or sender name in a conversation header. */
    NAME(KindGroup.TEXT, true),
    /** Chat Privacy Mode: profile pictures, found as faces on a conversation header. */
    AVATAR(KindGroup.FACE, true),
    EMAIL(KindGroup.TEXT, true),
    CARD(KindGroup.TEXT, true),
    ID_NUMBER(KindGroup.TEXT, true),
    PASSPORT(KindGroup.TEXT, true),
    ACCOUNT_NUMBER(KindGroup.TEXT, true),
    OTP(KindGroup.TEXT, true),
    DOB(KindGroup.TEXT, true),
    SECRET(KindGroup.TEXT, true),
    LOCATION(KindGroup.TEXT, true),
    NETWORK(KindGroup.TEXT, true),
    PLATE(KindGroup.TEXT, true),
    LONG_NUMBER(KindGroup.TEXT, false),
    ADDRESS(KindGroup.TEXT, false),
    QR(KindGroup.CODE, true),
    FACE(KindGroup.FACE, true),
    MANUAL(KindGroup.MANUAL, true);

    companion object {
        /** Order used by the UI chips. */
        val chipOrder: List<SensitiveKind> = listOf(
            PHONE, NAME, AVATAR, EMAIL, CARD, ID_NUMBER, PASSPORT, ACCOUNT_NUMBER, OTP, DOB,
            SECRET, LOCATION, NETWORK, PLATE, LONG_NUMBER, ADDRESS, QR, FACE,
        )
    }
}

enum class DetectionOrigin { TEXT_PATTERN, BARCODE, FACE }

/** One thing worth hiding, with the text that triggered it (kept in memory only). */
data class Detection(
    val id: String,
    val kind: SensitiveKind,
    val bounds: NormRect,
    val label: String,
    val origin: DetectionOrigin,
    val enabled: Boolean = true,
)

/** A region the user drew by hand. */
data class ManualRegion(
    val id: String,
    val bounds: NormRect,
)

data class RedactionRegion(
    val bounds: NormRect,
    val enabled: Boolean,
) {
    companion object {
        fun active(detections: List<Detection>, manual: List<ManualRegion>): List<RedactionRegion> =
            buildList {
                detections.filter { it.enabled }.forEach { add(RedactionRegion(it.bounds, true)) }
                manual.forEach { add(RedactionRegion(it.bounds, true)) }
            }

        /** Kinds that are a photo of a person rather than text, and so get the mask treatment. */
        val FACE_LIKE_KINDS: Set<SensitiveKind> = setOf(SensitiveKind.FACE, SensitiveKind.AVATAR)
    }
}

/** Beautify presets — the "screenshot sharing" wrapper around the redacted image. */
enum class BackgroundStyle(val id: String) {
    AUTO("auto"),
    NIGHT("night"),
    PAPER("paper"),
    GRADIENT("gradient"),

    /** One flat colour sampled from the screenshot itself. */
    SOLID("solid");

    companion object {
        fun fromId(id: String?): BackgroundStyle = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

data class BeautifyConfig(
    val enabled: Boolean = false,
    val paddingFraction: Float = 0.06f,
    val cornerFraction: Float = 0.04f,
    val background: BackgroundStyle = BackgroundStyle.AUTO,
    /** 0 = no drop shadow, 1 = deepest. Only meaningful when [enabled]. */
    val shadow: Float = 0.5f,
) {
    companion object {
        const val MAX_PADDING = 0.16f
        const val MAX_CORNER = 0.12f

        fun of(preset: BeautifyPreset): BeautifyConfig = when (preset) {
            BeautifyPreset.OFF -> BeautifyConfig()
            else -> BeautifyConfig(
                enabled = true,
                paddingFraction = preset.padding,
                cornerFraction = preset.corner,
                background = preset.background,
                shadow = preset.shadow,
            )
        }
    }
}

/**
 * Ready-made looks. Four are free and two are premium: this is the one place where a rewarded video
 * buys something, and what it buys is a background — never a safety feature.
 *
 * A preset is just a [BeautifyConfig], so anything a preset sets can still be adjusted by hand
 * afterwards; the presets exist to make the good-looking result the default, not to hide the knobs.
 */
enum class BeautifyPreset(
    val id: String,
    val premium: Boolean,
    val padding: Float,
    val corner: Float,
    val background: BackgroundStyle,
    val shadow: Float,
) {
    OFF("off", false, 0f, 0f, BackgroundStyle.AUTO, 0f),
    CLEAN("clean", false, 0.05f, 0.035f, BackgroundStyle.PAPER, 0.30f),
    NIGHT("night", false, 0.06f, 0.045f, BackgroundStyle.NIGHT, 0.55f),
    SOLID("solid", false, 0.06f, 0.040f, BackgroundStyle.SOLID, 0.45f),

    /** Premium: a wide, shadowed frame on a violet-to-teal gradient. */
    AURORA("aurora", true, 0.09f, 0.060f, BackgroundStyle.GRADIENT, 0.80f),

    /** Premium: the biggest, darkest frame, for screenshots that should look like a spec sheet. */
    STUDIO("studio", true, 0.12f, 0.075f, BackgroundStyle.NIGHT, 1.00f);

    companion object {
        val DEFAULT = CLEAN
        fun fromId(id: String?): BeautifyPreset = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * How face-like detections are drawn.
 *
 * Boxes are the honest default for text, but a rectangle around a face reads as "someone censored
 * this", which is exactly what the user is trying not to broadcast. [SOFT_OVAL] feathered-masks the
 * face instead, so nothing is redacted any less — it just stops looking like a sticker.
 */
enum class FaceMaskStyle(val id: String) {
    BOX("box"),
    SOFT_OVAL("oval");

    companion object {
        val DEFAULT = SOFT_OVAL
        fun fromId(id: String?): FaceMaskStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * What to trim away before redaction. Both flags are user-visible toggles so a mis-crop is one tap
 * away from being undone — the crop itself is never destructive.
 */
data class CropConfig(
    val trimSystemBars: Boolean = true,
    val trimBlankEdges: Boolean = true,
)

data class CropResult(
    val rect: IntRect,
    val method: CropMethod,
)

enum class CropMethod { NONE, SYSTEM_BARS, BLANK_EDGES, BOTH }
