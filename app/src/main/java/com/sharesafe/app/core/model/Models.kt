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
            PHONE, EMAIL, CARD, ID_NUMBER, PASSPORT, ACCOUNT_NUMBER, OTP, DOB, SECRET, LOCATION,
            NETWORK, PLATE, LONG_NUMBER, ADDRESS, QR, FACE,
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
    }
}

/** Beautify presets — the "screenshot sharing" wrapper around the redacted image. */
enum class BackgroundStyle(val id: String) {
    AUTO("auto"),
    NIGHT("night"),
    PAPER("paper"),
    GRADIENT("gradient");

    companion object {
        fun fromId(id: String?): BackgroundStyle = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

data class BeautifyConfig(
    val enabled: Boolean = false,
    val paddingFraction: Float = 0.06f,
    val cornerFraction: Float = 0.04f,
    val background: BackgroundStyle = BackgroundStyle.AUTO,
) {
    companion object {
        const val MAX_PADDING = 0.16f
        const val MAX_CORNER = 0.12f
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
