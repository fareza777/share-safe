package com.sharesafe.app

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The privacy promise is only as strong as the manifest, and two SDKs - ML Kit and Mobile Ads - get
 * to add to it transitively. This runs against the *installed* (merged) manifest of the real APK,
 * so a new dependency that quietly asks for more fails here instead of at a Play review.
 *
 * The policy changed with ads: network access is now legitimate (an ad request has to go
 * somewhere), so the test no longer demands its absence. It demands instead that the network
 * permissions are exactly the two the ads SDK needs, that every other declared permission is on a
 * reviewed allow-list, and that nothing capable of reading a user's life is requested at all.
 */
@RunWith(AndroidJUnit4::class)
class PermissionPolicyInstrumentedTest {

    private val declaredPermissions: List<String>
        get() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val info: PackageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS,
            )
            return info.requestedPermissions?.toList().orEmpty()
        }

    @Test
    fun everyDeclaredPermissionHasBeenReviewed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val allowed = buildSet {
            // Advertising, and the only reason this app may use the network.
            add("android.permission.INTERNET")
            add("android.permission.ACCESS_NETWORK_STATE")
            add("com.google.android.gms.permission.AD_ID")
            // Privacy Sandbox permissions the ads SDK declares on Android 14+ (topics, ad
            // attribution and ad id are the three sandbox APIs the SDK participates in).
            add("android.permission.ACCESS_ADSERVICES_ATTRIBUTION")
            add("android.permission.ACCESS_ADSERVICES_AD_ID")
            add("android.permission.ACCESS_ADSERVICES_TOPICS")
            // Footprint of androidx.work 2.7.0, which play-services-ads pulls in transitively. It
            // ships a wakelock, a foreground-service permission and its own services, but the app
            // never enqueues work and the merged manifest declares no foregroundServiceType, so
            // none of it runs. Reviewed and accepted rather than silently inherited.
            add("android.permission.WAKE_LOCK")
            add("android.permission.FOREGROUND_SERVICE")
            // Reading the screenshots the user asks to redact.
            add("android.permission.READ_MEDIA_IMAGES")
            add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
            // Signature-level permission androidx.core declares for its own non-exported receivers.
            add("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
            // Capped with maxSdkVersion="32" in the manifest, so the platform stops listing it on
            // API 33+ - the older gallery picker only ever needed it below that.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add("android.permission.READ_EXTERNAL_STORAGE")
            }
        }
        val undeclared = declaredPermissions.filterNot { it in allowed }
        assertEquals(
            "a new dependency asked for permissions nobody reviewed: $undeclared",
            emptyList<String>(),
            undeclared,
        )
    }

    @Test
    fun networkAccessIsLimitedToTheTwoPermissionsAdsNeed() {
        val network = declaredPermissions.filter {
            it.endsWith(".INTERNET") || it.endsWith(".ACCESS_NETWORK_STATE")
        }
        assertEquals(
            listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE").sorted(),
            network.sorted(),
        )
    }

    /**
     * A permission is only harmless if it is also unused. WorkManager's foreground service is
     * declared but nothing can start it, because no service in the merged manifest requests a
     * foreground service type on a platform that requires one (API 34+).
     */
    @Test
    fun noDeclaredServiceCanRunInTheForeground() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SERVICES,
        )
        val typed = info.services.orEmpty().filter { it.foregroundServiceType != 0 }
        assertTrue("no foreground service type should be declared, found $typed", typed.isEmpty())
    }

    @Test
    fun requestsNothingThatCouldReadTheRestOfThePhone() {
        val forbidden = listOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_CALL_LOG",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.BODY_SENSORS",
            "android.permission.READ_CALENDAR",
        )
        val present = declaredPermissions.filter { it in forbidden }
        assertTrue("ShareSafe must never request $present", present.isEmpty())
    }

    @Test
    fun stillReadsScreenshotsThroughTheGalleryPermissions() {
        val permissions = declaredPermissions
        assertTrue(
            "without photo access the recents strip cannot work; found $permissions",
            permissions.contains("android.permission.READ_MEDIA_IMAGES"),
        )
    }
}
