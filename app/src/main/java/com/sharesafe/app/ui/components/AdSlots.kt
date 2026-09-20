package com.sharesafe.app.ui.components

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.sharesafe.app.core.ads.AdsConfig
import com.sharesafe.app.core.ads.findActivity
import com.sharesafe.app.ui.TestTags

/**
 * True while ads should be on screen. A rewarded video buys 24 hours of silence, and that window
 * is stored as an absolute timestamp, so it also survives a restart and a clock that keeps moving.
 */
@Composable
fun rememberAdsVisible(): Boolean {
    val until by com.sharesafe.app.data.SettingsStore.instance.adFreeUntil.collectAsState()
    return until <= System.currentTimeMillis()
}

/**
 * Banner slot. Only ever placed on chrome screens — Home, History, Settings, About — and never on
 * the editor or the preview, because those are the two screens that show the user's own pixels.
 *
 * The view is remembered rather than recreated per composition, and destroyed on dispose: an
 * AdView that is merely dropped leaks its loaders and keeps polling.
 */
@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    if (!AdsConfig.ENABLED || !visible) return
    val context = LocalContext.current
    val adView = remember { newBanner(context) }
    LaunchedEffect(adView) { runCatching { adView.loadAd(AdRequest.Builder().build()) } }
    DisposableEffect(adView) { onDispose { runCatching { adView.destroy() } } }
    // The tag lives on a Compose box rather than on the AndroidView itself, so the instrumented
    // suite can assert where ads are *missing* without depending on how view interop publishes
    // semantics.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.AD_BANNER),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
    }
}

private fun newBanner(context: Context): AdView = AdView(context).apply {
    adUnitId = AdsConfig.BANNER_UNIT_ID
    setAdSize(AdSize.BANNER)
}

/**
 * Interstitial that preloads once and then hands the caller a `show` lambda. The callback always
 * fires — successfully shown, failed to show, or no inventory at all — so an ad can never be the
 * reason a share silently stops working.
 */
class InterstitialSlot internal constructor(private val context: Context) {

    private var pending: InterstitialAd? = null

    internal fun preload() {
        if (!AdsConfig.ENABLED || pending != null) return
        runCatching {
            InterstitialAd.load(
                context,
                AdsConfig.INTERSTITIAL_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        pending = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        pending = null
                    }
                },
            )
        }
    }

    /** True when something is actually ready, so the caller can skip work when it is not. */
    val isReady: Boolean get() = pending != null

    fun show(onFinished: () -> Unit) {
        val ad = pending
        val activity: Activity? = context.findActivity()
        if (ad == null || activity == null) {
            onFinished()
            return
        }
        pending = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onFinished()
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                onFinished()
                preload()
            }
        }
        runCatching { ad.show(activity) }.onFailure {
            onFinished()
            preload()
        }
    }
}

@Composable
fun rememberInterstitialSlot(): InterstitialSlot {
    val context = LocalContext.current
    val slot = remember { InterstitialSlot(context) }
    LaunchedEffect(slot) { slot.preload() }
    return slot
}

/**
 * Rewarded video. The reward here is purely additive — watching it buys 24 hours without banners or
 * interstitials. No safety feature is ever behind it, and nothing is taken away when it is skipped.
 */
class RewardedSlot internal constructor(private val context: Context) {

    private var pending: RewardedAd? = null

    internal fun preload() {
        if (!AdsConfig.ENABLED || pending != null) return
        runCatching {
            RewardedAd.load(
                context,
                AdsConfig.REWARDED_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        pending = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        pending = null
                    }
                },
            )
        }
    }

    val isReady: Boolean get() = pending != null

    /**
     * `onReward` fires only when the SDK reports the reward was earned; `onFinished` fires in every
     * case so the UI never keeps a spinner forever.
     */
    fun show(onReward: () -> Unit, onFinished: () -> Unit) {
        val ad = pending
        val activity: Activity? = context.findActivity()
        if (ad == null || activity == null) {
            onFinished()
            return
        }
        pending = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onFinished()
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                onFinished()
                preload()
            }
        }
        runCatching {
            ad.show(activity) { onReward() }
        }.onFailure {
            onFinished()
            preload()
        }
    }
}

@Composable
fun rememberRewardedSlot(): RewardedSlot {
    val context = LocalContext.current
    val slot = remember { RewardedSlot(context) }
    LaunchedEffect(slot) { slot.preload() }
    return slot
}
