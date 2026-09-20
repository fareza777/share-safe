package com.sharesafe.app.ui

import android.content.Context
import android.content.res.Configuration
import com.sharesafe.app.core.ads.findActivity
import com.sharesafe.app.data.AppLanguage
import java.util.Locale

/**
 * In-app language selection, without pulling in AppCompat: the Activity is wrapped with the chosen
 * locale in `attachBaseContext`, which is the earliest point where the resources are still ours to
 * configure. Because that runs before `onCreate`, every `stringResource` and every
 * `DateTimeFormatter.ofLocalized*` call in the tree picks the same language - the calendar and the
 * time stamps in History included.
 *
 * The alternative, `LocaleManager` (API 33+), would only work on a third of devices and would put
 * the setting somewhere other than the app.
 */
object LocaleSupport {

    /** Wraps [base] with the locale of [language]; "device language" is returned untouched. */
    fun wrap(base: Context, language: AppLanguage): Context {
        val tag = language.tag ?: return base
        val locale = Locale.forLanguageTag(tag)
        // Keeps java.time formatters, which ask Locale.getDefault() rather than the Context,
        // in step with the choice made in Settings.
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    /** `recreate()` re-runs `attachBaseContext`, so this is all a language change needs. */
    fun recreate(context: Context) {
        context.findActivity()?.recreate()
    }
}
