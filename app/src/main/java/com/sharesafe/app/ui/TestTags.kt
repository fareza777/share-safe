package com.sharesafe.app.ui

/**
 * Stable handles for instrumented UI tests. Tags rather than user-visible strings, so the tests do
 * not depend on the active locale — which matters more now that the app ships a language setting.
 */
object TestTags {
    const val SPLASH = "splash"

    // Home. There is exactly one primary action now, so the tags describe intent, not widgets.
    const val HOME_PICK = "home-pick"
    const val HOME_RECENT_THUMB = "home-recent-thumb"
    const val HOME_SELECT_TOGGLE = "home-select-toggle"
    const val HOME_BATCH_ACTION = "home-batch-action"
    const val HOME_SETTINGS_ENTRY = "home-settings-entry"
    const val HOME_HISTORY_ENTRY = "home-history-entry"
    const val HOME_STATS = "home-stats"

    const val ONBOARDING_CTA = "onboarding-cta"
    const val ONBOARDING_NEXT = "onboarding-next"

    // Settings is a stack of collapsible cards; each header carries a stable id.
    const val SETTINGS_SCREEN = "settings-screen"
    const val SETTINGS_SECTION = "settings-section"
    const val SETTINGS_ABOUT_ENTRY = "settings-about-entry"
    const val SETTINGS_PALETTE = "settings-palette"
    const val SETTINGS_HISTORY_CLEAR = "settings-history-clear"
    const val SETTINGS_LANGUAGE = "settings-language"
    const val SETTINGS_ADS_REWARD = "settings-ads-reward"

    const val ABOUT_SCREEN = "about-screen"
    const val ABOUT_SHARE = "about-share"
    const val ABOUT_RATE = "about-rate"
    const val ABOUT_REPLAY_ONBOARDING = "about-replay-onboarding"

    const val HISTORY_SCREEN = "history-screen"
    const val HISTORY_CALENDAR_DAY = "history-calendar-day"
    const val HISTORY_ITEM = "history-item"
    const val HISTORY_CLEAR = "history-clear"
    const val HISTORY_REOPEN = "history-reopen"

    // Batch Protect.
    const val BATCH_SCREEN = "batch-screen"
    const val BATCH_ROW = "batch-row"
    const val BATCH_PROGRESS = "batch-progress"
    const val BATCH_SHARE_ALL = "batch-share-all"
    const val BATCH_RETRY = "batch-retry"
    const val BATCH_SUMMARY = "batch-summary"

    const val EDITOR_STATUS = "editor-status"
    const val EDITOR_PREVIEW_SHARE = "editor-preview-share"
    const val EDITOR_PANEL_TOGGLE = "editor-panel-toggle"
    const val EDITOR_TAB = "editor-tab"
    const val EDITOR_CHAT_SUGGESTION = "editor-chat-suggestion"
    const val EDITOR_CHAT_SUGGEST_ACCEPT = "editor-chat-suggestion-accept"
    const val EDITOR_CHAT_SUGGEST_DISMISS = "editor-chat-suggestion-dismiss"
    const val EDITOR_CHAT_ROW = "editor-chat-row"
    const val EDITOR_PRESET_ROW = "editor-preset-row"

    const val PREVIEW_VERIFY_BANNER = "preview-verify-banner"
    const val PREVIEW_SAFE_SHARE = "preview-safe-share"
    const val PREVIEW_SAVE = "preview-save"
    const val PREVIEW_COMPARE = "preview-compare"
    const val PREVIEW_OPTIONS_TOGGLE = "preview-options-toggle"
    const val PREVIEW_BACK_TO_EDIT = "preview-back-to-edit"

    /** Banner slot, so a test can assert it is *absent* where it must never appear. */
    const val AD_BANNER = "ad-banner"

    /** Tag for one collapsible settings card, e.g. `settingsSection("detection")`. */
    fun settingsSection(id: String): String = "$SETTINGS_SECTION-$id"

    /**
     * Tag for one chip in a horizontal options row — a chat layout, a beautify preset. Addressed by
     * the model's own id rather than its label, so the tests survive translation.
     */
    fun editorOption(id: String): String = "editor-option-$id"
}
