package com.sharesafe.app.core.detect

import android.graphics.Bitmap
import android.os.SystemClock
import com.sharesafe.app.core.model.Detection
import com.sharesafe.app.core.model.DetectionOrigin
import com.sharesafe.app.core.model.SensitiveKind
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class ScanOptions(
    val detectCodes: Boolean = true,
    val detectFaces: Boolean = true,
    /** Off by default: highlighting every long digit run also highlights tracking numbers. */
    val includeLongNumbers: Boolean = false,
    /**
     * Chat Privacy Mode. Adds the conversation-header name and the profile pictures, and changes
     * nothing else about the pass: the ordinary patterns keep running, which is why a phone number
     * shown as a contact name still reads as a phone number.
     */
    val chat: ChatPreset = ChatPreset.DEFAULT,
)

data class ScanResult(
    val detections: List<Detection>,
    val text: String,
    /** Non-fatal problems worth telling the user about, e.g. a recognizer that failed. */
    val warnings: List<String>,
    val durationMs: Long,
    /**
     * A conversation layout the header geometry agreed on, offered as a one-tap suggestion when the
     * user has not already chosen a preset. Null when the screenshot does not look like a chat.
     */
    val suggestedChat: ChatPreset? = null,
)

/**
 * One pass over an image: OCR + pattern matching, barcode boxes and face boxes, all on device and
 * in parallel. The scanners only read the bitmap, which the caller keeps alive for the pass.
 *
 * [onPartial] reports detections as soon as they are known — text lands in well under a second
 * while barcode and face detection need several, so the editor can already draw the text boxes
 * instead of showing a spinner for the whole pass.
 */
object SensitiveScanner {

    suspend fun scan(
        bitmap: Bitmap,
        options: ScanOptions = ScanOptions(),
        onPartial: ((List<Detection>) -> Unit)? = null,
    ): ScanResult =
        coroutineScope {
            val started = SystemClock.elapsedRealtime()
            val warnings = ArrayList<String>(2)

            val spansTask = async {
                runCatching { OcrEngine.recognizeSpans(bitmap) }
                    .getOrElse {
                        warnings += "text recognition unavailable"
                        emptyList()
                    }
            }
            val codesTask = async {
                if (!options.detectCodes) {
                    emptyList()
                } else {
                    runCatching { CodeEngine.scan(bitmap) }
                        .getOrElse {
                            warnings += "barcode recognition unavailable"
                            emptyList()
                        }
                }
            }
            val facesTask = async {
                if (!options.detectFaces) emptyList() else FaceEngine.detect(bitmap)
            }

            val spans = spansTask.await()
            val textMatches = SensitivePatterns.findMatches(
                text = spans.joinToString(" ") { it.text },
                includeLongNumbers = options.includeLongNumbers,
            )
            val textDetections = DetectionMapper.finalize(
                DetectionMapper.buildDetections(spans, textMatches),
            )
            if (textDetections.isNotEmpty()) onPartial?.invoke(textDetections)

            val codes = codesTask.await()
            val faces = facesTask.await()

            val codeDetections = codes.mapIndexed { index, hit ->
                Detection(
                    id = "code-${index + 1}",
                    kind = SensitiveKind.QR,
                    bounds = hit.bounds.padded(0.02f),
                    label = hit.label,
                    origin = DetectionOrigin.BARCODE,
                )
            }

            // Chat Privacy Mode: a face in a conversation header is somebody's profile picture, so
            // it is reported as an avatar (and masked as one) instead of as a plain face. Faces
            // that are avatars are not reported twice.
            val avatarHits = ChatHeuristics.avatarDetections(faces, options.chat)
            val avatarIndices = avatarHits.mapTo(HashSet()) { it.faceIndex }
            val faceDetections = faces.mapIndexedNotNull { index, bounds ->
                if (index in avatarIndices) {
                    null
                } else {
                    Detection(
                        id = "face-${index + 1}",
                        kind = SensitiveKind.FACE,
                        bounds = bounds,
                        label = "Face",
                        origin = DetectionOrigin.FACE,
                    )
                }
            }
            val nameDetections = ChatHeuristics.dropCovered(
                candidates = ChatHeuristics.nameDetections(spans, options.chat),
                existing = textDetections + codeDetections,
            )

            ScanResult(
                detections = DetectionMapper.finalize(
                    textDetections + codeDetections + faceDetections + nameDetections +
                        avatarHits.map { it.detection },
                ),
                text = spans.joinToString("\n") { it.text },
                warnings = warnings.toList(),
                durationMs = SystemClock.elapsedRealtime() - started,
                suggestedChat = if (options.chat.isChat) null else ChatHeuristics.detectPreset(spans, faces),
            )
        }

    fun countByKind(detections: List<Detection>): Map<SensitiveKind, Int> =
        detections.groupingBy { it.kind }.eachCount()
}
