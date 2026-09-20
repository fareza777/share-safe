package com.sharesafe.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharesafe.app.core.batch.BatchProtectItem
import com.sharesafe.app.data.HistoryStore
import com.sharesafe.app.data.SettingsStore
import com.sharesafe.app.ui.about.AboutScreen
import com.sharesafe.app.ui.batch.BatchProtectScreen
import com.sharesafe.app.ui.batch.BatchProtectViewModel
import com.sharesafe.app.ui.editor.EditorPhase
import com.sharesafe.app.ui.editor.EditorScreen
import com.sharesafe.app.ui.editor.EditorViewModel
import com.sharesafe.app.ui.history.HistoryScreen
import com.sharesafe.app.ui.home.HomeScreen
import com.sharesafe.app.ui.onboarding.OnboardingScreen
import com.sharesafe.app.ui.preview.PreviewScreen
import com.sharesafe.app.ui.settings.SettingsScreen
import com.sharesafe.app.ui.splash.SplashScreen
import com.sharesafe.app.ui.theme.LocalAnimationsEnabled
import kotlinx.coroutines.delay

/**
 * Depth drives the transition direction: going deeper slides in from the right, coming back slides
 * in from the left, and the two first-run screens cross-fade.
 */
private enum class Screen(val depth: Int) {
    SPLASH(0),
    ONBOARDING(0),
    HOME(1),
    HISTORY(2),
    SETTINGS(2),
    EDITOR(2),
    BATCH(2),
    ABOUT(3),
    PREVIEW(3),
}

/**
 * The whole app in one state machine: splash → onboarding → pick → redact → share, with History,
 * Settings and About hanging off it. No navigation library — one ViewModel instance is shared
 * across steps so the image and every edit survive rotation, and one AnimatedContent owns the
 * transitions so every move feels the same.
 */
