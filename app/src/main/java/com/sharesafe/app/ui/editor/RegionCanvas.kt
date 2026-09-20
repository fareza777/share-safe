package com.sharesafe.app.ui.editor

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sharesafe.app.R
import com.sharesafe.app.core.model.IntRect
import com.sharesafe.app.core.model.NormRect
import com.sharesafe.app.data.SettingsStore
import kotlin.math.max
import kotlin.math.min

/** Projects normalized source-space rectangles onto the canvas, including crop and padding. */
private class CanvasGeometry(
    val scale: Float,
    val bitmapOrigin: Offset,
    val content: Rect,
    val previewScale: Float,
    val sourceWidth: Float,
    val sourceHeight: Float,
    val crop: IntRect,
) {
    fun normToView(rect: NormRect): ComposeRect {
        val left = bitmapOrigin.x + (content.left + (rect.left * sourceWidth - crop.left) * previewScale) * scale
        val top = bitmapOrigin.y + (content.top + (rect.top * sourceHeight - crop.top) * previewScale) * scale
        val right = bitmapOrigin.x + (content.left + (rect.right * sourceWidth - crop.left) * previewScale) * scale
        val bottom = bitmapOrigin.y + (content.top + (rect.bottom * sourceHeight - crop.top) * previewScale) * scale
        return ComposeRect(left, top, right, bottom)
    }

    fun viewToNorm(point: Offset): Offset {
        val previewX = (point.x - bitmapOrigin.x) / scale
        val previewY = (point.y - bitmapOrigin.y) / scale
        val sourceX = (previewX - content.left) / previewScale + crop.left
        val sourceY = (previewY - content.top) / previewScale + crop.top
        return Offset(
            (sourceX / sourceWidth).coerceIn(0f, 1f),
            (sourceY / sourceHeight).coerceIn(0f, 1f),
        )
    }
}

/**
 * The redaction canvas: renders the redacted preview and lets the user toggle detected boxes, draw
 * new ones, move/resize them and pinch-zoom for precision.
 */
