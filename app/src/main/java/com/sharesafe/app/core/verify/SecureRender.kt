package com.sharesafe.app.core.verify

import android.graphics.Bitmap
import android.graphics.Rect
import com.sharesafe.app.core.model.IntRect
import com.sharesafe.app.core.model.NormRect
import com.sharesafe.app.core.model.SensitiveKind
import com.sharesafe.app.core.render.RenderPipeline

/**
 * Render → verify → repair → verify again, in one place.
 *
 * This loop is the app's actual promise, so it must not exist twice: the editor's export and Batch
 * Protect both call it, which is what makes "the batch is as safe as doing it by hand" a fact rather
 * than a hope. It was moved out of the editor view model unchanged.
 *
 * The loop stops on the first clean verdict, on the first round that finds nothing new to hide, or
 * after [DEFAULT_MAX_ROUNDS] so a pathological image cannot spin forever.
 */
object SecureRender {

    const val DEFAULT_MAX_ROUNDS = 2

    data class Outcome(
        val bitmap: Bitmap,
        val redactedRects: List<Rect>,
        val verify: VerifyResult?,
        /** Regions the automatic repair added, so callers can surface them as editable boxes. */
        val repaired: List<NormRect>,
    )

    /**
     * [request] must target full resolution. Kinds the user switched off are neither reported nor
     * repaired: the verdict has to describe the redaction they asked for, not an imaginary one.
     */
    suspend fun renderVerified(
        request: RenderPipeline.Request,
        ignoreKinds: Set<SensitiveKind>,
        verifyEnabled: Boolean,
        autoFix: Boolean,
        checkFaces: Boolean,
        maxRounds: Int = DEFAULT_MAX_ROUNDS,
        onProgress: (round: Int) -> Unit = {},
    ): Outcome {
        var current = request
        var rendered = RenderPipeline.renderDetailed(current)
        val repaired = ArrayList<NormRect>()

        var verdict = if (verifyEnabled) {
            RedactionVerifier.verify(
                exported = rendered.bitmap,
                redactedRects = rendered.redactedRects,
                checkFaces = checkFaces,
                ignoreKinds = ignoreKinds,
            )
        } else {
            null
        }

        var rounds = 0
        while (autoFix && verdict != null && verdict.completed && !verdict.isClean &&
            rounds < maxRounds
        ) {
            val content = rendered.contentRect
            val additions = AutoFixPlanner.plan(
                leftovers = verdict.leftoverBounds,
                content = IntRect(content.left, content.top, content.right, content.bottom),
                crop = current.crop,
                sourceWidth = request.source.width,
                sourceHeight = request.source.height,
                exportedWidth = rendered.bitmap.width,
                exportedHeight = rendered.bitmap.height,
                existing = current.regions,
            )
            if (additions.isEmpty()) break

            repaired += additions
            val previous = rendered.bitmap
            // Repairs are rectangles on purpose, even over a face: if the verifier could still see
            // something, the shape of the mask is no longer a design question.
            current = current.copy(
                regions = current.regions + additions,
                faceRegions = current.faceRegions,
            )
            rendered = RenderPipeline.renderDetailed(current)
            if (previous !== rendered.bitmap && !previous.isRecycled) previous.recycle()
            verdict = RedactionVerifier.verify(
                exported = rendered.bitmap,
                redactedRects = rendered.redactedRects,
                checkFaces = checkFaces,
                ignoreKinds = ignoreKinds,
            )
            rounds++
            onProgress(rounds)
        }

        return Outcome(
            bitmap = rendered.bitmap,
            redactedRects = rendered.redactedRects,
            verify = verdict,
            repaired = repaired,
        )
    }
}
