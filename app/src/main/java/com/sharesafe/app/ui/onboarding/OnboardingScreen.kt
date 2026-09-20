package com.sharesafe.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sharesafe.app.R
import com.sharesafe.app.ui.TestTags
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val titleRes: Int,
    val bodyRes: Int,
    val icon: ImageVector,
    val accent: Color,
)

private val pages = listOf(
    OnboardingPage(
        titleRes = R.string.onboarding_page_scan_title,
        bodyRes = R.string.onboarding_page_scan_body,
        icon = Icons.Rounded.Lock,
        accent = Color(0xFF4C8DFF),
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_page_redact_title,
        bodyRes = R.string.onboarding_page_redact_body,
        icon = Icons.Rounded.AutoFixHigh,
        accent = Color(0xFF00B894),
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_page_verify_title,
        bodyRes = R.string.onboarding_page_verify_body,
        icon = Icons.Rounded.Shield,
        accent = Color(0xFFFF7A59),
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_page_offline_title,
        bodyRes = R.string.onboarding_page_offline_body,
        icon = Icons.Rounded.WifiOff,
        accent = Color(0xFF9B51E0),
    ),
)

/**
 * Four pages that explain the promise in the order the app works: scan, redact, verify, and never
 * leave the device. Swipeable, skippable, and shown exactly once — unless the user asks for it
 * again from About.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    animations: Boolean = true,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.size - 1

    // The app draws edge to edge, so without these insets the skip button would sit underneath the
    // status bar and the primary button underneath the gesture bar — visible but not tappable.
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onDone,
                modifier = Modifier.testTag(TestTags.ONBOARDING_CTA),
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            OnboardingPageContent(
                page = pages[page],
                animations = animations,
                isActive = pagerState.currentPage == page,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.indices.forEach { index ->
                val selected = index == pagerState.currentPage
                val width by animateFloatAsState(
                    targetValue = if (selected) 22f else 8f,
                    animationSpec = tween(durationMillis = if (animations) 260 else 0),
                    label = "dot",
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            },
                        ),
                )
            }
        }

        Surface(
            onClick = {
                if (lastPage) {
                    onDone()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .testTag(TestTags.ONBOARDING_NEXT),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (lastPage) R.string.onboarding_start else R.string.onboarding_next,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    animations: Boolean,
    isActive: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingArtwork(icon = page.icon, accent = page.accent, animations = animations)
        Spacer(Modifier.height(34.dp))
        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(if (animations) 320 else 0)),
            exit = fadeOut(tween(if (animations) 120 else 0)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(page.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(page.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * One illustration per page, drawn in Compose: a phone that gets scanned, bars that get covered, a
 * shield that confirms, and an offline badge. No bitmap assets, so the onboarding stays sharp and
 * tiny.
 */
@Composable
private fun OnboardingArtwork(
    icon: ImageVector,
    accent: Color,
    animations: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "artwork")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "artwork-progress",
    )
    val phase = if (animations) progress else 0.5f

    Box(
        modifier = Modifier
            .size(210.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.06f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // A phone frame with three text bars: the top two are already hidden, the third is being
        // swept by the scan line.
        Box(
            modifier = Modifier
                .size(width = 108.dp, height = 168.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                repeat(3) { index ->
                    val covered = index < 2
                    val barAlpha by animateFloatAsState(
                        targetValue = if (covered) 1f else 0.35f,
                        animationSpec = tween(if (animations) 700 else 0, delayMillis = index * 140),
                        label = "bar-$index",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index == 1) 0.75f else 0.95f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (covered) {
                                    accent.copy(alpha = 0.55f * barAlpha + 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                },
                            ),
                    )
                }
            }

            // Scan line sweeps down the frame while it is visible.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offsetYFraction(phase)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, accent, Color.Transparent),
                        ),
                    ),
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .size(56.dp)
                .scale(0.94f + phase * 0.08f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** Moves a child down the artwork by a fraction of the parent height. */
private fun Modifier.offsetYFraction(fraction: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val fullHeight = constraints.maxHeight
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(
                x = 0,
                y = (fullHeight * fraction.coerceIn(0f, 1f)).toInt(),
            )
        }
    }
