package com.sharesafe.app.ui.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sharesafe.app.R
import com.sharesafe.app.core.ads.AdsConfig
import com.sharesafe.app.core.ads.InterstitialPolicy
import com.sharesafe.app.core.export.ExportFormat
import com.sharesafe.app.core.export.ExportOptions
import com.sharesafe.app.core.export.ShareHelper
import com.sharesafe.app.core.export.SharePayload
import com.sharesafe.app.core.verify.VerifyResult
import com.sharesafe.app.data.SettingsStore
import com.sharesafe.app.ui.TestTags
import com.sharesafe.app.ui.components.GradientActionButton
import com.sharesafe.app.ui.components.LoadingPane
import com.sharesafe.app.ui.components.PrimaryButton
import com.sharesafe.app.ui.components.SelectChip
import com.sharesafe.app.ui.components.rememberAdsVisible
import com.sharesafe.app.ui.components.rememberInterstitialSlot
import com.sharesafe.app.ui.editor.EditorViewModel
import com.sharesafe.app.ui.startActivitySafely
import com.sharesafe.app.ui.theme.MintSecondary
import com.sharesafe.app.ui.toast
import com.sharesafe.app.ui.toastOnMain
import kotlinx.coroutines.launch

/** Tells the user the app did the repair pass for them, instead of hiding the fact. */
@Composable
private fun AutoFixNotice(count: Int) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.AutoFixHigh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = pluralStringResource(R.plurals.preview_autofix, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Export preview: the finished file, a hold-to-compare against the original, the verification
 * verdict and the share actions. The primary action stays Safe Share — everything else is optional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    viewModel: EditorViewModel,
    onBackToEdit: () -> Unit,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = onBackToEdit,
    animations: Boolean = true,
) {
    val context = LocalContext.current
    val preview by viewModel.preview.collectAsState()
    val scope = rememberCoroutineScope()
    val settings = SettingsStore.instance
    val whatsapp = remember(context) { ShareHelper.whatsappPackage(context) }
    val telegram = remember(context) { ShareHelper.telegramPackage(context) }
    val exportFormat by settings.exportFormat.collectAsState()
    val exportSize by settings.exportSize.collectAsState()

    var comparing by remember { mutableStateOf(false) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    val autoSave by settings.autoSave.collectAsState()
    var autoSaved by remember { mutableStateOf(false) }

    // The only format allowed to interrupt. It is rationed by a pure policy (see
    // InterstitialPolicy) rather than by a callback buried in the SDK, it never sits over the
    // image - this screen has no banner - and it can never block a share: if there is nothing
    // loaded, or the user bought silence with a rewarded video, the share just goes ahead.
    val interstitial = rememberInterstitialSlot()
    val adsVisible = rememberAdsVisible()

    // "Save a copy anyway" is a default, not a decision the user has to make every time.
    LaunchedEffect(preview.bitmap, autoSave) {
        if (autoSave && preview.bitmap != null && !autoSaved) {
            autoSaved = true
            val saved = viewModel.saveToGallery().getOrNull()
            if (saved != null) context.toast(context.getString(R.string.preview_saved, saved))
        }
    }

    suspend fun share(payload: SharePayload, packageName: String?) {
        val intent = if (packageName != null) {
            ShareHelper.directIntent(context, payload, packageName)
        } else {
            ShareHelper.shareSheetIntent(
                context = context,
                payload = payload,
                title = context.getString(R.string.share_sheet_title),
            )
        }
        context.startActivitySafely(intent) { reason ->
            // startActivitySafely already reports on the main dispatcher, so a Toast is safe here.
            context.toastOnMain(reason)
        }
    }

    suspend fun exportFailed(error: Throwable) {
        context.toast(context.getString(R.string.preview_export_failed, error.message.orEmpty()))
    }

    /**
     * Every share goes through here: export first, then decide whether an ad may take the moment.
     * The export is deliberately finished *before* the ad is considered, so a slow or failed
     * render never costs the user their share.
     */
    fun shareFrom(export: suspend () -> Result<SharePayload>, packageName: String?) {
        scope.launch {
            export()
                .onSuccess { payload ->
                    settings.recordShare()
                    val now = System.currentTimeMillis()
                    val mayShow = AdsConfig.ENABLED && adsVisible && interstitial.isReady &&
                        InterstitialPolicy.shouldShow(
                            shareCount = settings.shareCount.value,
                            lastShownAtMillis = settings.lastInterstitialAt.value,
                            nowMillis = now,
                        )
                    if (mayShow) {
                        settings.recordInterstitial(now)
                        interstitial.show { scope.launch { share(payload, packageName) } }
                    } else {
                        share(payload, packageName)
                    }
                }
                .onFailure { error -> exportFailed(error) }
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.preview_title)) },
            navigationIcon = {
                IconButton(onClick = onBackToEdit) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.preview_back_to_edit),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = preview.bitmap
            val original = preview.original
            val shown = if (comparing && original != null) original else bitmap
            when {
                preview.exporting || bitmap == null -> LoadingPane(text = stringResource(R.string.loading))
                shown == null || shown.isRecycled -> LoadingPane(text = stringResource(R.string.loading))
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TestTags.PREVIEW_COMPARE)
                        .pointerInput(bitmap, original) {
                            detectTapGestures(
                                onPress = {
                                    if (original != null) {
                                        comparing = true
                                        tryAwaitRelease()
                                        comparing = false
                                    }
                                },
                            )
                        },
                ) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = stringResource(R.string.preview_title),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                    )

                    CompareBadge(
                        text = if (comparing) {
                            stringResource(R.string.preview_original)
                        } else {
                            stringResource(R.string.preview_redacted)
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                    )

                    if (original != null && !comparing) {
                        CompareBadge(
                            text = stringResource(R.string.preview_compare_hint),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                        )
                    }
                }
            }
        }

        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VerificationBanner(
                    verifying = preview.verifying,
                    verify = preview.verify,
                    error = preview.error,
                    animations = animations,
                    modifier = Modifier.testTag(TestTags.PREVIEW_VERIFY_BANNER),
                )

                if (preview.autoFixed > 0) {
                    AutoFixNotice(count = preview.autoFixed)
                }

                GradientActionButton(
                    text = stringResource(R.string.preview_safe_share),
                    subtitle = stringResource(R.string.preview_share_other),
                    icon = Icons.Rounded.Share,
                    modifier = Modifier.testTag(TestTags.PREVIEW_SAFE_SHARE),
                    enabled = preview.bitmap != null && !preview.exporting,
                    onClick = { shareFrom({ viewModel.exportForShare() }, null) },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (whatsapp != null) {
                        PrimaryButton(
                            text = stringResource(R.string.preview_send_whatsapp),
                            icon = Icons.AutoMirrored.Rounded.Send,
                            modifier = Modifier.weight(1f),
                            enabled = preview.bitmap != null,
                            onClick = { shareFrom({ viewModel.exportForShare() }, whatsapp) },
                        )
                    }
                    if (telegram != null) {
                        PrimaryButton(
                            text = stringResource(R.string.preview_send_telegram),
                            icon = Icons.AutoMirrored.Rounded.Send,
                            modifier = Modifier.weight(1f),
                            enabled = preview.bitmap != null,
                            onClick = { shareFrom({ viewModel.exportForShare() }, telegram) },
                        )
                    }
                }

                PrimaryButton(
                    text = stringResource(R.string.preview_save),
                    icon = Icons.Rounded.SaveAlt,
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.PREVIEW_SAVE),
                    enabled = preview.bitmap != null,
                    onClick = {
                        scope.launch {
                            viewModel.saveToGallery()
                                .onSuccess { path ->
                                    val message = if (path != null) {
                                        context.getString(R.string.preview_saved, path)
                                    } else {
                                        context.getString(R.string.preview_export_failed, "media store")
                                    }
                                    context.toast(message)
                                }
                                .onFailure { error -> exportFailed(error) }
                        }
                    },
                )

                ExportOptionsCard(
                    open = optionsOpen,
                    onToggle = { optionsOpen = !optionsOpen },
                    format = exportFormat,
                    size = exportSize,
                    onFormat = { settings.setExportFormat(it) },
                    onSize = { settings.setExportSize(it) },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = onBackToEdit,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TestTags.PREVIEW_BACK_TO_EDIT),
                    ) {
                        Text(stringResource(R.string.preview_back_to_edit))
                    }
                    TextButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ExportOptionsCard(
    open: Boolean,
    onToggle: () -> Unit,
    format: ExportFormat,
    size: Int,
    onFormat: (ExportFormat) -> Unit,
    onSize: (Int) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.PREVIEW_OPTIONS_TOGGLE)
                    .clickable(onClick = onToggle),
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.preview_export_options),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(visible = open) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.preview_format),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportFormat.entries.forEach { entry ->
                            SelectChip(
                                label = entry.name,
                                selected = format == entry,
                                onClick = { onFormat(entry) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.preview_size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportOptions.SIZE_CHOICES.forEach { choice ->
                            SelectChip(
                                label = stringResource(
                                    when (choice) {
                                        1080 -> R.string.preview_size_1080
                                        720 -> R.string.preview_size_720
                                        else -> R.string.preview_size_original
                                    },
                                ),
                                selected = size == choice,
                                onClick = { onSize(choice) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.preview_metadata_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationBanner(
    verifying: Boolean,
    verify: VerifyResult?,
    error: String?,
    modifier: Modifier = Modifier,
    animations: Boolean = true,
) {
    val container: Color
    val content: Color
    val text: String
    val scopeText: String?
    val icon: androidx.compose.ui.graphics.vector.ImageVector

    when {
        error != null -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Rounded.WarningAmber
            text = stringResource(R.string.preview_export_failed, error)
            scopeText = null
        }

        verifying || verify == null -> {
            container = MaterialTheme.colorScheme.surfaceContainerHigh
            content = MaterialTheme.colorScheme.onSurface
            icon = Icons.Rounded.Verified
            text = stringResource(R.string.preview_verifying)
            scopeText = null
        }

        verify.isClean -> {
            container = MintSecondary.copy(alpha = 0.16f)
            content = MaterialTheme.colorScheme.onSurface
            icon = Icons.Rounded.Verified
            text = stringResource(R.string.preview_verify_ok)
            scopeText = if (verify.checkedCodes && verify.checkedFaces) {
                stringResource(R.string.preview_verify_scope)
            } else {
                stringResource(R.string.preview_verify_scope_text)
            }
        }

        else -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Rounded.WarningAmber
            text = pluralStringResource(
                R.plurals.preview_verify_warn,
                verify.leftoverCount,
                verify.leftoverCount,
            )
            scopeText = null
        }
    }

    // The verdict is the emotional payoff of the whole flow, so it animates into place instead of
    // snapping in.
    val scale = remember { androidx.compose.animation.core.Animatable(if (animations) 0.92f else 1f) }
    val fade = remember { androidx.compose.animation.core.Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(verify, verifying, animations) {
        if (!animations) {
            scale.snapTo(1f)
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        scale.snapTo(0.92f)
        fade.snapTo(0f)
        scale.animateTo(1f, androidx.compose.animation.core.spring())
        fade.animateTo(1f, tween(220))
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        modifier = modifier.graphicsLayer {
            this.scaleX = scale.value
            this.scaleY = scale.value
            alpha = fade.value
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (verifying) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = content,
                )
                if (scopeText != null) {
                    Text(
                        text = scopeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}
