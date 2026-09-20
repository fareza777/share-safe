package com.sharesafe.app.data

import android.content.Context
import android.content.SharedPreferences
import com.sharesafe.app.core.export.ExportFormat
import com.sharesafe.app.core.export.ExportOptions
import com.sharesafe.app.ui.theme.ThemePalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Theme selection, persisted in a private preferences file. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * UI language, applied by overriding the Activity's configuration.
 *
 * English is the default on purpose: the app is written in English, the screenshots people redact
 * are usually in English, and a device set to Indonesian used to receive half-translated copy the
 * moment a new string landed. Choosing "Device language" is still one tap away.
 */
enum class AppLanguage(val tag: String?) {
    ENGLISH("en"),
    INDONESIAN("in"),
    SYSTEM(null);

    companion object {
        val DEFAULT = ENGLISH

        fun fromTag(tag: String?): AppLanguage = when (tag) {
            ENGLISH.tag -> ENGLISH
            INDONESIAN.tag -> INDONESIAN
            else -> SYSTEM
        }
    }
}

/**
 * App-wide preferences: appearance, how automatic the pipeline is, and what an export looks like.
 * Settings are intentionally tiny so a SharedPreferences file is enough — no DataStore dependency,
 * no data leaving the device.
 *
 * The three `auto*` flags are what make the app work without manual editing: a screenshot is
 * scanned on open, every hit starts switched on, and anything the verifier still finds after
 * rendering is repaired and re-verified automatically before the share sheet is offered.
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _palette = MutableStateFlow(ThemePalette.fromId(prefs.getString(KEY_PALETTE, null)))
    val palette: StateFlow<ThemePalette> = _palette.asStateFlow()

    /** Material You: follow the wallpaper palette on Android 12+. */
    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    /** Screen transitions, list animations and the splash flourish. */
    private val _animations = MutableStateFlow(prefs.getBoolean(KEY_ANIMATIONS, true))
    val animations: StateFlow<Boolean> = _animations.asStateFlow()

    private val _haptics = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    private val _lastStyle = MutableStateFlow(prefs.getString(KEY_STYLE, null))
    val lastStyle: StateFlow<String?> = _lastStyle.asStateFlow()

    private val _lastStrength = MutableStateFlow(prefs.getFloat(KEY_STRENGTH, DEFAULT_STRENGTH))
    val lastStrength: StateFlow<Float> = _lastStrength.asStateFlow()

    /** Face detection used to always run; now the user decides how aggressive the scan is. */
    private val _detectFaces = MutableStateFlow(prefs.getBoolean(KEY_DETECT_FACES, true))
    val detectFaces: StateFlow<Boolean> = _detectFaces.asStateFlow()

    /** Opt-in: also flag any long digit run (tracking numbers included). */
    private val _longNumbers = MutableStateFlow(prefs.getBoolean(KEY_LONG_NUMBERS, false))
    val longNumbers: StateFlow<Boolean> = _longNumbers.asStateFlow()

    /** Network addresses and licence plates are only worth flagging on request. */
    private val _extraHeuristics = MutableStateFlow(prefs.getBoolean(KEY_EXTRA_HEURISTICS, true))
    val extraHeuristics: StateFlow<Boolean> = _extraHeuristics.asStateFlow()

    /** Scan as soon as an image is opened — the app's whole premise is that this is automatic. */
    private val _autoScan = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SCAN, true))
    val autoScan: StateFlow<Boolean> = _autoScan.asStateFlow()

    private val _autoSelectAll = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SELECT_ALL, true))
    val autoSelectAll: StateFlow<Boolean> = _autoSelectAll.asStateFlow()

    /** Re-render with extra regions when the verifier still sees something. */
    private val _autoFixLeftovers = MutableStateFlow(prefs.getBoolean(KEY_AUTO_FIX, true))
    val autoFixLeftovers: StateFlow<Boolean> = _autoFixLeftovers.asStateFlow()

    private val _autoVerify = MutableStateFlow(prefs.getBoolean(KEY_AUTO_VERIFY, true))
    val autoVerify: StateFlow<Boolean> = _autoVerify.asStateFlow()

    /** Save a redacted copy next to the original as part of the one-tap flow. */
    private val _autoSave = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SAVE, false))
    val autoSave: StateFlow<Boolean> = _autoSave.asStateFlow()

    private val _keepHistory = MutableStateFlow(prefs.getBoolean(KEY_KEEP_HISTORY, true))
    val keepHistory: StateFlow<Boolean> = _keepHistory.asStateFlow()

    private val _exportFormat =
        MutableStateFlow(ExportFormat.fromId(prefs.getString(KEY_EXPORT_FORMAT, null)))
    val exportFormat: StateFlow<ExportFormat> = _exportFormat.asStateFlow()

    private val _exportSize = MutableStateFlow(prefs.getInt(KEY_EXPORT_SIZE, 0))
    val exportSize: StateFlow<Int> = _exportSize.asStateFlow()

    private val _jpegQuality = MutableStateFlow(prefs.getInt(KEY_JPEG_QUALITY, DEFAULT_QUALITY))
    val jpegQuality: StateFlow<Int> = _jpegQuality.asStateFlow()

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _shareCount = MutableStateFlow(prefs.getInt(KEY_SHARE_COUNT, 0))
    val shareCount: StateFlow<Int> = _shareCount.asStateFlow()

    // Light by default: this tool is used in bright places to check what a screenshot is about to
    // leak, and the exported artwork is designed against a light canvas. Dark and System stay one
    // tap away in Settings.
    private fun readThemeMode(): ThemeMode = when (prefs.getString(KEY_THEME, null)) {
        ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
        ThemeMode.DARK.name -> ThemeMode.DARK
        ThemeMode.LIGHT.name -> ThemeMode.LIGHT
        else -> ThemeMode.LIGHT
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private val _language = MutableStateFlow(
        AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null)).let { stored ->
            // Only an explicitly stored choice is honoured; a missing key means "never chosen",
            // which is English rather than "follow the device".
            if (prefs.contains(KEY_LANGUAGE)) stored else AppLanguage.DEFAULT
        },
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        _language.value = language
    }

    /** Wall-clock millis until which the rewarded video keeps ads hidden. 0 means "showing ads". */
    private val _adFreeUntil = MutableStateFlow(prefs.getLong(KEY_AD_FREE_UNTIL, 0L))
    val adFreeUntil: StateFlow<Long> = _adFreeUntil.asStateFlow()

    private val _lastInterstitialAt = MutableStateFlow(prefs.getLong(KEY_LAST_INTERSTITIAL, 0L))
    val lastInterstitialAt: StateFlow<Long> = _lastInterstitialAt.asStateFlow()

    fun grantAdFree(untilMillis: Long) {
        prefs.edit().putLong(KEY_AD_FREE_UNTIL, untilMillis).apply()
        _adFreeUntil.value = untilMillis
    }

    /** Only called after an interstitial was actually shown, so the cap measures impressions. */
    fun recordInterstitial(shownAtMillis: Long) {
        prefs.edit().putLong(KEY_LAST_INTERSTITIAL, shownAtMillis).apply()
        _lastInterstitialAt.value = shownAtMillis
    }

    /** Convenience for callers that only need to know whether to show an ad right now. */
    fun adsVisible(nowMillis: Long = System.currentTimeMillis()): Boolean =
        _adFreeUntil.value <= nowMillis

    fun setPalette(palette: ThemePalette) {
        prefs.edit().putString(KEY_PALETTE, palette.id).apply()
        _palette.value = palette
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setAnimations(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS, enabled).apply()
        _animations.value = enabled
    }

    fun setHaptics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _haptics.value = enabled
    }

    fun setLastStyle(styleId: String) {
        prefs.edit().putString(KEY_STYLE, styleId).apply()
        _lastStyle.value = styleId
    }

    fun setLastStrength(strength: Float) {
        prefs.edit().putFloat(KEY_STRENGTH, strength).apply()
        _lastStrength.value = strength
    }

    fun setDetectFaces(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DETECT_FACES, enabled).apply()
        _detectFaces.value = enabled
    }

    fun setLongNumbers(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LONG_NUMBERS, enabled).apply()
        _longNumbers.value = enabled
    }

    fun setExtraHeuristics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXTRA_HEURISTICS, enabled).apply()
        _extraHeuristics.value = enabled
    }

    fun setAutoScan(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SCAN, enabled).apply()
        _autoScan.value = enabled
    }

    fun setAutoSelectAll(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SELECT_ALL, enabled).apply()
        _autoSelectAll.value = enabled
    }

    fun setAutoFixLeftovers(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FIX, enabled).apply()
        _autoFixLeftovers.value = enabled
    }

    fun setAutoVerify(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_VERIFY, enabled).apply()
        _autoVerify.value = enabled
    }

    fun setAutoSave(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SAVE, enabled).apply()
        _autoSave.value = enabled
    }

    fun setKeepHistory(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_HISTORY, enabled).apply()
        _keepHistory.value = enabled
    }

    fun setExportFormat(format: ExportFormat) {
        prefs.edit().putString(KEY_EXPORT_FORMAT, format.id).apply()
        _exportFormat.value = format
    }

    fun setExportSize(maxLongEdge: Int) {
        prefs.edit().putInt(KEY_EXPORT_SIZE, maxLongEdge).apply()
        _exportSize.value = maxLongEdge
    }

    fun setJpegQuality(quality: Int) {
        val clamped = quality.coerceIn(MIN_QUALITY, 100)
        prefs.edit().putInt(KEY_JPEG_QUALITY, clamped).apply()
        _jpegQuality.value = clamped
    }

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING, done).apply()
        _onboardingDone.value = done
    }

    /** Called before a rate prompt, so the ask stays rare. */
    fun recordShare() {
        val next = _shareCount.value + 1
        prefs.edit().putInt(KEY_SHARE_COUNT, next).apply()
        _shareCount.value = next
    }

    /** Convenience snapshot for exporters and the batch redactor. */
    fun exportOptions(): ExportOptions = ExportOptions(
        format = _exportFormat.value,
        maxLongEdge = _exportSize.value,
        jpegQuality = _jpegQuality.value,
    )

    companion object {
        private const val PREFS_NAME = "sharesafe_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_AD_FREE_UNTIL = "ad_free_until"
        private const val KEY_LAST_INTERSTITIAL = "last_interstitial_at"
        private const val KEY_PALETTE = "theme_palette"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_ANIMATIONS = "animations"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_STYLE = "redaction_style"
        private const val KEY_STRENGTH = "redaction_strength"
        private const val KEY_DETECT_FACES = "detect_faces"
        private const val KEY_LONG_NUMBERS = "long_numbers"
        private const val KEY_EXTRA_HEURISTICS = "extra_heuristics"
        private const val KEY_AUTO_SCAN = "auto_scan"
        private const val KEY_AUTO_SELECT_ALL = "auto_select_all"
        private const val KEY_AUTO_FIX = "auto_fix"
        private const val KEY_AUTO_VERIFY = "auto_verify"
        private const val KEY_AUTO_SAVE = "auto_save"
        private const val KEY_KEEP_HISTORY = "keep_history"
        private const val KEY_EXPORT_FORMAT = "export_format"
        private const val KEY_EXPORT_SIZE = "export_size"
        private const val KEY_JPEG_QUALITY = "jpeg_quality"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_SHARE_COUNT = "share_count"

        private const val DEFAULT_STRENGTH = 0.75f
        private const val DEFAULT_QUALITY = 92
        private const val MIN_QUALITY = 60

        @Volatile
        private var instanceOrNull: SettingsStore? = null

        val instance: SettingsStore
            get() = checkNotNull(instanceOrNull) { "SettingsStore.attach() was not called" }

        fun attach(context: Context) {
            if (instanceOrNull == null) {
                synchronized(this) {
                    if (instanceOrNull == null) {
                        instanceOrNull = SettingsStore(context)
                    }
                }
            }
        }
    }
}
