package com.sharesafe.app.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import com.sharesafe.app.R
import com.sharesafe.app.ui.components.DriftingGradient
import com.sharesafe.app.ui.components.PulsingBox

/**
 * The first frame the user sees: brand gradient, shield breathing in, the promise spelled out, and
 * a thin track that fills while the app is actually getting ready. Purely decorative — it never
 * blocks work, and it is skippable by simply being short.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    animations: Boolean = true,
) {
    val sweep = remember { Animatable(0f) }

    LaunchedEffect(animations) {
        if (!animations) {
            sweep.snapTo(1f)
            return@LaunchedEffect
        }
        sweep.animateTo(1f, animationSpec = tween(durationMillis = 1500, easing = LinearEasing))
    }

    val logoScale = remember { Animatable(if (animations) 0.72f else 1f) }
    LaunchedEffect(animations) {
        if (animations) {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            )
        }
    }

    Box(modifier.fillMaxSize()) {
        DriftingGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primary,
            ),
            enabled = animations,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PulsingBox(enabled = animations) {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.size(112.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(58.dp)
                                .scale(logoScale.value),
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .alpha(logoScale.value)
                    .graphicsLayer { translationY = (1f - logoScale.value) * 24f },
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier
                    .alpha(logoScale.value)
                    .fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.height(30.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SplashChip(text = stringResource(R.string.splash_chip_local), icon = Icons.Rounded.Lock)
                SplashChip(text = stringResource(R.string.splash_chip_offline), icon = Icons.Rounded.WifiOff)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 56.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(sweep.value.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun SplashChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.16f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}
