package com.sharesafe.app.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sharesafe.app.R
import com.sharesafe.app.core.ads.AD_FREE_WINDOW_MILLIS
import com.sharesafe.app.core.export.ExportFormat
import com.sharesafe.app.core.export.ExportOptions
import com.sharesafe.app.data.AppLanguage
import com.sharesafe.app.data.HistoryStore
import com.sharesafe.app.data.SettingsStore
import com.sharesafe.app.data.ThemeMode
import com.sharesafe.app.ui.LocaleSupport
import com.sharesafe.app.ui.TestTags
import com.sharesafe.app.ui.components.AdBanner
import com.sharesafe.app.ui.components.InfoRow
import com.sharesafe.app.ui.components.SelectChip
import com.sharesafe.app.ui.components.SwitchRow
import com.sharesafe.app.ui.components.rememberAdsVisible
import com.sharesafe.app.ui.components.rememberRewardedSlot
import com.sharesafe.app.ui.theme.MintSecondary
import com.sharesafe.app.ui.theme.ThemePalette
import com.sharesafe.app.ui.theme.VioletPrimary
import com.sharesafe.app.ui.theme.lightColors
import com.sharesafe.app.ui.toastOnMain
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Settings as a stack of top-down cards: each card is closed by default and expands to reveal its
 * contents, so the whole screen is scannable in one glance and only one thing is being edited at a
 * time. The summary line on a closed card carries the answer the user came for (the current theme,
 * how many history entries exist), which is what makes collapsing the details an improvement rather
 * than a hiding place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAbout: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    animations: Boolean = true,
) {
    val context = LocalContext.current
    val settings = SettingsStore.instance
    val adsVisible = rememberAdsVisible()

    val themeMode by settings.themeMode.collectAsState()
    val palette by settings.palette.collectAsState()
    val dynamicColor by settings.dynamicColor.collectAsState()
    val motionEnabled by settings.animations.collectAsState()
    val haptics by settings.haptics.collectAsState()
    val language by settings.language.collectAsState()
    val detectFaces by settings.detectFaces.collectAsState()
    val longNumbers by settings.longNumbers.collectAsState()
    val extraHeuristics by settings.extraHeuristics.collectAsState()
    val autoScan by settings.autoScan.collectAsState()
    val autoSelectAll by settings.autoSelectAll.collectAsState()
    val autoFix by settings.autoFixLeftovers.collectAsState()
    val autoVerify by settings.autoVerify.collectAsState()
    val autoSave by settings.autoSave.collectAsState()
    val keepHistory by settings.keepHistory.collectAsState()
    val exportFormat by settings.exportFormat.collectAsState()
    val exportSize by settings.exportSize.collectAsState()
    val jpegQuality by settings.jpegQuality.collectAsState()
    val adFreeUntil by settings.adFreeUntil.collectAsState()

    val historyEntries by HistoryStore.instance.entries.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    // Appearance opens first: it is the card people come to Settings for, and it makes the collapsed
    // state of the rest read as "there is more below" rather than "nothing here".
    var openSection by rememberSaveable { mutableStateOf(SettingsSection.APPEARANCE.id) }

    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    fun toggle(section: SettingsSection) {
        openSection = if (openSection == section.id) EMPTY_SECTION else section.id
    }

    Column(modifier.fillMaxSize().testTag(TestTags.SETTINGS_SCREEN)) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.settings_title))
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 14.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection.entries.forEach { section ->
                SettingCard(
                    section = section,
                    open = openSection == section.id,
                    animations = animations,
                    summary = when (section) {
                        SettingsSection.APPEARANCE -> stringResource(R.string.settings_appearance_summary)
                        SettingsSection.AUTOMATION -> stringResource(R.string.settings_automation_summary)
                        SettingsSection.DETECTION -> stringResource(R.string.settings_detection_summary)
                        SettingsSection.EXPORT -> stringResource(R.string.settings_export_summary)
                        SettingsSection.HISTORY -> stringResource(
                            R.string.settings_history_summary,
                            historyEntries.size,
                        )
                        SettingsSection.ADS -> stringResource(R.string.settings_ads_summary)
                        SettingsSection.PRIVACY -> stringResource(R.string.settings_privacy_summary)
                        SettingsSection.ABOUT -> stringResource(R.string.settings_about_summary, version)
                    },
                    onToggle = { toggle(section) },
                ) {
                    when (section) {
                        SettingsSection.APPEARANCE -> AppearanceSection(
                            themeMode = themeMode,
                            palette = palette,
                            dynamicColor = dynamicColor,
                            motionEnabled = motionEnabled,
                            haptics = haptics,
                            language = language,
                            onTheme = { settings.setThemeMode(it) },
                            onPalette = {
                                settings.setPalette(it)
                                settings.setDynamicColor(false)
                            },
                            onDynamicColor = { settings.setDynamicColor(it) },
                            onAnimations = { settings.setAnimations(it) },
                            onHaptics = { settings.setHaptics(it) },
                            onLanguage = { chosen ->
                                settings.setLanguage(chosen)
                                // Rebuilds the Activity context with the new locale, which is
                                // where LocaleSupport.wrap() gets its next turn.
                                LocaleSupport.recreate(context)
                            },
                        )

                        SettingsSection.AUTOMATION -> AutomationSection(
                            autoScan = autoScan,
                            autoSelectAll = autoSelectAll,
                            autoVerify = autoVerify,
                            autoFix = autoFix,
                            autoSave = autoSave,
                            onAutoScan = { settings.setAutoScan(it) },
                            onAutoSelectAll = { settings.setAutoSelectAll(it) },
                            onAutoVerify = { settings.setAutoVerify(it) },
                            onAutoFix = { settings.setAutoFixLeftovers(it) },
                            onAutoSave = { settings.setAutoSave(it) },
                        )

                        SettingsSection.DETECTION -> DetectionSection(
                            detectFaces = detectFaces,
                            longNumbers = longNumbers,
                            extraHeuristics = extraHeuristics,
                            onDetectFaces = { settings.setDetectFaces(it) },
                            onLongNumbers = { settings.setLongNumbers(it) },
                            onExtraHeuristics = { settings.setExtraHeuristics(it) },
                        )

                        SettingsSection.EXPORT -> ExportSection(
                            format = exportFormat,
                            size = exportSize,
                            quality = jpegQuality,
                            onFormat = { settings.setExportFormat(it) },
                            onSize = { settings.setExportSize(it) },
                            onQuality = { settings.setJpegQuality(it) },
                        )

                        SettingsSection.HISTORY -> HistorySection(
                            keepHistory = keepHistory,
                            count = historyEntries.size,
                            onKeepHistory = { settings.setKeepHistory(it) },
                            onOpenHistory = onOpenHistory,
                            onClear = { confirmClear = true },
                        )

                        SettingsSection.ADS -> AdsSection(
                            adFreeUntil = adFreeUntil,
                            adsVisible = adsVisible,
                        )

                        SettingsSection.PRIVACY -> PrivacySection()

                        SettingsSection.ABOUT -> AboutSection(
                            version = version,
                            onOpenAbout = onOpenAbout,
                        )
                    }
                }
            }

            AdBanner(visible = adsVisible)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    HistoryStore.instance.clear()
                }) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private const val EMPTY_SECTION = ""

