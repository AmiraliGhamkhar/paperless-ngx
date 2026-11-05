package studio.vela.art.presentation.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import studio.vela.art.data.repository.ProjectRepository
import studio.vela.art.data.settings.AppSettings
import studio.vela.art.data.settings.SettingsRepository
import studio.vela.art.data.settings.toInputPolicy
import studio.vela.art.drawing.brush.BrushLibrary
import studio.vela.art.drawing.document.ArtDocument
import studio.vela.art.drawing.document.EditorController
import studio.vela.art.drawing.document.ShapeKind
import studio.vela.art.drawing.document.ToolKind
import studio.vela.art.drawing.layers.Layer
import studio.vela.art.drawing.layers.LayerProps
import studio.vela.art.domain.model.BlendKind
import studio.vela.art.domain.model.BrushPreset

enum class SaveState { SAVED, UNSAVED, SAVING, ERROR }

data class LayerRow(
    val id: String,
    val name: String,
    val opacity: Float,
    val blend: BlendKind,
    val visible: Boolean,
    val locked: Boolean,
    val alphaLocked: Boolean,
    val clipping: Boolean,
    val isActive: Boolean,
    val thumbnail: Bitmap?,
)

data class EditorUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val projectName: String = "",
    val canvasWidth: Int = 0,
    val canvasHeight: Int = 0,
    val canvasDpi: Int = 0,
    val tool: ToolKind = ToolKind.BRUSH,
    val brush: BrushPreset = BrushLibrary.default,
    val primaryColor: Int = 0xFF111111.toInt(),
    val secondaryColor: Int = 0xFFFFFFFF.toInt(),
    val shapeKind: ShapeKind = ShapeKind.LINE,
    val shapeFilled: Boolean = false,
    val fillTolerance: Float = 0.12f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
    val layers: List<LayerRow> = emptyList(),
    val activeLayerId: String? = null,
    val saveState: SaveState = SaveState.SAVED,
    val lastSavedAt: Long = 0L,
    val zoomPercent: Float = 100f,
    val recoveryNote: String? = null,
    val message: String? = null,
    val historyBytes: Long = 0L,
)

/**
 * Drives the editor: owns the document via [EditorController], mirrors its state for Compose,
 * and runs autosave independently of the painting loop.
 */
