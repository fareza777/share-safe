package com.sharesafe.app

import com.sharesafe.app.core.ads.AD_FREE_WINDOW_MILLIS
import com.sharesafe.app.core.ads.AdsConfig
import com.sharesafe.app.core.ads.InterstitialPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides when to interrupt someone is the one piece of ad behaviour worth testing,
 * and it is deliberately pure so it can be. Everything else about the SDK is a callback.
 */
class InterstitialPolicyTest {

    @Test
    fun firstShareIsNeverInterrupted() {
        assertFalse(
            InterstitialPolicy.shouldShow(
                shareCount = 1,
                lastShownAtMillis = 0L,
                nowMillis = 10_000_000L,
            ),
        )
    }

    @Test
    fun secondShareMayShowOnceTheIntervalHasPassed() {
        assertTrue(
            InterstitialPolicy.shouldShow(
                shareCount = InterstitialPolicy.MIN_SHARES_BEFORE_FIRST,
                lastShownAtMillis = 0L,
                nowMillis = 10_000_000L,
            ),
        )
    }

    @Test
    fun twoSharesInARowOnlyEverProduceOneImpression() {
        val shownAt = 10_000_000L
        assertFalse(
            InterstitialPolicy.shouldShow(
                shareCount = 9,
                lastShownAtMillis = shownAt,
                nowMillis = shownAt + InterstitialPolicy.MIN_INTERVAL_MILLIS - 1,
            ),
        )
        assertTrue(
            InterstitialPolicy.shouldShow(
                shareCount = 9,
                lastShownAtMillis = shownAt,
                nowMillis = shownAt + InterstitialPolicy.MIN_INTERVAL_MILLIS,
            ),
        )
    }

    @Test
    fun aFreshInstallNeverSeesAnAdOnItsVeryFirstRun() {
        // shareCount starts at zero, so the first two exports are always ad-free.
        assertFalse(InterstitialPolicy.shouldShow(0, 0L, Long.MAX_VALUE / 2))
    }

    @Test
    fun theRewardedWindowIsADay() {
        assertEquals(24L * 60L * 60L * 1000L, AD_FREE_WINDOW_MILLIS)
    }

    /**
     * A real ad unit id is a 16-digit publisher id plus a 10-digit ad unit slot. Shipping a real
     * unit by accident is the one mistake that cannot be undone, so the test ids are pinned here:
     * if someone pastes production values in, the suite fails first.
     */
    @Test
    fun allAdUnitsAreGooglesPublishedTestInventory() {
        val testPublisher = "ca-app-pub-3940256099942544"
        listOf(
            AdsConfig.BANNER_UNIT_ID,
            AdsConfig.INTERSTITIAL_UNIT_ID,
            AdsConfig.REWARDED_UNIT_ID,
        ).forEach { unit ->
            assertTrue("$unit is not a test unit id", unit.startsWith("$testPublisher/"))
        }
    }
}
