package com.sharesafe.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.sharesafe.app.core.ads.AdsConfig
import com.sharesafe.app.data.HistoryStore
import com.sharesafe.app.data.SettingsStore

class ShareSafeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.attach(this)
        HistoryStore.attach(this)

        // The ads SDK has to be initialised before the first banner is inflated, and it is the one
        // thing at startup that talks to the network. It is asked to run without blocking the first
        // frame; nothing else in this app depends on it, so a failure here is silently ignored.
        if (AdsConfig.ENABLED) {
            runCatching { MobileAds.initialize(this) }
        }
    }
}
