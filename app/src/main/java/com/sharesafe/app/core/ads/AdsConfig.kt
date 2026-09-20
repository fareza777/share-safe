package com.sharesafe.app.core.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The one place advertising is configured.
 *
 * Everything here is Google's **official test inventory**, which is why the app can ship ads without
 * serving a real impression: the test application id in the manifest pairs with the three test units
 * below, and Google serves house/test creatives with a "Test Ad" label. Replacing the five constants
 * with real values is the entire switch to production — no other file needs to change.
 *
 * What the SDK is and is not allowed to see is a product rule, not a technical one: no bitmap, no
 * OCR string, no file name ever reaches an ad request. The only variable an ad request may carry is
 * the format it was asked for.
 */
object AdsConfig {

    /**
     * Master switch. Turning this off removes every banner, interstitial and rewarded entry point
     * without touching call sites, which is what makes the "no ads" build a one-line change.
     */
    const val ENABLED = true

    // Google's published test unit ids (developers.google.com/admob/android/test-ads).
    const val BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    /**
     * How many images a free Batch Protect run may contain. Selecting the same images again in
     * smaller groups is always possible, so this is a shortcut rather than a wall, and one rewarded
     * video lifts it for 24 hours along with the banners.
     */
    const val FREE_BATCH_LIMIT = 5

    /** Fallback used when a request fails and the UI still has to render something. */
    fun describeFailure(message: String): String = "ad-load-failed: $message"
}

/**
 * Frequency rules for the one format that interrupts: the ads are deliberately rationed so the
 * product still feels like a tool rather than a billboard.
 *
 * Kept as pure arithmetic on purpose — the behaviour that decides when a user is interrupted is
 * exactly the behaviour worth unit-testing, and it cannot be unit-tested if it lives inside an SDK
 * callback.
 */
object InterstitialPolicy {

    /**
     * The first impression waits until sharing is an established habit, not a first-run accident.
     * Three exports also means a batch run, which is the moment the app has demonstrably saved the
     * user real work.
     */
    const val MIN_SHARES_BEFORE_FIRST = 3

    /** At most one interstitial per this window, no matter how many exports happen. */
    const val MIN_INTERVAL_MILLIS = 120_000L

    fun shouldShow(shareCount: Int, lastShownAtMillis: Long, nowMillis: Long): Boolean {
        if (shareCount < MIN_SHARES_BEFORE_FIRST) return false
        return nowMillis - lastShownAtMillis >= MIN_INTERVAL_MILLIS
    }
}

/** How long a rewarded video keeps the app ad-free. */
const val AD_FREE_WINDOW_MILLIS = 24L * 60L * 60L * 1000L

/**
 * `LocalContext` inside an Activity is usually that Activity, but not always — it can be a
 * `ContextWrapper` chain, and the ads SDK needs the real Activity to own the full-screen container.
 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
