package com.sharesafe.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharesafe.app.R
import com.sharesafe.app.core.detect.ChatPreset
import com.sharesafe.app.core.model.BackgroundStyle
import com.sharesafe.app.core.model.BeautifyConfig
import com.sharesafe.app.core.model.BeautifyPreset
import com.sharesafe.app.core.model.FaceMaskStyle
import com.sharesafe.app.core.model.RedactionStyle
import com.sharesafe.app.core.model.SensitiveKind
import com.sharesafe.app.ui.TestTags
import com.sharesafe.app.ui.components.PlusUnlockDialog
import com.sharesafe.app.ui.components.ShimmerBox
import com.sharesafe.app.ui.components.SelectChip
import com.sharesafe.app.ui.components.rememberPlusActive
import com.sharesafe.app.ui.theme.KindManual

private enum class EditorTab(val labelRes: Int) {
    DETECT(R.string.editor_tab_detect),
    STYLE(R.string.editor_tab_style),
    TOOLS(R.string.editor_tab_tools),
}

/** Redaction editor: canvas, detection list, style controls, crop and beautify. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    animations: Boolean = true,
) {
    val state by viewModel.state.collectAsState()
    val counts = state.detections.groupingBy { it.kind }.eachCount()
    var menuOpen by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(EditorTab.DETECT) }
    var panelExpanded by rememberSaveable { mutableStateOf(true) }

    // Real window insets instead of framework resource lookups: this is what the screenshots were
    // captured with on this device.
    val density = LocalDensity.current
    val statusBarInset = WindowInsets.statusBars.getTop(density)
    val navigationBarInset = WindowInsets.navigationBars.getBottom(density)
    LaunchedEffect(statusBarInset, navigationBarInset) {
        viewModel.setDeviceInsets(statusBarInset, navigationBarInset)
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.titleMedium)
                    if (state.activeRegionCount > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.editor_regions_count,
                                state.activeRegionCount,
                                state.activeRegionCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = stringResource(R.string.editor_undo),
                    )
                }
                IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = stringResource(R.string.editor_redo),
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.editor_more),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_fix_everything)) },
                            leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.enableEverything()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_rescan)) },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.retryScan()
                            },
                        )
                        val selectedId = state.selectedManualId
                        if (selectedId != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_delete_region)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.removeManualRegion(selectedId)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_reset)) },
                            leadingIcon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.resetAll()
                            },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            RegionCanvas(
                state = state,
                onToggleDetection = viewModel::toggleDetection,
                onSelectManual = viewModel::selectManualRegion,
                onCreateManual = viewModel::addManualRegion,
                onMoveManual = viewModel::updateManualRegion,
                onBeginInteraction = viewModel::beginInteractiveEdit,
                modifier = Modifier.fillMaxSize(),
            )

            when {
                state.phase == EditorPhase.FAILED -> ErrorOverlay(
                    message = state.error ?: stringResource(R.string.editor_load_error),
                    onRetry = onBack,
                )

                state.scanning && state.detections.isEmpty() -> ScanningOverlay(animations)
            }

            // While a partial scan is in flight the canvas is dimmed with a live shimmer, so the
            // wait is visible without hiding the boxes that already landed.
            if (state.scanning && state.detections.isNotEmpty()) {
                ShimmerBox(
                    enabled = animations,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .size(width = 180.dp, height = 30.dp),
                )
            }

            if (!state.scanning && state.phase == EditorPhase.READY) {
                HintBar(
                    text = if (state.detections.isEmpty()) {
                        stringResource(R.string.editor_detected_none)
                    } else {
                        stringResource(R.string.editor_hint_manual)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
                    .animateContentSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    // The primary action is the last thing on screen: keep it clear of the gesture
                    // bar so it stays comfortably tappable.
                    .navigationBarsPadding(),
            ) {
                StatusRow(
                    state = state,
                    counts = counts,
                    expanded = panelExpanded,
                    onRetry = viewModel::retryScan,
                    onClearManual = viewModel::clearManualRegions,
                    onToggleExpanded = { panelExpanded = !panelExpanded },
                )

                if (panelExpanded) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryTabRow(
                        selectedTabIndex = tab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        EditorTab.entries.forEach { entry ->
                            Tab(
                                selected = tab == entry,
                                onClick = { tab = entry },
                                text = { Text(stringResource(entry.labelRes)) },
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            if (!animations) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                val forward = targetState.ordinal > initialState.ordinal
                                (slideInHorizontally(tween(220)) { width -> if (forward) width / 6 else -width / 6 } +
                                    fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(180)) { width -> if (forward) -width / 8 else width / 8 } +
                                        fadeOut(tween(140)))
                            }
                        },
                        label = "editor-tab",
                    ) { activeTab ->
                        Column(
                            modifier = Modifier
                                .height(232.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            when (activeTab) {
                                EditorTab.DETECT -> {
                                    ChatSuggestion(
                                        suggestion = state.suggestedChat
                                            ?.takeIf { !state.chatSuggestionDismissed && state.chat == ChatPreset.OFF },
                                        animations = animations,
                                        onAccept = viewModel::acceptChatSuggestion,
                                        onDismiss = viewModel::dismissChatSuggestion,
                                    )
                                    ChatPresetRow(preset = state.chat, onSelect = viewModel::setChat)
                                    KindChips(
                                        counts = counts,
                                        disabled = state.disabledKinds,
                                        onToggle = viewModel::toggleKind,
                                    )
                                    DetectionList(state = state, viewModel = viewModel)
                                }

                                EditorTab.STYLE -> StyleSection(state = state, viewModel = viewModel)

                                EditorTab.TOOLS -> {
                                    ToolsSection(state = state, viewModel = viewModel)
                                    BeautifyPresetsRow(
                                        selected = state.beautifyPreset,
                                        onSelect = viewModel::setBeautifyPreset,
                                    )
                                    if (state.beautify.enabled) {
                                        BeautifySection(state = state, viewModel = viewModel)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = onNext,
                    enabled = state.phase == EditorPhase.READY && !state.scanning,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.EDITOR_PREVIEW_SHARE),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.editor_preview_share),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    state: EditorUiState,
    counts: Map<SensitiveKind, Int>,
    expanded: Boolean,
    onRetry: () -> Unit,
    onClearManual: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val total = counts.values.sum()
        if (state.warnings.isNotEmpty()) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.editor_scan_failed, state.warnings.first()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        } else {
            Text(
                text = when {
                    state.scanning -> stringResource(R.string.editor_detecting)
                    total > 0 -> pluralStringResource(
                        R.plurals.editor_detected_count,
                        state.detectionCount,
                        state.detectionCount,
                    )

                    else -> stringResource(R.string.editor_detected_none)
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).testTag(TestTags.EDITOR_STATUS),
            )
            if (state.manualCount > 0) {
                SelectChip(
                    label = stringResource(R.string.category_manual),
                    selected = false,
                    badge = state.manualCount,
                    accent = KindManual,
                    onClick = onClearManual,
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        IconButton(
            onClick = onToggleExpanded,
            modifier = Modifier.testTag(TestTags.EDITOR_PANEL_TOGGLE),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                contentDescription = stringResource(
                    if (expanded) R.string.editor_collapse else R.string.editor_expand,
                ),
            )
        }
    }
}

@Composable
private fun KindChips(
    counts: Map<SensitiveKind, Int>,
    disabled: Set<SensitiveKind>,
    onToggle: (SensitiveKind) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.editor_tools),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SensitiveKind.chipOrder.toList(), key = { it.name }) { kind ->
                val count = counts[kind] ?: 0
                SelectChip(
                    label = kindLabel(kind),
                    selected = kind !in disabled,
                    badge = count,
                    icon = kindIcon(kind),
                    accent = kindColor(kind),
                    enabled = count > 0 || kind in disabled,
                    onClick = { onToggle(kind) },
                )
            }
        }
    }
}

/** Per-detection control: tap a row to switch one box off without hunting for it on the canvas. */
@Composable
private fun DetectionList(state: EditorUiState, viewModel: EditorViewModel) {
    val ordered = remember(state.detections) {
        state.detections.sortedWith(
            compareBy({ SensitiveKind.chipOrder.indexOf(it.kind) }, { it.bounds.top }),
        )
    }
    if (ordered.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ordered.forEach { detection ->
            val active = state.isDetectionActive(detection)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = kindIcon(detection.kind),
                    contentDescription = null,
                    tint = if (active) kindColor(detection.kind) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = kindLabel(detection.kind),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (detection.label.isNotBlank()) {
                        Text(
                            text = detection.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = active,
                    onCheckedChange = { viewModel.toggleDetection(detection.id) },
                )
            }
        }
    }
}