/** Ordered list of the cards, in the order a user actually meets them. */
private enum class SettingsSection(val id: String, val icon: ImageVector, val titleRes: Int) {
    APPEARANCE("appearance", Icons.Rounded.Palette, R.string.settings_appearance),
    AUTOMATION("automation", Icons.Rounded.Bolt, R.string.settings_automation),
    DETECTION("detection", Icons.Rounded.Shield, R.string.settings_detection),
    EXPORT("export", Icons.Rounded.Tune, R.string.settings_export),
    HISTORY("history", Icons.Rounded.History, R.string.settings_history),
    ADS("ads", Icons.Rounded.SmartDisplay, R.string.settings_ads),
    PRIVACY("privacy", Icons.Rounded.Lock, R.string.settings_privacy),
    ABOUT("about", Icons.Rounded.Info, R.string.settings_about),
}

/**
 * One collapsible card. The header is the tap target; the chevron rotates instead of swapping
 * glyphs so the state change reads as motion rather than as a different icon.
 */
@Composable
private fun SettingCard(
    section: SettingsSection,
    open: Boolean,
    animations: Boolean,
    summary: String,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(if (animations) 240 else 0),
        label = "chevron",
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.settingsSection(section.id))
                    .clickable(onClick = onToggle)
                    .padding(vertical = 12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            section.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(section.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (open) R.string.settings_collapse else R.string.settings_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }

            AnimatedVisibility(
                visible = open,
                enter = if (animations) expandVertically(tween(240)) + fadeIn(tween(200)) else
                    androidx.compose.animation.EnterTransition.None,
                exit = if (animations) shrinkVertically(tween(200)) + fadeOut(tween(140)) else
                    androidx.compose.animation.ExitTransition.None,
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    palette: ThemePalette,
    dynamicColor: Boolean,
    motionEnabled: Boolean,
    haptics: Boolean,
    language: AppLanguage,
    onTheme: (ThemeMode) -> Unit,
    onPalette: (ThemePalette) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
) {
    Label(stringResource(R.string.settings_theme))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectChip(
            label = stringResource(R.string.theme_system),
            selected = themeMode == ThemeMode.SYSTEM,
            icon = Icons.Rounded.BrightnessAuto,
            onClick = { onTheme(ThemeMode.SYSTEM) },
        )
        SelectChip(
            label = stringResource(R.string.theme_light),
            selected = themeMode == ThemeMode.LIGHT,
            icon = Icons.Rounded.LightMode,
            onClick = { onTheme(ThemeMode.LIGHT) },
        )
        SelectChip(
            label = stringResource(R.string.theme_dark),
            selected = themeMode == ThemeMode.DARK,
            icon = Icons.Rounded.DarkMode,
            onClick = { onTheme(ThemeMode.DARK) },
        )
    }

    Spacer(Modifier.height(10.dp))
    Label(stringResource(R.string.settings_palette))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag(TestTags.SETTINGS_PALETTE),
    ) {
        items(ThemePalette.entries.toList(), key = { it.id }) { entry ->
            PaletteSwatch(
                entry = entry,
                selected = entry == palette && !dynamicColor,
                modifier = Modifier.testTag("${TestTags.SETTINGS_PALETTE}-${entry.id}"),
                onClick = { onPalette(entry) },
            )
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchRow(
            icon = Icons.Rounded.Palette,
            title = stringResource(R.string.settings_dynamic_color),
            body = stringResource(R.string.settings_dynamic_color_body),
            checked = dynamicColor,
            onCheckedChange = onDynamicColor,
        )
    }

    SwitchRow(
        icon = Icons.Rounded.MotionPhotosOn,
        title = stringResource(R.string.settings_animations),
        body = stringResource(R.string.settings_animations_body),
        checked = motionEnabled,
        onCheckedChange = onAnimations,
    )
    SwitchRow(
        icon = Icons.Rounded.Vibration,
        title = stringResource(R.string.settings_haptics),
        body = stringResource(R.string.settings_haptics_body),
        checked = haptics,
        onCheckedChange = onHaptics,
    )

    Spacer(Modifier.height(10.dp))
    Label(stringResource(R.string.settings_language))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(TestTags.SETTINGS_LANGUAGE),
    ) {
        SelectChip(
            label = stringResource(R.string.language_english),
            selected = language == AppLanguage.ENGLISH,
            icon = Icons.Rounded.Language,
            onClick = { onLanguage(AppLanguage.ENGLISH) },
        )
        SelectChip(
            label = stringResource(R.string.language_indonesian),
            selected = language == AppLanguage.INDONESIAN,
            onClick = { onLanguage(AppLanguage.INDONESIAN) },
        )
        SelectChip(
            label = stringResource(R.string.language_system),
            selected = language == AppLanguage.SYSTEM,
            onClick = { onLanguage(AppLanguage.SYSTEM) },
        )
    }
    Text(
        text = stringResource(R.string.settings_language_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun AutomationSection(
    autoScan: Boolean,
    autoSelectAll: Boolean,
    autoVerify: Boolean,
    autoFix: Boolean,
    autoSave: Boolean,
    onAutoScan: (Boolean) -> Unit,
    onAutoSelectAll: (Boolean) -> Unit,
    onAutoVerify: (Boolean) -> Unit,
    onAutoFix: (Boolean) -> Unit,
    onAutoSave: (Boolean) -> Unit,
) {
    SwitchRow(
        icon = Icons.Rounded.Bolt,
        title = stringResource(R.string.settings_auto_scan),
        body = stringResource(R.string.settings_auto_scan_body),
        checked = autoScan,
        onCheckedChange = onAutoScan,
    )
    SwitchRow(
        icon = Icons.Rounded.AutoFixHigh,
        title = stringResource(R.string.settings_auto_select),
        body = stringResource(R.string.settings_auto_select_body),
        checked = autoSelectAll,
        onCheckedChange = onAutoSelectAll,
    )
    SwitchRow(
        icon = Icons.Rounded.Verified,
        title = stringResource(R.string.settings_auto_verify),
        body = stringResource(R.string.settings_auto_verify_body),
        checked = autoVerify,
        onCheckedChange = onAutoVerify,
    )
    SwitchRow(
        icon = Icons.Rounded.Shield,
        title = stringResource(R.string.settings_auto_fix),
        body = stringResource(R.string.settings_auto_fix_body),
        checked = autoFix,
        onCheckedChange = onAutoFix,
    )
    SwitchRow(
        icon = Icons.Rounded.SaveAlt,
        title = stringResource(R.string.settings_auto_save),
        body = stringResource(R.string.settings_auto_save_body),
        checked = autoSave,
        onCheckedChange = onAutoSave,
    )
}

@Composable
private fun DetectionSection(
    detectFaces: Boolean,
    longNumbers: Boolean,
    extraHeuristics: Boolean,
    onDetectFaces: (Boolean) -> Unit,
    onLongNumbers: (Boolean) -> Unit,
    onExtraHeuristics: (Boolean) -> Unit,
) {
    SwitchRow(
        icon = Icons.Rounded.Face,
        title = stringResource(R.string.settings_detect_faces),
        body = stringResource(R.string.settings_detect_faces_body),
        checked = detectFaces,
        onCheckedChange = onDetectFaces,
    )
    SwitchRow(
        icon = Icons.Rounded.Numbers,
        title = stringResource(R.string.settings_long_numbers),
        body = stringResource(R.string.settings_long_numbers_body),
        checked = longNumbers,
        onCheckedChange = onLongNumbers,
    )
    SwitchRow(
        icon = Icons.Rounded.Router,
        title = stringResource(R.string.settings_extra_heuristics),
        body = stringResource(R.string.settings_extra_heuristics_body),
        checked = extraHeuristics,
        onCheckedChange = onExtraHeuristics,
    )
}

@Composable
private fun ExportSection(
    format: ExportFormat,
    size: Int,
    quality: Int,
    onFormat: (ExportFormat) -> Unit,
    onSize: (Int) -> Unit,
    onQuality: (Int) -> Unit,
) {
    Label(stringResource(R.string.settings_export_format))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportFormat.entries.forEach { entry ->
            SelectChip(
                label = entry.name,
                selected = format == entry,
                onClick = { onFormat(entry) },
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Label(stringResource(R.string.settings_export_size))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportOptions.SIZE_CHOICES.forEach { choice ->
            SelectChip(
                label = sizeLabel(choice),
                selected = size == choice,
                onClick = { onSize(choice) },
            )
        }
    }
    if (format == ExportFormat.JPEG) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_jpeg_quality),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(104.dp),
            )
            Slider(
                value = quality.toFloat(),
                onValueChange = { onQuality(it.toInt()) },
                valueRange = 60f..100f,
                modifier = Modifier.weight(1f),
            )
            Text(text = "$quality", style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(
        text = stringResource(R.string.settings_export_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun HistorySection(
    keepHistory: Boolean,
    count: Int,
    onKeepHistory: (Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onClear: () -> Unit,
) {
    SwitchRow(
        icon = Icons.Rounded.History,
        title = stringResource(R.string.settings_keep_history),
        body = stringResource(R.string.settings_keep_history_body),
        checked = keepHistory,
        onCheckedChange = onKeepHistory,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenHistory)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_open_history),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_history_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(
        onClick = onClear,
        enabled = count > 0,
        modifier = Modifier.testTag(TestTags.SETTINGS_HISTORY_CLEAR),
    ) {
        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.history_clear))
    }
}

/**
 * The ads card. It exists to be honest about the trade: the app is free because of the two formats
 * that do not interrupt a redaction, and one optional video removes them for a day. No redaction
 * capability is behind this card.
 */
@Composable
private fun AdsSection(adFreeUntil: Long, adsVisible: Boolean) {
    val context = LocalContext.current
    val rewarded = rememberRewardedSlot()
    var busy by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.settings_ads_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))

    if (!adsVisible) {
        val until = remember(adFreeUntil) {
            runCatching {
                Instant.ofEpochMilli(adFreeUntil)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            }.getOrDefault("")
        }
        InfoRow(
            icon = Icons.Rounded.Verified,
            title = stringResource(R.string.settings_ads_free_until, until),
            body = stringResource(R.string.settings_ads_remove_body),
            tint = MaterialTheme.colorScheme.secondary,
        )
        return
    }

    androidx.compose.material3.Button(
        onClick = {
            if (!rewarded.isReady) {
                context.toastOnMain(context.getString(R.string.settings_ads_unavailable))
                return@Button
            }
            busy = true
            rewarded.show(
                onReward = {
                    SettingsStore.instance.grantAdFree(
                        System.currentTimeMillis() + AD_FREE_WINDOW_MILLIS,
                    )
                },
                onFinished = { busy = false },
            )
        },
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag(TestTags.SETTINGS_ADS_REWARD),
    ) {
        if (busy) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.settings_ads_loading))
        } else {
            Icon(Icons.Rounded.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_ads_remove))
        }
    }
    Text(
        text = stringResource(R.string.settings_ads_remove_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun PrivacySection() {
    InfoRow(
        icon = Icons.Rounded.Lock,
        title = stringResource(R.string.home_privacy_scan_title),
        body = stringResource(R.string.settings_on_device_body),
        tint = VioletPrimary,
    )
    InfoRow(
        icon = Icons.Rounded.WifiOff,
        title = stringResource(R.string.home_privacy_offline_title),
        body = stringResource(R.string.settings_no_internet_body),
        tint = MintSecondary,
    )
    InfoRow(
        icon = Icons.Rounded.Image,
        title = stringResource(R.string.home_privacy_export_title),
        body = stringResource(R.string.settings_permissions_body),
        tint = MaterialTheme.colorScheme.tertiary,
    )
    InfoRow(
        icon = Icons.Rounded.SmartDisplay,
        title = stringResource(R.string.home_privacy_ads_title),
        body = stringResource(R.string.home_privacy_ads_body),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AboutSection(version: String, onOpenAbout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAbout)
            .padding(vertical = 10.dp)
            .testTag(TestTags.SETTINGS_ABOUT_ENTRY),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_version, version),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** A round swatch of the palette's three accent colours, which is the whole identity of the theme. */
@Composable
private fun PaletteSwatch(
    entry: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = remember(entry) {
        val accent = entry.lightColors()
        listOf(accent.primary, accent.secondary, accent.tertiary)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors)),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.22f)),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = paletteLabel(entry),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun paletteLabel(palette: ThemePalette): String = stringResource(
    when (palette) {
        ThemePalette.VIOLET -> R.string.palette_violet
        ThemePalette.OCEAN -> R.string.palette_ocean
        ThemePalette.SUNSET -> R.string.palette_sunset
        ThemePalette.FOREST -> R.string.palette_forest
        ThemePalette.MONO -> R.string.palette_mono
    },
)

@Composable
private fun sizeLabel(maxLongEdge: Int): String = stringResource(
    when (maxLongEdge) {
        1080 -> R.string.preview_size_1080
        720 -> R.string.preview_size_720
        else -> R.string.preview_size_original
    },
)