@Composable
fun RegionCanvas(
    state: EditorUiState,
    onToggleDetection: (String) -> Unit,
    onSelectManual: (String?) -> Unit,
    onCreateManual: (NormRect) -> Unit,
    onMoveManual: (String, NormRect) -> Unit,
    onBeginInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = state.source
    val preview = state.preview

    if (source == null || preview == null || preview.isRecycled) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (state.scanning) CircularProgressIndicator()
        }
        return
    }

    val content = state.previewContentRect ?: Rect(0, 0, preview.width, preview.height)
    val currentState by rememberUpdatedState(state)
    // A short haptic tick confirms a region flip without having to look away from the canvas.
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by SettingsStore.instance.haptics.collectAsState()
    val toggleWithFeedback: (String) -> Unit = { id ->
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onToggleDetection(id)
    }
    val latestToggle by rememberUpdatedState(toggleWithFeedback)
    val latestSelect by rememberUpdatedState(onSelectManual)
    val latestCreate by rememberUpdatedState(onCreateManual)
    val latestMove by rememberUpdatedState(onMoveManual)
    val latestBegin by rememberUpdatedState(onBeginInteraction)
    val handleRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
    val image = remember(preview) { preview.asImageBitmap() }

    var zoom by remember(preview) { mutableFloatStateOf(1f) }
    var pan by remember(preview) { mutableStateOf(Offset.Zero) }
    var draft by remember(preview) { mutableStateOf<ComposeRect?>(null) }

    BoxWithConstraints(
        modifier = modifier.background(Color.Black.copy(alpha = 0.6f)),
    ) {
        val viewWidth = constraints.maxWidth.toFloat()
        val viewHeight = constraints.maxHeight.toFloat()
        val contentWidth = content.width().toFloat()
        val contentHeight = content.height().toFloat()
        val fit = if (contentWidth > 0f && contentHeight > 0f) {
            min(viewWidth / contentWidth, viewHeight / contentHeight) * 0.96f
        } else {
            1f
        }
        val scale = fit * zoom
        val baseOrigin = Offset(
            (viewWidth - contentWidth * scale) / 2f + pan.x,
            (viewHeight - contentHeight * scale) / 2f + pan.y,
        )
        val geometry = CanvasGeometry(
            scale = scale,
            bitmapOrigin = Offset(
                baseOrigin.x - content.left * scale,
                baseOrigin.y - content.top * scale,
            ),
            content = content,
            previewScale = contentWidth / max(1, state.cropRect.width),
            sourceWidth = source.width.toFloat(),
            sourceHeight = source.height.toFloat(),
            crop = state.cropRect,
        )
        val geometryState by rememberUpdatedState(geometry)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(preview, state.cropRect) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        var target = hitTest(start, currentState, geometryState, handleRadiusPx)
                        var dragging = false
                        var transformed = false
                        var accumulated = Offset.Zero
                        var last = start

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount == 0) break

                            if (pressedCount > 1) {
                                transformed = true
                                draft = null
                                target = GestureTarget.None
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f) zoom = (zoom * zoomChange).coerceIn(0.6f, 8f)
                                if (panChange != Offset.Zero) pan += panChange
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            if (transformed) continue

                            val change = event.changes.first()
                            val position = change.position
                            val delta = position - last
                            last = position
                            accumulated += delta
                            if (!dragging && accumulated.getDistance() > 12f) {
                                dragging = true
                                if (target !is GestureTarget.None) latestBegin()
                            }
                            if (!dragging) continue

                            val geometryNow = geometryState
                            when (val current = target) {
                                is GestureTarget.Move -> {
                                    val region = currentState.manualRegions.firstOrNull { it.id == current.id }
                                    if (region != null) {
                                        val dx = delta.x / (geometryNow.scale * geometryNow.previewScale * geometryNow.sourceWidth)
                                        val dy = delta.y / (geometryNow.scale * geometryNow.previewScale * geometryNow.sourceHeight)
                                        latestMove(current.id, region.bounds.translated(dx, dy))
                                    }
                                    change.consume()
                                }

                                is GestureTarget.ResizeHandle -> {
                                    val region = currentState.manualRegions.firstOrNull { it.id == current.id }
                                    if (region != null) {
                                        val norm = geometryNow.viewToNorm(position)
                                        latestMove(current.id, current.corner.applyTo(region.bounds, norm))
                                    }
                                    change.consume()
                                }

                                GestureTarget.None -> {
                                    draft = rectBetween(start, position)
                                    change.consume()
                                }

                                is GestureTarget.Detection -> change.consume()
                            }
                        }

                        val finishedDraft = draft
                        draft = null
                        when {
                            !dragging && !transformed -> {
                                handleTap(start, currentState, geometryState, latestToggle, latestSelect)
                            }

                            dragging && finishedDraft != null && target is GestureTarget.None -> {
                                val geometryNow = geometryState
                                val first = geometryNow.viewToNorm(Offset(finishedDraft.left, finishedDraft.top))
                                val second = geometryNow.viewToNorm(Offset(finishedDraft.right, finishedDraft.bottom))
                                val norm = NormRect(
                                    min(first.x, second.x),
                                    min(first.y, second.y),
                                    max(first.x, second.x),
                                    max(first.y, second.y),
                                )
                                if (norm.width > 0.01f && norm.height > 0.01f) latestCreate(norm)
                            }
                        }
                    }
                },
        ) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(preview.width, preview.height),
                dstOffset = IntOffset(
                    geometry.bitmapOrigin.x.toInt(),
                    geometry.bitmapOrigin.y.toInt(),
                ),
                dstSize = IntSize(
                    (preview.width * geometry.scale).toInt().coerceAtLeast(1),
                    (preview.height * geometry.scale).toInt().coerceAtLeast(1),
                ),
                filterQuality = if (geometry.scale > 1.6f) FilterQuality.Medium else FilterQuality.Low,
            )

            state.detections.forEach { detection ->
                val rect = geometry.normToView(detection.bounds)
                val color = kindColor(detection.kind)
                if (state.isDetectionActive(detection)) {
                    drawRect(color = color.copy(alpha = 0.24f), topLeft = rect.topLeft, size = rect.size)
                    drawRect(
                        color = color,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                } else {
                    drawRect(
                        color = color.copy(alpha = 0.4f),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                        ),
                    )
                }
            }

            state.manualRegions.forEach { region ->
                val rect = geometry.normToView(region.bounds)
                val selected = region.id == state.selectedManualId
                drawRect(
                    color = Color.White.copy(alpha = if (selected) 0.18f else 0.10f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                drawRect(
                    color = Color.White,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = if (selected) 2.5.dp.toPx() else 1.5.dp.toPx()),
                )
                if (selected) drawHandles(rect)
            }

            draft?.let { draftRect ->
                drawRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = draftRect.topLeft,
                    size = draftRect.size,
                )
                drawRect(
                    color = Color.White,
                    topLeft = draftRect.topLeft,
                    size = draftRect.size,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZoomButton(Icons.Rounded.Add, stringResource(R.string.editor_zoom_in)) {
                zoom = (zoom * 1.4f).coerceAtMost(8f)
            }
            ZoomButton(Icons.Rounded.Remove, stringResource(R.string.editor_zoom_out)) {
                zoom = (zoom / 1.4f).coerceAtLeast(0.6f)
            }
            ZoomButton(Icons.Rounded.CenterFocusStrong, stringResource(R.string.editor_zoom_fit)) {
                zoom = 1f
                pan = Offset.Zero
            }
        }
    }
}

