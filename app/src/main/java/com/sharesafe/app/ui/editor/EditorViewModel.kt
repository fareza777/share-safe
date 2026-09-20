package com.sharesafe.app.ui.editor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharesafe.app.core.detect.ScanOptions
import com.sharesafe.app.core.detect.SensitiveScanner
import com.sharesafe.app.core.export.ExportOptions
import com.sharesafe.app.core.export.ImageExporter
import com.sharesafe.app.core.export.SharePayload
import com.sharesafe.app.core.image.BitmapLoader
import com.sharesafe.app.core.model.BeautifyConfig
import com.sharesafe.app.core.model.CropConfig
import com.sharesafe.app.core.model.Detection
import com.sharesafe.app.core.model.IntRect
import com.sharesafe.app.core.model.ManualRegion
import com.sharesafe.app.core.model.NormRect
import com.sharesafe.app.core.model.RedactionStyle
import com.sharesafe.app.core.model.SensitiveKind
import com.sharesafe.app.core.render.AutoTrim
import com.sharesafe.app.core.render.BitmapSampler
import com.sharesafe.app.core.render.Bitmaps
import com.sharesafe.app.core.render.RedactionRenderer
import com.sharesafe.app.core.render.RenderPipeline
import com.sharesafe.app.core.render.Bitmaps.sanitized
import com.sharesafe.app.core.verify.AutoFixPlanner
import com.sharesafe.app.core.verify.RedactionVerifier
import com.sharesafe.app.core.verify.VerifyResult
import com.sharesafe.app.data.HistoryStore
import com.sharesafe.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How far the flow has progressed. */
enum class EditorPhase { EMPTY, LOADING, READY, FAILED }

data class EditorUiState(
    val phase: EditorPhase = EditorPhase.EMPTY,
    val source: Bitmap? = null,
    val sourceName: String = "",
    val detections: List<Detection> = emptyList(),
    val manualRegions: List<ManualRegion> = emptyList(),
    val disabledDetectionIds: Set<String> = emptySet(),
    val disabledKinds: Set<SensitiveKind> = defaultDisabledKinds(),
    val selectedManualId: String? = null,
    val style: RedactionStyle = RedactionStyle.DEFAULT,
    val strength: Float = DEFAULT_STRENGTH,
    val tintColor: Int = RedactionRenderer.DEFAULT_TINT,
    val cropConfig: CropConfig = CropConfig(),
    val cropRect: IntRect = IntRect(0, 0, 1, 1),
    val beautify: BeautifyConfig = BeautifyConfig(),
    val preview: Bitmap? = null,
    val previewContentRect: Rect? = null,
    val previewRedactedRects: List<Rect> = emptyList(),
    val scanning: Boolean = false,
    val rendering: Boolean = false,
    val scanDurationMs: Long = 0,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val detectionCount: Int get() = detections.count { isDetectionActive(it) }
    val manualCount: Int get() = manualRegions.size
    val activeRegionCount: Int get() = detectionCount + manualCount

    fun isDetectionActive(detection: Detection): Boolean =
        detection.id !in disabledDetectionIds && detection.kind !in disabledKinds

    fun activeRegions(): List<NormRect> = buildList {
        detections.filter { isDetectionActive(it) }.forEach { add(it.bounds) }
        manualRegions.forEach { add(it.bounds) }
    }

    companion object {
        const val DEFAULT_STRENGTH = 0.75f

        fun defaultDisabledKinds(): Set<SensitiveKind> =
            SensitiveKind.entries.filterNot { it.defaultEnabled }.toSet()
    }
}

data class PreviewUiState(
    val bitmap: Bitmap? = null,
    /** Same crop and beautify, but unredacted — the "hold to compare" reference frame. */
    val original: Bitmap? = null,
    val redactedRects: List<Rect> = emptyList(),
    val exporting: Boolean = false,
    val verifying: Boolean = false,
    val verify: VerifyResult? = null,
    /** How many areas the automatic repair added after verification found something. */
    val autoFixed: Int = 0,
    val error: String? = null,
)

private data class EditSnapshot(
    val manualRegions: List<ManualRegion>,
    val disabledDetectionIds: Set<String>,
    val disabledKinds: Set<SensitiveKind>,
)