class EditorViewModel(
    private val repository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val projectId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    var controller: EditorController? = null
        private set

    private var settings: AppSettings = AppSettings()
    private var autosaveJob: Job? = null
    private var settingsJob: Job? = null

    init {
        settingsJob = viewModelScope.launch {
            settingsRepository.settings.collect { loaded ->
                settings = loaded
                controller?.policy = loaded.toInputPolicy()
                scheduleAutosave()
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = repository.openProject(projectId)) {
                is ProjectRepository.OpenResult.Success -> {
                    val doc = result.document
                    val editor = EditorController(doc)
                    editor.policy = settings.toInputPolicy()
                    editor.setPrimaryColor(settings.lastColorArgb)
                    editor.onStateChanged = { refreshState() }
                    editor.onEditCommitted = { label ->
                        _state.value = _state.value.copy(message = null, lastSavedAt = _state.value.lastSavedAt)
                        markUnsaved()
                        scheduleAutosave()
                        refreshLayers()
                        // Keep the label available for the UI status line.
                        _state.value = _state.value.copy(undoLabel = label)
                    }
                    editor.onColorPicked = { color ->
                        viewModelScope.launch { settingsRepository.update { it.copy(lastColorArgb = color) } }
                    }
                    doc.onContentChanged = { markUnsaved() }
                    controller = editor
                    _state.value = _state.value.copy(
                        loading = false,
                        projectName = doc.name,
                        canvasWidth = doc.width,
                        canvasHeight = doc.height,
                        canvasDpi = doc.dpi,
                        primaryColor = editor.primaryColor,
                        secondaryColor = editor.secondaryColor,
                        saveState = SaveState.SAVED,
                        lastSavedAt = doc.lastSavedAtMillis,
                        recoveryNote = result.recoveryNote,
                    )
                    refreshState()
                }

                is ProjectRepository.OpenResult.Failure -> {
                    _state.value = _state.value.copy(loading = false, loadError = result.reason)
                }
            }
        }
    }

    /** Rebuilds the Compose-facing snapshot from the controller and document. */
    fun refreshState() {
        val editor = controller ?: return
        val doc = editor.document
        _state.value = _state.value.copy(
            projectName = doc.name,
            tool = editor.tool,
            brush = editor.activeBrush,
            primaryColor = editor.primaryColor,
            secondaryColor = editor.secondaryColor,
            shapeKind = editor.shapeKind,
            shapeFilled = editor.shapeFilled,
            fillTolerance = editor.fillTolerance,
            canUndo = doc.history.canUndo,
            canRedo = doc.history.canRedo,
            undoLabel = doc.history.undoLabel,
            redoLabel = doc.history.redoLabel,
            activeLayerId = doc.activeLayerId,
            historyBytes = doc.history.bytesUsed,
            layers = layerRows(doc),
        )
    }

    private fun refreshLayers() {
        val doc = controller?.document ?: return
        _state.value = _state.value.copy(layers = layerRows(doc), activeLayerId = doc.activeLayerId)
    }

    private fun layerRows(doc: ArtDocument): List<LayerRow> =
        doc.stack.all().asReversed().map { layer: Layer ->
            LayerRow(
                id = layer.id,
                name = layer.name,
                opacity = layer.opacity,
                blend = layer.blend,
                visible = layer.visible,
                locked = layer.locked,
                alphaLocked = layer.alphaLocked,
                clipping = layer.clipping,
                isActive = layer.id == doc.activeLayerId,
                thumbnail = layer.ensureThumbnail(96),
            )
        }

    // ------------------------------------------------------------------ tools & colours

    fun selectTool(tool: ToolKind) {
        controller?.selectTool(tool)
        refreshState()
    }

    fun selectBrush(preset: BrushPreset) {
        controller?.selectBrush(preset)
        refreshState()
    }

    fun updateBrush(label: String, transform: (BrushPreset) -> BrushPreset) {
        controller?.updateActiveBrush(label, transform)
        refreshState()
    }

    fun setPrimaryColor(color: Int) {
        controller?.setPrimaryColor(color)
        _state.value = _state.value.copy(primaryColor = color)
        viewModelScope.launch { settingsRepository.update { it.copy(lastColorArgb = color) } }
    }

    fun setSecondaryColor(color: Int) {
        controller?.setSecondaryColor(color)
        _state.value = _state.value.copy(secondaryColor = color)
    }

    fun swapColors() {
        controller?.swapColors()
        refreshState()
    }

    fun setShapeKind(kind: ShapeKind) {
        controller?.shapeKind = kind
        _state.value = _state.value.copy(shapeKind = kind)
    }

    fun setShapeFilled(filled: Boolean) {
        controller?.shapeFilled = filled
        _state.value = _state.value.copy(shapeFilled = filled)
    }

    fun setFillTolerance(value: Float) {
        controller?.fillTolerance = value
        _state.value = _state.value.copy(fillTolerance = value)
    }

    fun setZoom(percent: Float) {
        _state.value = _state.value.copy(zoomPercent = percent)
    }

    // ------------------------------------------------------------------ history

    fun undo() {
        controller?.undo()
        refreshState()
        markUnsaved()
        scheduleAutosave()
    }

    fun redo() {
        controller?.redo()
        refreshState()
        markUnsaved()
        scheduleAutosave()
    }

    // ------------------------------------------------------------------ layers

    fun addLayer() {
        controller?.addLayer()
        refreshState()
        markUnsaved()
        scheduleAutosave()
    }

    fun duplicateLayer() {
        controller?.duplicateActiveLayer()
        refreshState()
        markUnsaved()
        scheduleAutosave()
    }

    fun deleteLayer() {
        if (controller?.deleteActiveLayer() == true) {
            refreshState()
            markUnsaved()
            scheduleAutosave()
        } else {
            _state.value = _state.value.copy(message = "A project needs at least one layer")
        }
    }

    fun mergeLayerDown() {
        if (controller?.mergeActiveLayerDown() == true) {
            refreshState()
            markUnsaved()
            scheduleAutosave()
        } else {
            _state.value = _state.value.copy(message = "Nothing to merge into")
        }
    }

    fun moveLayer(up: Boolean) {
        controller?.moveActiveLayer(up)
        refreshState()
        markUnsaved()
        scheduleAutosave()
    }

    fun selectLayer(id: String) {
        controller?.selectLayer(id)
        refreshState()
    }

    fun renameLayer(id: String, name: String) {
        controller?.renameLayer(id, name)
        refreshState()
        scheduleAutosave()
    }

    fun captureLayerProps(id: String): LayerProps? = controller?.captureProps(id)

    fun updateLayerProps(id: String, transform: (Layer) -> Unit) {
        controller?.updateLayerProps(id, transform)
        refreshLayers()
        markUnsaved()
    }

    fun commitLayerProps(id: String, before: LayerProps?, label: String) {
        controller?.commitProps(id, before, label)
        refreshState()
        scheduleAutosave()
    }

    fun setLayerBlend(id: String, blend: BlendKind) {
        controller?.setLayerBlend(id, blend)
        refreshState()
        scheduleAutosave()
    }

    fun clearLayer() {
        controller?.clearActiveLayer()
        refreshState()
        markUnsaved()
        scheduleAutosave()
    }

    fun removeMessage() {
        _state.value = _state.value.copy(message = null, recoveryNote = null)
    }

    // ------------------------------------------------------------------ persistence

    private fun markUnsaved() {
        if (_state.value.saveState != SaveState.UNSAVED) {
            _state.value = _state.value.copy(saveState = SaveState.UNSAVED)
        }
    }

    /** Debounced autosave; never runs while a stroke is in flight. */
    fun scheduleAutosave() {
        autosaveJob?.cancel()
        val intervalMs = settings.autosaveSeconds.coerceIn(1, 120) * 1000L
        autosaveJob = viewModelScope.launch {
            delay(intervalMs)
            saveNow()
        }
    }

    fun saveNow() {
        val editor = controller ?: return
        if (editor.isDrawing) {
            scheduleAutosave()
            return
        }
        if (_state.value.saveState == SaveState.SAVING) return
        _state.value = _state.value.copy(saveState = SaveState.SAVING)
        viewModelScope.launch {
            val ok = repository.save(editor.document)
            _state.value = _state.value.copy(
                saveState = if (ok) SaveState.SAVED else SaveState.ERROR,
                lastSavedAt = if (ok) System.currentTimeMillis() else _state.value.lastSavedAt,
                message = if (ok) _state.value.message else "Could not save the project",
            )
            if (ok) refreshLayers()
        }
    }

    fun renameProject(name: String) {
        val editor = controller ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameProject(projectId, trimmed)
            editor.document.name = trimmed
            refreshState()
        }
    }

    fun document(): ArtDocument? = controller?.document

    override fun onCleared() {
        autosaveJob?.cancel()
        settingsJob?.cancel()
        controller?.release()
        super.onCleared()
    }

    class Factory(
        private val repository: ProjectRepository,
        private val settingsRepository: SettingsRepository,
        private val projectId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditorViewModel(repository, settingsRepository, projectId) as T
    }
}