@Composable
fun ShareSafeRoot(
    shareTarget: Uri? = null,
    onShareTargetConsumed: () -> Unit = {},
) {
    val viewModel: EditorViewModel = viewModel()
    val batchViewModel: BatchProtectViewModel = viewModel()
    val editorState by viewModel.state.collectAsState()
    val settings = SettingsStore.instance
    val animations by settings.animations.collectAsState()
    val onboardingDone by settings.onboardingDone.collectAsState()

    var screen by rememberSaveable { mutableStateOf(Screen.SPLASH) }

    // Arrived from another app's share sheet: open that image straight in the editor, skipping the
    // splash so the safe path starts one tap earlier.
    LaunchedEffect(shareTarget) {
        val target = shareTarget ?: return@LaunchedEffect
        viewModel.load(target, target.lastPathSegment?.substringAfterLast('/').orEmpty())
        screen = Screen.EDITOR
        onShareTargetConsumed()
    }

    // The splash is decorative and short: it hands over as soon as the first-run decision is known.
    LaunchedEffect(screen, animations) {
        if (screen != Screen.SPLASH) return@LaunchedEffect
        delay(if (animations) SPLASH_MILLIS else 250L)
        screen = if (onboardingDone) Screen.HOME else Screen.ONBOARDING
    }

    // If the process was recreated we may have lost the image: go back home instead of showing a
    // dead editor. An incoming share is excluded — its load starts on this very frame, so the
    // empty phase here is not a lost image.
    LaunchedEffect(editorState.phase, shareTarget) {
        val onImageScreen = screen == Screen.EDITOR || screen == Screen.PREVIEW
        when {
            editorState.phase == EditorPhase.EMPTY && onImageScreen && shareTarget == null ->
                screen = Screen.HOME

            // The one-tap path lands on Preview before the render exists; a failed decode has to
            // fall back to the editor, which knows how to explain itself.
            editorState.phase == EditorPhase.FAILED && screen == Screen.PREVIEW ->
                screen = Screen.EDITOR

            else -> Unit
        }
    }

    BackHandler(enabled = screen != Screen.HOME && screen != Screen.SPLASH && screen != Screen.ONBOARDING) {
        screen = when (screen) {
            Screen.PREVIEW -> {
                viewModel.releasePreview()
                Screen.EDITOR
            }

            Screen.EDITOR -> {
                viewModel.releasePreview()
                Screen.HOME
            }

            Screen.ABOUT -> Screen.SETTINGS
            Screen.BATCH -> {
                batchViewModel.reset()
                Screen.HOME
            }

            Screen.HISTORY, Screen.SETTINGS -> Screen.HOME
            else -> Screen.HOME
        }
    }

    CompositionLocalProvider(LocalAnimationsEnabled provides animations) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (!animations || initialState.depth == targetState.depth) {
                    fadeIn(tween(if (animations) 220 else 0)) togetherWith
                        fadeOut(tween(if (animations) 160 else 0))
                } else {
                    val forward = targetState.depth > initialState.depth
                    val enter = slideInHorizontally(
                        animationSpec = tween(280),
                        initialOffsetX = { width -> if (forward) width / 5 else -width / 5 },
                    ) + fadeIn(tween(240))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(240),
                        targetOffsetX = { width -> if (forward) -width / 8 else width / 8 },
                    ) + fadeOut(tween(180))
                    enter togetherWith exit
                }
            },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.SPLASH -> SplashScreen(animations = animations)

                Screen.ONBOARDING -> OnboardingScreen(
                    animations = animations,
                    onDone = {
                        settings.setOnboardingDone(true)
                        screen = Screen.HOME
                    },
                )

                Screen.HOME -> HomeScreen(
                    // Every entry point on the home screen - the big card and any thumbnail in the
                    // strip - runs the same automatic pipeline. That is deliberate: the previous
                    // "pick" and "do it for me" pair led to the same place with different names.
                    onProtect = { uri, name ->
                        viewModel.loadAndPrepare(uri, name)
                        screen = Screen.PREVIEW
                    },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenHistory = { screen = Screen.HISTORY },
                    onStartBatch = { chosen ->
                        batchViewModel.start(
                            chosen.map { BatchProtectItem(it.uri, it.displayName) },
                        )
                        screen = Screen.BATCH
                    },
                    animations = animations,
                )

                Screen.HISTORY -> HistoryScreen(
                    onBack = { screen = Screen.HOME },
                    onReopen = { entry ->
                        // Re-opening works on the stored *redacted* copy: the original was never
                        // written to disk, and it never will be.
                        HistoryStore.instance.shareUri(entry)?.let { uri ->
                            viewModel.load(uri, entry.sourceName)
                            screen = Screen.EDITOR
                        }
                    },
                    animations = animations,
                )

                Screen.BATCH -> BatchProtectScreen(
                    viewModel = batchViewModel,
                    onBack = {
                        batchViewModel.reset()
                        screen = Screen.HOME
                    },
                    onOpenOne = { uri, name ->
                        batchViewModel.reset()
                        viewModel.load(uri, name)
                        screen = Screen.EDITOR
                    },
                    animations = animations,
                )

                Screen.SETTINGS -> SettingsScreen(
                    onBack = { screen = Screen.HOME },
                    onOpenAbout = { screen = Screen.ABOUT },
                    onOpenHistory = { screen = Screen.HISTORY },
                    animations = animations,
                )

                Screen.ABOUT -> AboutScreen(
                    onBack = { screen = Screen.SETTINGS },
                    onReplayOnboarding = { screen = Screen.ONBOARDING },
                )

                Screen.EDITOR -> EditorScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.releasePreview()
                        screen = Screen.HOME
                    },
                    onNext = {
                        viewModel.preparePreview()
                        screen = Screen.PREVIEW
                    },
                    animations = animations,
                )

                Screen.PREVIEW -> PreviewScreen(
                    viewModel = viewModel,
                    onBackToEdit = {
                        viewModel.releasePreview()
                        screen = Screen.EDITOR
                    },
                    onDone = {
                        viewModel.releasePreview()
                        screen = Screen.HOME
                    },
                    animations = animations,
                )
            }
        }
    }
}

private const val SPLASH_MILLIS = 1500L
