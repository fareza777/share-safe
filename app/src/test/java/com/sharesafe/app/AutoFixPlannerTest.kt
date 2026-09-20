package com.sharesafe.app

import com.sharesafe.app.core.model.IntRect
import com.sharesafe.app.core.model.NormRect
import com.sharesafe.app.core.verify.AutoFixPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repair pass reads coordinates in the exported bitmap and has to write them back into source
 * space. A mistake here would hide the wrong pixels, so the mapping is pinned down numerically.
 */
class AutoFixPlannerTest {

    private val source = 1000 to 2000
    private val crop = IntRect(0, 100, 1000, 1900)

    @Test
    fun mapsLeftoverBackThroughCropAndContent() {
        // No beautify padding: content fills the export exactly.
        val plan = AutoFixPlanner.plan(
            leftovers = listOf(NormRect(0.1f, 0.2f, 0.2f, 0.3f)),
            content = IntRect(0, 0, 1000, 1800),
            crop = crop,
            sourceWidth = source.first,
            sourceHeight = source.second,
            exportedWidth = 1000,
            exportedHeight = 1800,
        )

        assertEquals(1, plan.size)
        val rect = plan.first()
        // x: 100..200 source px; y: 100 + 0.2*1800 = 460 .. 100 + 0.3*1800 = 640, plus 2% padding.
        assertEquals(0.098f, rect.left, 0.002f)
        assertEquals(0.228f, rect.top, 0.002f)
        assertEquals(0.202f, rect.right, 0.002f)
        assertEquals(0.322f, rect.bottom, 0.002f)
    }

    @Test
    fun beautifyPaddingDoesNotShiftTheResult() {
        // Same crop, but the export is padded by 50px on every side: the leftover coordinates below
        // are the exported-normalized version of the same 10%..20% x / 20%..30% y window.
        val plan = AutoFixPlanner.plan(
            leftovers = listOf(NormRect(0.13636f, 0.21579f, 0.22727f, 0.31053f)),
            content = IntRect(50, 50, 1050, 1850),
            crop = crop,
            sourceWidth = source.first,
            sourceHeight = source.second,
            exportedWidth = 1100,
            exportedHeight = 1900,
        )

        assertEquals(1, plan.size)
        val rect = plan.first()
        // Identical to the unpadded case, which is the point: padding must not shift the repair.
        assertEquals(0.098f, rect.left, 0.004f)
        assertEquals(0.228f, rect.top, 0.004f)
        assertEquals(0.202f, rect.right, 0.004f)
        assertEquals(0.322f, rect.bottom, 0.004f)
    }

    @Test
    fun skipsAdditionsAlreadyCovered() {
        val existing = listOf(NormRect(0.05f, 0.2f, 0.25f, 0.35f))
        val plan = AutoFixPlanner.plan(
            leftovers = listOf(NormRect(0.1f, 0.2f, 0.2f, 0.3f)),
            content = IntRect(0, 0, 1000, 1800),
            crop = crop,
            sourceWidth = source.first,
            sourceHeight = source.second,
            exportedWidth = 1000,
            exportedHeight = 1800,
            existing = existing,
        )
        assertTrue("a region inside an existing redaction must not be added twice", plan.isEmpty())
    }

    @Test
    fun collapsesDuplicateLeftovers() {
        val leftover = NormRect(0.1f, 0.2f, 0.2f, 0.3f)
        val plan = AutoFixPlanner.plan(
            leftovers = listOf(leftover, leftover),
            content = IntRect(0, 0, 1000, 1800),
            crop = crop,
            sourceWidth = source.first,
            sourceHeight = source.second,
            exportedWidth = 1000,
            exportedHeight = 1800,
        )
        assertEquals(1, plan.size)
    }

    @Test
    fun returnsNothingForDegenerateInput() {
        assertEquals(
            emptyList<NormRect>(),
            AutoFixPlanner.plan(
                leftovers = listOf(NormRect(0.1f, 0.1f, 0.2f, 0.2f)),
                content = IntRect(0, 0, 0, 0),
                crop = IntRect(0, 0, 0, 0),
                sourceWidth = 0,
                sourceHeight = 0,
                exportedWidth = 0,
                exportedHeight = 0,
            ),
        )
    }
}