@Composable
private fun StyleSection(state: EditorUiState, viewModel: EditorViewModel) {
    Column {
        Text(
            text = stringResource(R.string.editor_style_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RedactionStyle.entries.forEach { style ->
                SelectChip(
                    label = styleLabel(style),
                    selected = state.style == style,
                    icon = styleIcon(style),
                    onClick = { viewModel.setStyle(style) },
                )
            }
        }
        if (state.style == RedactionStyle.BLUR || state.style == RedactionStyle.PIXELATE) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.editor_intensity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.strength,
                    onValueChange = viewModel::setStrength,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(
                    text = "${(state.strength * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        FaceMaskRow(style = state.faceMask, onSelect = viewModel::setFaceMask)
    }
}

/**
 * "This looks like a WhatsApp screenshot" — as a question, not a decision.
 *
 * The header geometry is reliable enough to offer a preset and not reliable enough to apply one: a
 * title, a clock and a profile photo in the top band can describe a conversation or a settings
 * screen, and only the user knows which. So the offer is one tap, it says which app it recognised,
 * and declining changes nothing about the redaction already on screen.
 */
@Composable
private fun ChatSuggestion(
    suggestion: ChatPreset?,
    animations: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = suggestion != null,
        enter = if (animations) expandVertically(tween(220)) + fadeIn(tween(180)) else
            EnterTransition.None,
        exit = if (animations) shrinkVertically(tween(180)) + fadeOut(tween(120)) else
            ExitTransition.None,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.EDITOR_CHAT_SUGGESTION),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Icon(
                    Icons.Rounded.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        R.string.editor_chat_suggest,
                        stringResource(
                            when (suggestion) {
                                ChatPreset.WHATSAPP -> R.string.chat_whatsapp
                                ChatPreset.TELEGRAM -> R.string.chat_telegram
                                ChatPreset.DM -> R.string.chat_dm
                                else -> R.string.chat_off
                            },
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onAccept,
                    modifier = Modifier.testTag(TestTags.EDITOR_CHAT_SUGGEST_ACCEPT),
                ) { Text(stringResource(R.string.editor_chat_suggest_accept)) }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(TestTags.EDITOR_CHAT_SUGGEST_DISMISS),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Chat Privacy Mode: which conversation layout the scan should be tuned for. Switching this re-runs
 * detection, so it sits above the chips rather than next to the style options.
 */
@Composable
private fun ChatPresetRow(preset: ChatPreset, onSelect: (ChatPreset) -> Unit) {
    Column(Modifier.testTag(TestTags.EDITOR_CHAT_ROW)) {
        Text(
            text = stringResource(R.string.editor_chat_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ChatPreset.entries.toList(), key = { it.name }) { entry ->
                SelectChip(
                    label = stringResource(
                        when (entry) {
                            ChatPreset.OFF -> R.string.chat_off
                            ChatPreset.WHATSAPP -> R.string.chat_whatsapp
                            ChatPreset.TELEGRAM -> R.string.chat_telegram
                            ChatPreset.DM -> R.string.chat_dm
                        },
                    ),
                    selected = preset == entry,
                    icon = Icons.Rounded.Forum,
                    modifier = Modifier.testTag(TestTags.editorOption("chat-${entry.id}")),
                    onClick = { onSelect(entry) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.editor_chat_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Boxes around text, but a feathered oval around a person. */
@Composable
private fun FaceMaskRow(style: FaceMaskStyle, onSelect: (FaceMaskStyle) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.editor_face_mask),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip(
                label = stringResource(R.string.editor_face_mask_oval),
                selected = style == FaceMaskStyle.SOFT_OVAL,
                icon = Icons.Rounded.FaceRetouchingNatural,
                onClick = { onSelect(FaceMaskStyle.SOFT_OVAL) },
            )
            SelectChip(
                label = stringResource(R.string.editor_face_mask_box),
                selected = style == FaceMaskStyle.BOX,
                icon = Icons.Rounded.CropSquare,
                onClick = { onSelect(FaceMaskStyle.BOX) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.editor_face_mask_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The looks that make an export look deliberate. Two of them are behind the rewarded window, and
 * the chips say so instead of failing silently — tapping a locked one explains rather than ignores.
 */
@Composable
private fun BeautifyPresetsRow(selected: BeautifyPreset, onSelect: (BeautifyPreset) -> Unit) {
    val plusActive = rememberPlusActive()
    var pendingPreset by remember { mutableStateOf<BeautifyPreset?>(null) }

    Column(Modifier.testTag(TestTags.EDITOR_PRESET_ROW)) {
        Text(
            text = stringResource(R.string.editor_preset_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BeautifyPreset.entries.toList(), key = { it.name }) { preset ->
                val locked = preset.premium && !plusActive
                SelectChip(
                    label = stringResource(presetLabel(preset)),
                    selected = selected == preset,
                    icon = if (locked) Icons.Rounded.Lock else null,
                    modifier = Modifier.testTag(TestTags.editorOption("preset-${preset.id}")),
                    onClick = {
                        if (locked) pendingPreset = preset else onSelect(preset)
                    },
                )
            }
        }
        Text(
            text = stringResource(R.string.editor_preset_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    pendingPreset?.let { preset ->
        PlusUnlockDialog(
            title = stringResource(R.string.editor_preset_locked_title, stringResource(presetLabel(preset))),
            body = stringResource(R.string.editor_preset_locked_body),
            confirmLabel = stringResource(R.string.plus_watch),
            alternativeLabel = stringResource(R.string.editor_preset_use_free),
            onAlternative = {
                pendingPreset = null
                onSelect(BeautifyPreset.CLEAN)
            },
            onDismiss = { pendingPreset = null },
            onUnlocked = {
                pendingPreset = null
                onSelect(preset)
            },
        )
    }
}

private fun presetLabel(preset: BeautifyPreset): Int = when (preset) {
    BeautifyPreset.OFF -> R.string.preset_off
    BeautifyPreset.CLEAN -> R.string.preset_clean
    BeautifyPreset.NIGHT -> R.string.preset_night
    BeautifyPreset.SOLID -> R.string.preset_solid
    BeautifyPreset.AURORA -> R.string.preset_aurora
    BeautifyPreset.STUDIO -> R.string.preset_studio
}

@Composable
private fun ToolsSection(state: EditorUiState, viewModel: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleRow(
            icon = Icons.Rounded.ContentCut,
            label = stringResource(R.string.editor_trim_bars),
            checked = state.cropConfig.trimSystemBars,
            onChecked = { viewModel.setCropConfig(state.cropConfig.copy(trimSystemBars = it)) },
        )
        ToggleRow(
            icon = Icons.Rounded.ContentCut,
            label = stringResource(R.string.editor_trim_edges),
            checked = state.cropConfig.trimBlankEdges,
            onChecked = { viewModel.setCropConfig(state.cropConfig.copy(trimBlankEdges = it)) },
        )
        ToggleRow(
            icon = Icons.Rounded.AutoFixHigh,
            label = stringResource(R.string.editor_beautify),
            checked = state.beautify.enabled,
            onChecked = { viewModel.setBeautify { config -> config.copy(enabled = it) } },
        )
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun BeautifySection(state: EditorUiState, viewModel: EditorViewModel) {
    val config = state.beautify
    Column {
        LabeledSlider(
            label = stringResource(R.string.editor_beautify_padding),
            value = config.paddingFraction,
            range = 0f..BeautifyConfig.MAX_PADDING,
            onChange = { value -> viewModel.setBeautify { it.copy(paddingFraction = value) } },
        )
        LabeledSlider(
            label = stringResource(R.string.editor_beautify_corner),
            value = config.cornerFraction,
            range = 0f..BeautifyConfig.MAX_CORNER,
            onChange = { value -> viewModel.setBeautify { it.copy(cornerFraction = value) } },
        )
        LabeledSlider(
            label = stringResource(R.string.editor_beautify_shadow),
            value = config.shadow,
            range = 0f..1f,
            onChange = { value -> viewModel.setBeautify { it.copy(shadow = value) } },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.editor_beautify_background),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BackgroundStyle.entries.toList(), key = { it.name }) { background ->
                SelectChip(
                    label = stringResource(
                        when (background) {
                            BackgroundStyle.AUTO -> R.string.editor_bg_auto
                            BackgroundStyle.NIGHT -> R.string.editor_bg_dark
                            BackgroundStyle.PAPER -> R.string.editor_bg_light
                            BackgroundStyle.GRADIENT -> R.string.editor_bg_gradient
                            BackgroundStyle.SOLID -> R.string.editor_bg_solid
                        },
                    ),
                    selected = config.background == background,
                    onClick = { viewModel.setBeautify { it.copy(background = background) } },
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ScanningOverlay(animations: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.editor_detecting),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.editor_scan_stages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_back)) }
        }
    }
}

@Composable
private fun HintBar(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.padding(12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