private fun DrawScope.drawHandles(rect: ComposeRect) {
    val radius = 7.dp.toPx()
    listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).forEach { corner ->
        drawCircle(Color.Black.copy(alpha = 0.55f), radius = radius + 1.dp.toPx(), center = corner)
        drawCircle(Color.White, radius = radius, center = corner)
    }
}

private enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private fun Corner.applyTo(bounds: NormRect, point: Offset): NormRect = when (this) {
    Corner.TOP_LEFT -> NormRect(point.x, point.y, bounds.right, bounds.bottom)
    Corner.TOP_RIGHT -> NormRect(bounds.left, point.y, point.x, bounds.bottom)
    Corner.BOTTOM_LEFT -> NormRect(point.x, bounds.top, bounds.right, point.y)
    Corner.BOTTOM_RIGHT -> NormRect(bounds.left, bounds.top, point.x, point.y)
}

private sealed interface GestureTarget {
    data object None : GestureTarget
    data class Move(val id: String) : GestureTarget
    data class ResizeHandle(val id: String, val corner: Corner) : GestureTarget
    data class Detection(val id: String) : GestureTarget
}

private fun rectBetween(start: Offset, end: Offset): ComposeRect = ComposeRect(
    left = min(start.x, end.x),
    top = min(start.y, end.y),
    right = max(start.x, end.x),
    bottom = max(start.y, end.y),
)

/** Nearest corner handle wins, then manual boxes, then the smallest detection box. */
private fun hitTest(
    point: Offset,
    state: EditorUiState,
    geometry: CanvasGeometry,
    handleRadius: Float,
): GestureTarget {
    state.manualRegions.forEach { region ->
        val rect = geometry.normToView(region.bounds)
        val corners = listOf(
            Corner.TOP_LEFT to rect.topLeft,
            Corner.TOP_RIGHT to rect.topRight,
            Corner.BOTTOM_LEFT to rect.bottomLeft,
            Corner.BOTTOM_RIGHT to rect.bottomRight,
        )
        val nearest = corners.minByOrNull { (_, corner) -> (corner - point).getDistance() }
        if (nearest != null && (nearest.second - point).getDistance() <= handleRadius) {
            return GestureTarget.ResizeHandle(region.id, nearest.first)
        }
        if (rect.contains(point)) return GestureTarget.Move(region.id)
    }
    val hit = state.detections
        .map { it to geometry.normToView(it.bounds) }
        .filter { (_, rect) -> rect.contains(point) }
        .minByOrNull { (_, rect) -> rect.width * rect.height }
    return if (hit != null) GestureTarget.Detection(hit.first.id) else GestureTarget.None
}

private fun handleTap(
    point: Offset,
    state: EditorUiState,
    geometry: CanvasGeometry,
    toggleDetection: (String) -> Unit,
    selectManual: (String?) -> Unit,
) {
    when (val target = hitTest(point, state, geometry, handleRadius = 30f)) {
        is GestureTarget.Detection -> toggleDetection(target.id)
        is GestureTarget.Move -> selectManual(target.id)
        is GestureTarget.ResizeHandle -> selectManual(target.id)
        GestureTarget.None -> selectManual(null)
    }
}

@Composable
private fun ZoomButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shadowElevation = 3.dp,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
        }
    }
}
