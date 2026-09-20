package com.sharesafe.app.core.detect

import com.sharesafe.app.core.model.Detection
import com.sharesafe.app.core.model.DetectionOrigin
import com.sharesafe.app.core.model.NormRect
import com.sharesafe.app.core.model.SensitiveKind
import kotlin.math.roundToInt

/**
 * Which conversation app a screenshot came from. The presets differ in where the conversation
 * header sits and how the name and profile picture are laid out, which is the only thing that
 * actually varies between them.
 */
enum class ChatPreset(val id: String) {
    OFF("off"),
    WHATSAPP("whatsapp"),
    TELEGRAM("telegram"),

    /** Generic direct-message layout: most other apps put the avatar on the right. */
    DM("dm");

    val isChat: Boolean get() = this != OFF

    companion object {
        val DEFAULT = OFF
        fun fromId(id: String?): ChatPreset = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Normalized anchors for one conversation layout. */
data class ChatLayout(
    /** Where the contact/sender name text lives. */
    val nameBand: NormRect,
    /** Where the header profile picture lives. */
    val avatarBand: NormRect,
)

/**
 * Chat Privacy Mode heuristics.
 *
 * The honest scope, because this is the part users will judge:
 *
 * - **It finds what is geometrically knowable.** A conversation header is always at the same place:
 *   below the status bar, one avatar plus one name row. So the name is taken from the OCR spans that
 *   actually fall inside that band, which means it is the *real* text box rather than a blanket
 *   rectangle across the top of the screen.
 * - **Sender names inside bubbles are not attempted.** Picking a person's name out of a message body
 *   needs name recognition, not geometry; a guess would either miss it or eat the message. The phone
 *   numbers, e-mails, codes and links in those bubbles are already covered by the ordinary patterns,
 *   and the manual editor is one tap away for the rest.
 * - **Everything it produces is an ordinary detection.** Name and avatar boxes arrive switched on,
 *   with their own chips, and can be turned off individually — the user stays the last word.
 *
 * Pure Kotlin on purpose: this is geometry plus a threshold, so it is unit-tested directly.
 */
object ChatHeuristics {

    /** Channel avatars in a message list are small; a big face in the middle is a photo, not an avatar. */
    private const val BUBBLE_AVATAR_MAX_WIDTH = 0.16f
    private const val LEFT_COLUMN_EDGE = 0.30f

    /**
     * Layout anchors as fractions of the **source screenshot**, status bar included. Trimming
     * happens after detection, so the bands are expressed in the space the detector sees.
     */
    fun layoutFor(preset: ChatPreset): ChatLayout = when (preset) {
        ChatPreset.OFF -> ChatLayout(NormRect.NONE, NormRect.NONE)

        // Avatar on the left, name beside it, actions on the right.
        ChatPreset.WHATSAPP -> ChatLayout(
            nameBand = NormRect(left = 0.17f, top = 0.035f, right = 0.72f, bottom = 0.10f),
            avatarBand = NormRect(left = 0.01f, top = 0.025f, right = 0.17f, bottom = 0.115f),
        )

        // Telegram's header is a little taller and the title starts slightly further in.
        ChatPreset.TELEGRAM -> ChatLayout(
            nameBand = NormRect(left = 0.15f, top = 0.035f, right = 0.70f, bottom = 0.11f),
            avatarBand = NormRect(left = 0.01f, top = 0.025f, right = 0.15f, bottom = 0.12f),
        )

        // Camera-first apps put the avatar on the right and let the name run from the left edge.
        ChatPreset.DM -> ChatLayout(
            nameBand = NormRect(left = 0.05f, top = 0.035f, right = 0.62f, bottom = 0.105f),
            avatarBand = NormRect(left = 0.80f, top = 0.025f, right = 0.99f, bottom = 0.115f),
        )
    }

    /**
     * Contact/sender names: only the OCR spans that sit inside the header band, so the box hugs real
     * text. Spans that are just punctuation or a single character are ignored — those are status
     * icons or the back chevron, not a name.
     */
    fun nameDetections(
        spans: List<TextSpan>,
        preset: ChatPreset,
    ): List<Detection> {
        if (!preset.isChat) return emptyList()
        val band = layoutFor(preset).nameBand
        if (band.isEmpty()) return emptyList()
        return spans
            .filter { span -> span.text.trim().length >= 2 && band.overlapFraction(span.bounds) >= 0.5f }
            .mapIndexed { index, span ->
                Detection(
                    id = "name-${index + 1}",
                    kind = SensitiveKind.NAME,
                    bounds = span.bounds.padded(0.04f),
                    label = span.text.trim().take(48),
                    origin = DetectionOrigin.TEXT_PATTERN,
                )
            }
    }

    /** A face that turned out to be somebody's profile picture, and which face it came from. */
    data class AvatarHit(val faceIndex: Int, val detection: Detection)

    /**
     * Profile pictures: faces that sit in the header avatar band, plus the small faces that line up
     * against an edge in a message list. Returning the source index as well lets the caller replace
     * the plain face detection with this one instead of reporting the same person twice.
     */
    fun avatarDetections(
        faces: List<NormRect>,
        preset: ChatPreset,
    ): List<AvatarHit> {
        if (!preset.isChat) return emptyList()
        val headerBand = layoutFor(preset).avatarBand
        return faces.mapIndexedNotNull { index, face ->
            // Measured against the face, not the band: the question is whether *this face* is the one
            // sitting in the header, and a band is far bigger than any picture in it.
            val isAvatar = headerBand.overlapFraction(face) >= IN_BAND || isValidBubbleAvatar(face)
            if (!isAvatar) {
                null
            } else {
                AvatarHit(
                    faceIndex = index,
                    detection = Detection(
                        id = "avatar-${index + 1}",
                        kind = SensitiveKind.AVATAR,
                        bounds = squareAround(face, EXPANSION),
                        label = "Avatar",
                        origin = DetectionOrigin.FACE,
                    ),
                )
            }
        }
    }

    /**
     * Which preset this screenshot looks like, or null when nothing chat-shaped is there.
     *
     * This is what makes Chat Privacy Mode automatic without making it reckless: rather than silently
     * redacting the top band of every image — where the clock, the battery and a document title also
     * live — the geometry has to *agree* with itself. A name-like text span inside the header band is
     * not enough on its own, and a face in the header is not enough on its own; a real conversation
     * header has both, side by side, which is a combination a settings screen does not produce. When
     * the two do line up the caller can offer the preset as a one-tap suggestion, and when they do not
     * nothing is guessed.
     *
     * The side the avatar sits on is what separates them: left for WhatsApp and Telegram, right for
     * camera-first apps. Telegram wins ties over WhatsApp because its header is the taller of the two,
     * so a face that reaches the bottom of both bands is a Telegram one.
     */
    fun detectPreset(
        spans: List<TextSpan>,
        faces: List<NormRect>,
    ): ChatPreset? {
        if (faces.isEmpty() || spans.isEmpty()) return null
        return LAYOUT_ORDER
            .mapNotNull { preset ->
                val layout = layoutFor(preset)
                val facesInBand = faces.filter { layout.avatarBand.overlapFraction(it) >= IN_BAND }
                val namesInBand = spans.filter { isNameLike(it, layout.nameBand) }
                if (facesInBand.isEmpty() || namesInBand.isEmpty()) {
                    null
                } else {
                    // Both bands matched, so the tie-break is how well the layout explains the face:
                    // a header that spills past WhatsApp's band is Telegram's taller one. Scores are
                    // rounded to whole-percent steps so float noise cannot decide a tie, and the
                    // rounded comparison is what lets the far more common app keep an exact tie.
                    val best = facesInBand.maxOf { layout.avatarBand.overlapFraction(it) }
                    preset to (best * 100f).roundToInt()
                }
            }
            // First of the highest scores wins, which is why the order is a preference order.
            .maxByOrNull { it.second }
            ?.first
    }

    /** WhatsApp first: its band is the subset, so ties have to fall to the likelier app. */
    private val LAYOUT_ORDER = listOf(ChatPreset.WHATSAPP, ChatPreset.TELEGRAM, ChatPreset.DM)


    /** How much of a face has to lie inside a band before the band is said to contain it. */
    private const val IN_BAND = 0.5f

    /** A "header" that starts below a fifth of the screen is a message, not a header. */
    private const val HEADER_LIMIT = 0.2f

    private fun isNameLike(span: TextSpan, band: NormRect): Boolean =
        span.text.trim().length >= 3 &&
            band.overlapFraction(span.bounds) >= 0.5f &&
            span.bounds.top < HEADER_LIMIT

    /**
     * Drops a chat candidate that an ordinary pattern already covers with a more precise label — a
     * contact name that is just a phone number should read "Phone", not "Name".
     */
    fun dropCovered(
        candidates: List<Detection>,
        existing: List<Detection>,
    ): List<Detection> = candidates.filter { candidate ->
        existing.none { it.bounds.intersects(candidate.bounds) }
    }

    /** A small face hugging an edge of the screen is a channel or sender picture. */
    private fun isValidBubbleAvatar(face: NormRect): Boolean {
        if (face.width > BUBBLE_AVATAR_MAX_WIDTH) return false
        if (face.width <= 0f || face.height <= 0f) return false
        val square = face.width / face.height in 0.6f..1.7f
        val huggingEdge = face.left <= LEFT_COLUMN_EDGE || face.right >= 1f - LEFT_COLUMN_EDGE
        return square && huggingEdge
    }

    /**
     * Grows a face box into the square a round avatar occupies. A detector returns the face itself;
     * the picture is usually a bit larger and inset inside its own circle, so covering the square
     * (rather than the face) is what actually hides the person's picture.
     */
    fun squareAround(face: NormRect, expansion: Float = EXPANSION): NormRect {
        if (face.isEmpty()) return face
        val cx = (face.left + face.right) / 2f
        val cy = (face.top + face.bottom) / 2f
        val half = (maxOf(face.width, face.height) * expansion) / 2f
        return NormRect(cx - half, cy - half, cx + half, cy + half).clampToUnit()
    }

    private const val EXPANSION = 1.75f
}

/** How much of [other] lies inside this rectangle, as a fraction of [other]'s own area. */
private fun NormRect.overlapFraction(other: NormRect): Float {
    if (other.isEmpty() || isEmpty()) return 0f
    val left = maxOf(this.left, other.left)
    val top = maxOf(this.top, other.top)
    val right = minOf(this.right, other.right)
    val bottom = minOf(this.bottom, other.bottom)
    if (right <= left || bottom <= top) return 0f
    val overlap = (right - left) * (bottom - top)
    return overlap / other.area()
}