/**
 * Single source of truth for the editor: image, detections, manual regions, redaction style,
 * crop and beautify. Rendering is conflated, so dragging a box never queues up stale frames.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        EditorUiState(
            style = initialStyle(),
            strength = SettingsStore.instance.lastStrength.value,
            disabledKinds = initialDisabledKinds(),
        ),
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _preview = MutableStateFlow(PreviewUiState())
    val preview: StateFlow<PreviewUiState> = _preview.asStateFlow()

    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()
    private val renderTrigger = MutableStateFlow(0)
    private val exportMutex = Mutex()

    private var analysisBitmap: Bitmap? = null
    private var exportedBitmap: Bitmap? = null

    /** Set by the one-tap path: once the scan lands, go straight on to the verified export. */
    private var autoPrepare = false

    /**
     * Device system bar heights, reported by the UI layer from the real window insets. Reading them
     * reflectively from framework resources is unreliable (and flagged by lint).
     */
    private var statusBarInsetPx = 0
    private var navigationBarInsetPx = 0

    init {
        viewModelScope.launch {
            renderTrigger.collectLatest {
                renderPreview()
            }
        }
    }

    // --- Loading --------------------------------------------------------------------------

    /**
     * The fully automatic path: scan the image and render the verified export without any manual
     * step, so the user lands on a finished, shareable result after a single tap.
     */
    fun loadAndPrepare(uri: Uri, displayName: String = "") {
        autoPrepare = true
        load(uri, displayName)
    }

    fun load(uri: Uri, displayName: String = "") {
        _state.update {
            it.copy(phase = EditorPhase.LOADING, error = null, sourceName = displayName)
        }
        val app = getApplication<Application>()
        viewModelScope.launch {
            val loaded = runCatching {
                BitmapLoader.loadSource(app, uri)
            }.getOrElse { error ->
                _state.update {
                    it.copy(phase = EditorPhase.FAILED, error = error.message ?: "decode failed")
                }
                return@launch
            }

            resetHistory()
            analysisBitmap?.let { old -> if (!old.isRecycled) old.recycle() }
            analysisBitmap = null
            exportedBitmap?.let { old -> if (!old.isRecycled) old.recycle() }
            exportedBitmap = null

            _state.value = EditorUiState(
                phase = EditorPhase.READY,
                source = loaded,
                sourceName = displayName,
                style = _state.value.style,
                strength = _state.value.strength,
                tintColor = _state.value.tintColor,
                disabledKinds = initialDisabledKinds(),
                cropConfig = _state.value.cropConfig,
                beautify = _state.value.beautify,
                scanning = true,
            )
            _preview.value = PreviewUiState()
            recomputeCrop()
            requestRender()
            // Automatic by default: opening a screenshot starts the scan. The toggle exists for
            // people who want to look at the image before anything touches it.
            if (SettingsStore.instance.autoScan.value || autoPrepare) {
                scan()
            } else {
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    fun retryScan() {
        if (_state.value.source == null) return
        _state.update { it.copy(scanning = true, warnings = emptyList()) }
        viewModelScope.launch { scan() }
    }

    private suspend fun scan() {
        val source = _state.value.source ?: return
        val settings = SettingsStore.instance
        val analysis = withContext(Dispatchers.Default) { Bitmaps.scaledDown(source, ANALYSIS_MAX_DIM) }
        analysisBitmap = analysis
        val options = ScanOptions(
            detectCodes = true,
            detectFaces = settings.detectFaces.value,
            includeLongNumbers = settings.longNumbers.value,
        )
        val result = SensitiveScanner.scan(analysis, options) { partial ->
            // Publish text hits immediately: barcode and face passes finish seconds later.
            _state.update { current ->
                if (current.source !== source) current else current.copy(detections = partial)
            }
            requestRender()
        }
        // "Redact everything found" is the default: every hit is switched on and the user only has
        // to take boxes away. Turning it off starts with every box off instead.
        val autoSelect = SettingsStore.instance.autoSelectAll.value
        _state.update { current ->
            if (current.source !== source) return@update current
            current.copy(
                detections = result.detections,
                scanning = false,
                scanDurationMs = result.durationMs,
                warnings = result.warnings,
                disabledDetectionIds = if (autoSelect) {
                    emptySet()
                } else {
                    result.detections.map { it.id }.toSet()
                },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        requestRender()
        // One-tap path: the scan just finished, so continue straight to the verified export.
        if (autoPrepare) {
            autoPrepare = false
            preparePreview()
        }
    }

    // --- Editing --------------------------------------------------------------------------

    fun toggleDetection(id: String) {
        pushHistory()
        _state.update { current ->
            val disabled = current.disabledDetectionIds.toMutableSet()
            if (!disabled.add(id)) disabled.remove(id)
            current.copy(disabledDetectionIds = disabled)
        }
        requestRender()
    }

    fun toggleKind(kind: SensitiveKind) {
        pushHistory()
        _state.update { current ->
            val disabled = current.disabledKinds.toMutableSet()
            if (!disabled.add(kind)) disabled.remove(kind)
            current.copy(disabledKinds = disabled)
        }
        requestRender()
    }

    fun addManualRegion(bounds: NormRect) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        pushHistory()
        val region = ManualRegion(id = "manual-${System.nanoTime()}", bounds = bounds.clampToUnit())
        _state.update { it.copy(manualRegions = it.manualRegions + region, selectedManualId = region.id) }
        requestRender()
    }

    fun selectManualRegion(id: String?) {
        _state.update { it.copy(selectedManualId = id) }
    }

    fun updateManualRegion(id: String, bounds: NormRect) {
        _state.update { current ->
            current.copy(
                manualRegions = current.manualRegions.map { region ->
                    if (region.id == id) region.copy(bounds = bounds.clampToUnit()) else region
                },
            )
        }
        requestRender()
    }

    fun removeManualRegion(id: String) {
        pushHistory()
        _state.update { current ->
            current.copy(
                manualRegions = current.manualRegions.filterNot { it.id == id },
                selectedManualId = current.selectedManualId.takeIf { it != id },
            )
        }
        requestRender()
    }

    fun clearManualRegions() {
        if (_state.value.manualRegions.isEmpty()) return
        pushHistory()
        _state.update { it.copy(manualRegions = emptyList(), selectedManualId = null) }
        requestRender()
    }

    /** "Fix everything" — re-enable every detection and hide all of it in one action. */
    fun enableEverything() {
        pushHistory()
        _state.update {
            it.copy(
                disabledDetectionIds = emptySet(),
                disabledKinds = emptySet(),
            )
        }
        requestRender()
    }

    fun resetAll() {
        pushHistory()
        _state.update {
            it.copy(
                disabledDetectionIds = emptySet(),
                disabledKinds = EditorUiState.defaultDisabledKinds(),
                manualRegions = emptyList(),
                selectedManualId = null,
                cropConfig = CropConfig(),
                beautify = BeautifyConfig(),
            )
        }
        recomputeCrop()
        requestRender()
    }

    /** Called before a gesture that will emit many updates, so undo restores the start state. */
    fun beginInteractiveEdit() {
        pushHistory()
    }

    fun setStyle(style: RedactionStyle) {
        if (_state.value.style == style) return
        _state.update { it.copy(style = style) }
        SettingsStore.instance.setLastStyle(style.id)
        requestRender()
    }

    fun setStrength(strength: Float) {
        val clamped = strength.coerceIn(0f, 1f)
        _state.update { it.copy(strength = clamped) }
        SettingsStore.instance.setLastStrength(clamped)
        requestRender()
    }

    fun setTintColor(color: Int) {
        _state.update { it.copy(tintColor = color) }
        requestRender()
    }

    fun setCropConfig(config: CropConfig) {
        _state.update { it.copy(cropConfig = config) }
        recomputeCrop()
        requestRender()
    }

    fun setBeautify(transform: (BeautifyConfig) -> BeautifyConfig) {
        _state.update { it.copy(beautify = transform(it.beautify)) }
        requestRender()
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(currentSnapshot())
        applySnapshot(snapshot)
    }

    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(currentSnapshot())
        applySnapshot(snapshot)
    }

    // --- Rendering ------------------------------------------------------------------------

    private fun requestRender() {
        renderTrigger.update { it + 1 }
    }

    /** Called by the editor once the window insets are known. */
    fun setDeviceInsets(statusBarPx: Int, navigationBarPx: Int) {
        if (statusBarPx == statusBarInsetPx && navigationBarPx == navigationBarInsetPx) return
        statusBarInsetPx = statusBarPx
        navigationBarInsetPx = navigationBarPx
        recomputeCrop()
        requestRender()
    }

    private fun recomputeCrop() {
        val source = _state.value.source ?: return
        val config = _state.value.cropConfig
        val screenWidth = getApplication<Application>().resources.displayMetrics.widthPixels
        val result = AutoTrim.estimate(
            sampler = BitmapSampler(source),
            config = config,
            statusBarPx = AutoTrim.scaleInset(statusBarInsetPx, source.width, screenWidth),
            navBarPx = AutoTrim.scaleInset(navigationBarInsetPx, source.width, screenWidth),
        )
        // Detections are normalized to the source image, so changing the crop never invalidates
        // them: they simply move with the visible area.
        _state.update { it.copy(cropRect = result.rect.sanitized(source.width, source.height)) }
    }

    private suspend fun renderPreview() {
        val snapshot = _state.value
        val source = snapshot.source ?: return
        if (snapshot.phase != EditorPhase.READY) return
        _state.update { it.copy(rendering = true) }
        val request = RenderPipeline.Request(
            source = source,
            crop = snapshot.cropRect.sanitized(source.width, source.height),
            regions = snapshot.activeRegions(),
            style = snapshot.style,
            strength = snapshot.strength,
            tintColor = snapshot.tintColor,
            beautify = snapshot.beautify,
            targetMaxDim = previewMaxDim(),
        )
        val rendered = withContext(Dispatchers.Default) {
            runCatching { RenderPipeline.renderDetailed(request) }.getOrNull()
        } ?: run {
            _state.update { it.copy(rendering = false) }
            return
        }
        _state.update { current ->
            if (current.source !== source) {
                rendered.bitmap.recycle()
                current
            } else {
                current.copy(
                    preview = rendered.bitmap,
                    previewContentRect = rendered.contentRect,
                    previewRedactedRects = rendered.redactedRects,
                    rendering = false,
                )
            }
        }
    }

    private fun previewMaxDim(): Int {
        val metrics = getApplication<Application>().resources.displayMetrics
        return RenderPipeline.previewMaxDim(metrics.widthPixels, metrics.heightPixels)
    }

    // --- Export, share, verify ------------------------------------------------------------

    /** What one export looks like after rendering, verification and automatic repair. */
    private data class PreparedExport(
        val bitmap: Bitmap,
        val redactedRects: List<Rect>,
        val original: Bitmap?,
        val verify: VerifyResult?,
        /** Regions the automatic repair added, so the editor can show them too. */
        val repaired: List<NormRect>,
    )

    fun preparePreview() {
        val snapshot = _state.value
        val source = snapshot.source ?: return
        _preview.update { it.copy(exporting = true, error = null, verify = null, autoFixed = 0) }
        viewModelScope.launch {
            val settings = SettingsStore.instance
            val verifyEnabled = settings.autoVerify.value
            val autoFix = settings.autoFixLeftovers.value
            // Kinds the user switched off are neither reported nor repaired: the verdict has to
            // describe the redaction they asked for, not an imaginary ideal one.
            val ignoreKinds = snapshot.disabledKinds
            val request = RenderPipeline.Request(
                source = source,
                crop = snapshot.cropRect.sanitized(source.width, source.height),
                regions = snapshot.activeRegions(),
                style = snapshot.style,
                strength = snapshot.strength,
                tintColor = snapshot.tintColor,
                beautify = snapshot.beautify,
                targetMaxDim = 0,
            )
            val rendered = withContext(Dispatchers.Default) {
                runCatching {
                    renderVerified(
                        request = request,
                        previewRequest = request.copy(
                            regions = emptyList(),
                            targetMaxDim = previewMaxDim(),
                        ),
                        ignoreKinds = ignoreKinds,
                        verifyEnabled = verifyEnabled,
                        autoFix = autoFix && verifyEnabled,
                        checkFaces = settings.detectFaces.value,
                    )
                }
            }
            val result = rendered.getOrElse { error ->
                _preview.update {
                    it.copy(exporting = false, error = error.message ?: "render failed")
                }
                return@launch
            }
            // Same reason as releasePreview: never recycle while an export is encoding.
            exportMutex.withLock { clearPreviewBitmaps() }
            exportedBitmap = result.bitmap
            if (result.repaired.isNotEmpty()) addAutoFixRegions(result.repaired)
            _preview.update {
                it.copy(
                    bitmap = result.bitmap,
                    original = result.original,
                    redactedRects = result.redactedRects,
                    exporting = false,
                    verifying = false,
                    verify = result.verify,
                    autoFixed = result.repaired.size,
                )
            }
        }
    }

    /**
     * Render → verify → repair → verify again, all on the device and without asking. The loop stops
     * on the first clean verdict, on the first round that finds nothing new to hide, or after
     * [MAX_AUTO_FIX_ROUNDS] so a pathological image cannot spin forever.
     */
    private suspend fun renderVerified(
        request: RenderPipeline.Request,
        previewRequest: RenderPipeline.Request,
        ignoreKinds: Set<SensitiveKind>,
        verifyEnabled: Boolean,
        autoFix: Boolean,
        checkFaces: Boolean,
    ): PreparedExport {
        var current = request
        var rendered = RenderPipeline.renderDetailed(current)
        val repaired = ArrayList<NormRect>()

        var verdict = if (verifyEnabled) {
            RedactionVerifier.verify(
                exported = rendered.bitmap,
                redactedRects = rendered.redactedRects,
                checkFaces = checkFaces,
                ignoreKinds = ignoreKinds,
            )
        } else {
            null
        }

        var rounds = 0
        while (autoFix && verdict != null && verdict.completed && !verdict.isClean &&
            rounds < MAX_AUTO_FIX_ROUNDS
        ) {
            val content = rendered.contentRect
            val additions = AutoFixPlanner.plan(
                leftovers = verdict.leftoverBounds,
                content = IntRect(content.left, content.top, content.right, content.bottom),
                crop = current.crop,
                sourceWidth = request.source.width,
                sourceHeight = request.source.height,
                exportedWidth = rendered.bitmap.width,
                exportedHeight = rendered.bitmap.height,
                existing = current.regions,
            )
            if (additions.isEmpty()) break

            repaired += additions
            val previous = rendered.bitmap
            current = current.copy(regions = current.regions + additions)
            rendered = RenderPipeline.renderDetailed(current)
            if (previous !== rendered.bitmap && !previous.isRecycled) previous.recycle()
            verdict = RedactionVerifier.verify(
                exported = rendered.bitmap,
                redactedRects = rendered.redactedRects,
                checkFaces = checkFaces,
                ignoreKinds = ignoreKinds,
            )
            rounds++
        }

        // A second, small pass without regions powers the before/after comparison.
        val original = runCatching { RenderPipeline.renderDetailed(previewRequest) }
            .getOrNull()
            ?.bitmap

        return PreparedExport(
            bitmap = rendered.bitmap,
            redactedRects = rendered.redactedRects,
            original = original,
            verify = verdict,
            repaired = repaired,
        )
    }

    /** Surfaces automatically repaired areas in the editor as ordinary, undoable regions. */
    private fun addAutoFixRegions(regions: List<NormRect>) {
        if (regions.isEmpty()) return
        pushHistory()
        val added = regions.map { bounds ->
            ManualRegion(id = "auto-${System.nanoTime()}-${bounds.hashCode()}", bounds = bounds)
        }
        _state.update { it.copy(manualRegions = it.manualRegions + added) }
    }

    /**
     * Frees the preview bitmaps. The export mutex is taken first on purpose: leaving the preview
     * while a save or share is still encoding used to recycle the pixels out from under the encoder,
     * which silently turned a successful tap into a failed export.
     */
    fun releasePreview() {
        viewModelScope.launch {
            exportMutex.withLock {
                clearPreviewBitmaps()
                _preview.value = PreviewUiState()
            }
        }
    }

    private fun clearPreviewBitmaps() {
        val current = _preview.value
        // The full-resolution export is separate from the preview copies, so all three are freed.
        listOfNotNull(current.bitmap, current.original).forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        exportedBitmap?.let { old -> if (!old.isRecycled) old.recycle() }
        exportedBitmap = null
    }

    /** Renders (if needed) and writes the file that the share sheet will hand over. */
    suspend fun exportForShare(
        options: ExportOptions = SettingsStore.instance.exportOptions(),
    ): Result<SharePayload> = exportMutex.withLock {
        val context: Context = getApplication<Application>()
        val bitmap = _preview.value.bitmap
            ?: exportedBitmap
            ?: return@withLock Result.failure(IllegalStateException("no export prepared"))
        runCatching { ImageExporter.exportForShare(context, bitmap, options) }
            .onSuccess {
                recordHistory(bitmap)
                SettingsStore.instance.recordShare()
            }
    }

    suspend fun saveToGallery(
        options: ExportOptions = SettingsStore.instance.exportOptions(),
    ): Result<String?> = exportMutex.withLock {
        val context: Context = getApplication<Application>()
        val bitmap = _preview.value.bitmap
            ?: exportedBitmap
            ?: return@withLock Result.failure(IllegalStateException("no export prepared"))
        runCatching { ImageExporter.saveToGallery(context, bitmap, options) }
            .onSuccess { recordHistory(bitmap) }
    }

    /**
     * Stores a private copy of the **redacted** result so History can show a thumbnail and re-share
     * it later. The unredacted source is never written to disk.
     */
    private suspend fun recordHistory(bitmap: Bitmap) {
        if (!SettingsStore.instance.keepHistory.value) return
        val snapshot = _state.value
        runCatching {
            HistoryStore.instance.record(
                bitmap = bitmap,
                sourceName = snapshot.sourceName,
                kinds = snapshot.detections
                    .filter { snapshot.isDetectionActive(it) }
                    .map { it.kind.name },
                regionCount = snapshot.activeRegionCount,
                verified = _preview.value.verify?.isClean == true,
                transparent = snapshot.beautify.enabled && snapshot.beautify.cornerFraction > 0f,
            )
        }
    }

    // --- History --------------------------------------------------------------------------

    private fun currentSnapshot(): EditSnapshot = with(_state.value) {
        EditSnapshot(manualRegions, disabledDetectionIds, disabledKinds)
    }

    private fun pushHistory() {
        undoStack.addLast(currentSnapshot())
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        _state.update { it.copy(canUndo = true, canRedo = false) }
    }

    private fun applySnapshot(snapshot: EditSnapshot) {
        _state.update {
            it.copy(
                manualRegions = snapshot.manualRegions,
                disabledDetectionIds = snapshot.disabledDetectionIds,
                disabledKinds = snapshot.disabledKinds,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        requestRender()
    }

    private fun resetHistory() {
        undoStack.clear()
        redoStack.clear()
        _state.update { it.copy(canUndo = false, canRedo = false) }
    }

    override fun onCleared() {
        analysisBitmap?.let { if (!it.isRecycled) it.recycle() }
        exportedBitmap?.let { if (!it.isRecycled) it.recycle() }
        analysisBitmap = null
        exportedBitmap = null
        super.onCleared()
    }

    private fun initialStyle(): RedactionStyle =
        RedactionStyle.fromId(SettingsStore.instance.lastStyle.value)

    /**
     * The disabled set follows the user's detection defaults: faces and long digit runs are only
     * on when they said so in Settings.
     */
    private fun initialDisabledKinds(): Set<SensitiveKind> {
        val settings = SettingsStore.instance
        val disabled = EditorUiState.defaultDisabledKinds().toMutableSet()
        if (settings.detectFaces.value) disabled.remove(SensitiveKind.FACE)
        if (settings.longNumbers.value) disabled.remove(SensitiveKind.LONG_NUMBER)
        if (!settings.extraHeuristics.value) {
            disabled += SensitiveKind.NETWORK
            disabled += SensitiveKind.PLATE
        }
        return disabled
    }

    private companion object {
        const val ANALYSIS_MAX_DIM = 1600
        const val MAX_HISTORY = 60

        /** Two repair rounds fix stacked leftovers without ever looping on a stubborn image. */
        const val MAX_AUTO_FIX_ROUNDS = 2
    }
}
