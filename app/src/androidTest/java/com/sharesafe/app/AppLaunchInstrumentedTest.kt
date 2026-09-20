package com.sharesafe.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Catches startup crashes: application init, theme resolution and the first composition. */
@RunWith(AndroidJUnit4::class)
class AppLaunchInstrumentedTest {

    @Test
    fun mainActivityStartsAndFinishesCleanly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val activity = instrumentation.startActivitySync(intent)
        assertNotNull(activity)
        // Deliberately not waitForIdleSync(): the home screen hosts indeterminate progress
        // animations, so the composition may never report idle. Give the first frames time to
        // render instead and fail the test if the process crashed meanwhile.
        android.os.SystemClock.sleep(2_500)
        instrumentation.runOnMainSync { activity.finish() }
    }
}
