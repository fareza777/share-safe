package com.sharesafe.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Motion helpers, all of them cheap and all of them respecting the user's preference: every entry
 * point takes the `enabled` flag that comes from Settings, so turning animations off really does
 * turn them off (durations collapse to zero and nothing springs).
 */

/** Number that rolls to its new value — used by the History and Home stat tiles. */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var target by remember { mutableIntStateOf(value) }
    val animated by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = if (enabled) tween(durationMillis = 650, easing = LinearEasing) else tween(0),
        label = "counter",
    )
    LaunchedEffect(value) { target = value }
    androidx.compose.material3.Text(
        text = animated.toInt().toString(),
        modifier = modifier,
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
    )
}

/**
 * Fades and lifts its content into place, with an optional stagger delay. Used to give the Home and
 * onboarding screens a sense of order instead of everything appearing at once.
 */
@Composable
fun AppearIn(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    index: Int = 0,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val progress = remember { Animatable(if (visible && enabled) 0f else 1f) }
    LaunchedEffect(visible, enabled) {
        if (!enabled) {
            progress.snapTo(1f)
        } else if (visible) {
            delay((index * 55).toLong())
            progress.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        } else {
            progress.snapTo(0f)
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 28f
        },
        content = content,
    )
}

/** A moving highlight used while a scan is running, so waiting for OCR feels alive. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corner: Int = 16,
    base: Color = Color.White.copy(alpha = 0.05f),
    highlight: Color = Color.White.copy(alpha = 0.22f),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-shift",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(base)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(shift * 900f, 0f),
                    end = Offset(shift * 900f + 260f, 260f),
                ),
            )
            .then(if (enabled) Modifier else Modifier.alpha(0f)),
    )
}

/** Softly breathes between two scales; used behind the splash logo and the scanning badge. */
@Composable
fun PulsingBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minScale: Float = 0.96f,
    maxScale: Float = 1.04f,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-scale",
    )
    Box(
        modifier = modifier.scale(if (enabled) scale else 1f),
        content = content,
    )
}

/** Press feedback for the big call-to-action surfaces. */
@Composable
fun rememberPressScale(enabled: Boolean = true): Pair<MutableInteractionSource, Float> {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "press-scale",
    )
    return interaction to scale
}

/** Full-bleed gradient with a slow drift, shared by splash, onboarding and the empty states. */
@Composable
fun DriftingGradient(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "drift")
    val position by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift-position",
    )
    val offset = if (enabled) position else 0.5f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = colors,
                    start = Offset(offset * 700f, 0f),
                    end = Offset(700f - offset * 700f, 1400f),
                ),
            ),
    )
}
