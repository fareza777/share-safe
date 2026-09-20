package com.sharesafe.app.ui.batch

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharesafe.app.core.batch.BatchItemStatus
import com.sharesafe.app.core.batch.BatchProtectEngine
import com.sharesafe.app.core.batch.BatchProtectItem
import com.sharesafe.app.core.batch.BatchProtectSummary
import com.sharesafe.app.core.detect.ScanOptions
import com.sharesafe.app.core.model.BeautifyConfig
import com.sharesafe.app.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives one Batch Protect run.
 *
 * It owns exactly two pieces of mutable state that matter: the row list, and the thumbnails. The
 * thumbnails are the only bitmaps this class holds, they are capped at [BatchProtectEngine
 * .THUMBNAIL_MAX_DIM], and they are recycled the moment the batch is replaced or the view model is
 * cleared — the full-resolution images never exist here at all, because the engine writes each one
 * to the gallery and drops it before starting the next.
 */
class BatchProtectViewModel(application: Application) : AndroidViewModel(application) {

    data class Row(
        val item: BatchProtectItem,
        val status: BatchItemStatus,
        val thumbnail: Bitmap?,
    )

    data class UiState(
        val rows: List<Row> = emptyList(),
        val running: Boolean = false,
        val summary: BatchProtectSummary? = null,
        val error: String? = null,
    ) {
        val total: Int get() = rows.size
        val doneCount: Int get() = rows.count { it.status is BatchItemStatus.Done }
        val failedCount: Int get() = rows.count { it.status is BatchItemStatus.Failed }
        val needsReviewCount: Int
            get() = rows.count { (it.status as? BatchItemStatus.Done)?.needsReview == true }
        val currentName: String
            get() = rows.firstOrNull { it.status is BatchItemStatus.Working }?.item?.displayName.orEmpty()

        /** Fraction of the run that has reached a terminal state. */
        val progress: Float
            get() = if (total == 0) 0f else (doneCount + failedCount).toFloat() / total.toFloat()

        val finished: Boolean get() = summary != null && !running

        /** Everything that was written to the gallery, in batch order. */
        val exportUris: List<Uri>
            get() = rows.mapNotNull { (it.status as? BatchItemStatus.Done)?.exportUri }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Starts a run. Calling it twice for the same selection is a no-op, which is what keeps a
     * configuration change (or a re-entry into the screen) from silently doing the work twice.
     */
    fun start(items: List<BatchProtectItem>, force: Boolean = false) {
        if (items.isEmpty()) return
        val current = _state.value
        if (current.running) return
        if (!force && current.rows.map { it.item.uri } == items.map { it.uri } && current.summary != null) {
            return
        }
        clearThumbnails()
        _state.value = UiState(
            rows = items.map { Row(it, BatchItemStatus.Queued, null) },
            running = true,
        )

        val settings = SettingsStore.instance
        viewModelScope.launch {
            val app = getApplication<Application>()
            val summary = runCatching {
                BatchProtectEngine.protect(
                    context = app,
                    items = items,
                    scanOptions = ScanOptions(
                        detectCodes = true,
                        detectFaces = settings.detectFaces.value,
                        includeLongNumbers = settings.longNumbers.value,
                    ),
                    style = settings.defaultStyle.value,
                    strength = settings.lastStrength.value,
                    exportOptions = settings.exportOptions(),
                    beautify = BeautifyConfig.of(settings.beautifyPreset.value),
                    faceMask = settings.faceMask.value,
                    ignoreKinds = settings.defaultDisabledKinds(),
                    verify = settings.autoVerify.value,
                    autoFix = settings.autoFixLeftovers.value,
                ) { index, status, thumbnail ->
                    applyUpdate(index, status, thumbnail)
                }
            }.getOrElse { error ->
                _state.update { it.copy(running = false, error = error.message ?: "batch failed") }
                return@launch
            }
            _state.update { it.copy(running = false, summary = summary) }
        }
    }

    /** Re-runs a single failed row, without disturbing the ones that already succeeded. */
    fun retry(index: Int) {
        val row = _state.value.rows.getOrNull(index) ?: return
        if (_state.value.running) return
        if (row.status !is BatchItemStatus.Failed) return
        val settings = SettingsStore.instance
        viewModelScope.launch {
            applyUpdate(index, BatchItemStatus.Working, null)
            val app = getApplication<Application>()
            runCatching {
                BatchProtectEngine.protect(
                    context = app,
                    items = listOf(row.item),
                    scanOptions = ScanOptions(
                        detectCodes = true,
                        detectFaces = settings.detectFaces.value,
                        includeLongNumbers = settings.longNumbers.value,
                    ),
                    style = settings.defaultStyle.value,
                    strength = settings.lastStrength.value,
                    exportOptions = settings.exportOptions(),
                    beautify = BeautifyConfig.of(settings.beautifyPreset.value),
                    faceMask = settings.faceMask.value,
                    ignoreKinds = settings.defaultDisabledKinds(),
                    verify = settings.autoVerify.value,
                    autoFix = settings.autoFixLeftovers.value,
                ) { _, status, thumbnail ->
                    // The engine reports index 0 for a one-item run; remap it onto this row.
                    applyUpdate(index, status, thumbnail)
                }
            }.onFailure { error ->
                applyUpdate(index, BatchItemStatus.Failed(error.message ?: "failed"), null)
            }
        }
    }

    private fun applyUpdate(index: Int, status: BatchItemStatus, thumbnail: Bitmap?) {
        _state.update { current ->
            if (index !in current.rows.indices) {
                thumbnail?.let { if (!it.isRecycled) it.recycle() }
                return@update current
            }
            val previous = current.rows[index]
            // A row can be updated more than once (queued → working → done); only the newest
            // thumbnail survives, and the one it replaces is freed here.
            if (thumbnail != null && previous.thumbnail !== thumbnail) {
                previous.thumbnail?.let { if (!it.isRecycled) it.recycle() }
            }
            current.copy(
                rows = current.rows.mapIndexed { i, row ->
                    if (i == index) row.copy(status = status, thumbnail = thumbnail ?: row.thumbnail)
                    else row
                },
            )
        }
    }

    /** Frees the thumbnails when the screen is left for good. */
    fun reset() {
        clearThumbnails()
        _state.value = UiState()
    }

    private fun clearThumbnails() {
        _state.value.rows.forEach { row ->
            row.thumbnail?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    override fun onCleared() {
        clearThumbnails()
        super.onCleared()
    }
}
