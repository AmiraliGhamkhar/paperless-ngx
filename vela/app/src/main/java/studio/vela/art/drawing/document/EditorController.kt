package studio.vela.art.drawing.document

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import studio.vela.art.core.input.InputPolicy
import studio.vela.art.core.input.PointerSample
import studio.vela.art.drawing.brush.BrushLibrary
import studio.vela.art.drawing.brush.DabGenerator
import studio.vela.art.drawing.history.BitmapEdit
import studio.vela.art.drawing.history.LayerAddEdit
import studio.vela.art.drawing.history.LayerPropsEdit
import studio.vela.art.drawing.history.LayerRemoveEdit
import studio.vela.art.drawing.history.LayerReorderEdit
import studio.vela.art.drawing.history.MergeDownEdit
import studio.vela.art.drawing.layers.Layer
import studio.vela.art.drawing.layers.LayerProps
import studio.vela.art.drawing.paint.StrokeSession
import studio.vela.art.drawing.render.DabRenderer
import studio.vela.art.drawing.tools.FloodFill
import studio.vela.art.domain.model.BlendKind
import studio.vela.art.domain.model.BrushEngineKind
import studio.vela.art.domain.model.BrushPreset
import kotlin.random.Random

/**
 * Owns all interactive editing state for one open document: selected tool, colours, the live
 * stroke session, and every undoable operation.
 *
 * Deliberately not a ViewModel: the custom canvas view needs synchronous access during
 * pointer input, while the ViewModel only mirrors this state for Compose. All calls happen on
 * the main thread.
 */
class EditorController(
    val document: ArtDocument,
    initialColor: Int = Color.BLACK,
) {
    private val renderer = DabRenderer()

    var policy: InputPolicy = InputPolicy()
        set(value) {
            field = value
            onStateChanged?.invoke()
        }

    var tool: ToolKind = ToolKind.BRUSH
        private set

    /** Per-tool brush slots: switching tools restores the brush last used for that tool. */
    private val toolBrushes: MutableMap<ToolKind, BrushPreset> = linkedMapOf(
        ToolKind.BRUSH to (BrushLibrary.byId("gouache-opaque") ?: BrushLibrary.default),
        ToolKind.PENCIL to (BrushLibrary.byId("graphite-pencil") ?: BrushLibrary.default),
        ToolKind.PEN to (BrushLibrary.byId("ink-liner") ?: BrushLibrary.default),
        ToolKind.ERASER to BrushLibrary.defaultEraser,
        ToolKind.SMUDGE to (BrushLibrary.byId("smudge-blender") ?: BrushLibrary.default),
    )

    var primaryColor: Int = initialColor
        private set
    var secondaryColor: Int = Color.WHITE
        private set

    var shapeKind: ShapeKind = ShapeKind.LINE
    var shapeFilled: Boolean = false
    var fillTolerance: Float = 0.12f
    var fillContiguous: Boolean = true
    var sampleMergedForEyedropper: Boolean = false

    var shapePreview: ShapePreview? = null
        private set

    private var generator: DabGenerator? = null
    private var session: StrokeSession? = null
    private var lastPressure: Float = 0f

    var onInvalidate: (() -> Unit)? = null
    var onStateChanged: (() -> Unit)? = null
    var onColorPicked: ((Int) -> Unit)? = null
    var onEditCommitted: ((String) -> Unit)? = null

    val isDrawing: Boolean get() = session != null
    val activeStrokeSession: StrokeSession? get() = session
    val activeBrush: BrushPreset get() = toolBrushes[tool] ?: BrushLibrary.default
    val currentColor: Int get() = primaryColor

    // ---------------------------------------------------------------- tool & brush state

    fun selectTool(newTool: ToolKind) {
        if (tool == newTool) return
        cancelInput()
        tool = newTool
        shapePreview = null
        onStateChanged?.invoke()
        onInvalidate?.invoke()
    }

    /** Applies a library or custom preset to the active tool. */
    fun selectBrush(preset: BrushPreset) {
        val target = if (tool.isPaintTool) tool else ToolKind.BRUSH
        if (tool != target) tool = target
        toolBrushes[target] = preset.sanitized()
        onStateChanged?.invoke()
    }

    fun updateActiveBrush(label: String, transform: (BrushPreset) -> BrushPreset) {
        val target = if (tool.isPaintTool) tool else ToolKind.BRUSH
        val current = toolBrushes[target] ?: BrushLibrary.default
        val updated = transform(current).sanitized()
        toolBrushes[target] = updated
        onStateChanged?.invoke()
        // Live-update the running stroke so parameter changes are felt immediately.
        generator?.updatePreset(updated)
    }

    fun setPrimaryColor(color: Int) {
        primaryColor = color
        onStateChanged?.invoke()
    }

    fun setSecondaryColor(color: Int) {
        secondaryColor = color
        onStateChanged?.invoke()
    }

    fun swapColors() {
        val tmp = primaryColor
        primaryColor = secondaryColor
        secondaryColor = tmp
        onStateChanged?.invoke()
    }

    // ---------------------------------------------------------------- pointer input

    /** @return true when the input started a painting/editing operation. */
    fun beginInput(sample: PointerSample, eraserStylus: Boolean = false): Boolean {
        lastPressure = sample.pressure
        val effectiveTool = if (eraserStylus && policy.useEraserFor(sample.tool)) ToolKind.ERASER else tool
        when (effectiveTool) {
            ToolKind.EYEDROPPER -> {
                val picked = pickColorAt(sample.x, sample.y)
                if (picked != null) {
                    primaryColor = picked
                    onColorPicked?.invoke(picked)
                    onStateChanged?.invoke()
                }
                return false
            }

            ToolKind.FILL -> {
                performFill(sample.x, sample.y)
                return false
            }

            ToolKind.SHAPE -> {
                shapePreview = ShapePreview(sample.x, sample.y, sample.x, sample.y)
                onInvalidate?.invoke()
                return true
            }

            else -> {
                return startStroke(sample, effectiveTool)
            }
        }
    }

    fun continueInput(samples: List<PointerSample>) {
        if (samples.isEmpty()) return
        val shape = shapePreview
        if (shape != null) {
            val last = samples.last()
            shapePreview = shape.copy(endX = last.x, endY = last.y)
            onInvalidate?.invoke()
            return
        }
        val gen = generator ?: return
        val current = session ?: return
        var stamped = false
        for (sample in samples) {
            lastPressure = sample.pressure
            val dabs = gen.add(sample.x, sample.y, sample.pressure, sample.tiltRadians, sample.timeMs)
            for (dab in dabs) {
                current.stamp(dab)
                stamped = true
            }
        }
        if (stamped) {
            if (current.shouldSplit()) splitStroke()
            onInvalidate?.invoke()
        }
    }

    fun endInput() {
        val shape = shapePreview
        if (shape != null) {
            shapePreview = null
            rasteriseShape(shape)
            onInvalidate?.invoke()
            return
        }
        finishStroke()
    }

    fun cancelInput() {
        shapePreview = null
        session?.cancel()
        session = null
        generator = null
        onInvalidate?.invoke()
    }

    // ---------------------------------------------------------------- strokes

    private fun startStroke(sample: PointerSample, effectiveTool: ToolKind): Boolean {
        val layer = document.activeLayer() ?: return false
        if (!layer.isPaintable()) return false
        val preset = (toolBrushes[effectiveTool] ?: BrushLibrary.default).sanitized()
        val direct = preset.engine == BrushEngineKind.ERASER || preset.engine == BrushEngineKind.SMUDGE
        val color = if (effectiveTool == ToolKind.ERASER) Color.WHITE else primaryColor
        val newSession = StrokeSession(layer, preset, color, renderer, direct)
        val newGenerator = DabGenerator(preset, Random(System.nanoTime()))
        session = newSession
        generator = newGenerator
        newGenerator.begin(sample.x, sample.y, sample.pressure.coerceAtLeast(0.02f), sample.tiltRadians, sample.timeMs)
            .forEach { newSession.stamp(it) }
        onInvalidate?.invoke()
        return true
    }

    private fun splitStroke() {
        val current = session ?: return
        val label = if (current.preset.isEraser) "Erase" else current.preset.name
        val edit = current.commit(label) ?: return
        document.history.push(edit)
        val layer = document.activeLayer() ?: return
        val preset = current.preset
        val direct = preset.engine == BrushEngineKind.ERASER || preset.engine == BrushEngineKind.SMUDGE
        session = StrokeSession(layer, preset, primaryColor, renderer, direct)
        onEditCommitted?.invoke(label)
    }

    private fun finishStroke() {
        val current = session ?: return
        val label = if (current.preset.isEraser) "Erase" else current.preset.name
        val edit = current.commit(label)
        session = null
        generator = null
        if (edit != null) {
            document.history.push(edit)
            document.notifyContentChanged()
            onEditCommitted?.invoke(label)
        }
        onStateChanged?.invoke()
        onInvalidate?.invoke()
    }

    // ---------------------------------------------------------------- history

    fun undo(): Boolean {
        if (!document.history.undo(document)) return false
        document.notifyContentChanged()
        onStateChanged?.invoke()
        onInvalidate?.invoke()
        return true
    }

    fun redo(): Boolean {
        if (!document.history.redo(document)) return false
        document.notifyContentChanged()
        onStateChanged?.invoke()
        onInvalidate?.invoke()
        return true
    }

    // ---------------------------------------------------------------- layer operations

    fun addLayer(): Layer {
        cancelInput()
        val active = document.activeLayer()
        val layer = document.createLayer(above = active)
        document.history.push(LayerAddEdit(layer, document.stack.indexOf(layer)))
        document.notifyStructureChanged()
        onStateChanged?.invoke()
        onInvalidate?.invoke()
        return layer
    }

    fun duplicateActiveLayer(): Boolean {
        val active = document.activeLayer() ?: return false
        val copy = document.stack.duplicate(active) ?: return false
        document.activeLayerId = copy.id
        document.history.push(LayerAddEdit(copy, document.stack.indexOf(copy), "Duplicate layer"))
        document.notifyStructureChanged()
        onStateChanged?.invoke()
        onInvalidate?.invoke()
        return true
    }

    fun deleteActiveLayer(): Boolean {
        if (document.stack.size <= 1) return false
        val active = document.activeLayer() ?: return false
        val index = document.stack.indexOf(active)
        if (index < 0) return false
        document.stack.remove(active)
        document.activeLayerId = document.stack.all().getOrNull((index - 1).coerceAtLeast(0))?.id
            ?: document.stack.all().lastOrNull()?.id
        document.history.push(LayerRemoveEdit(active, index))
        document.notifyStructureChanged()
        onStateChanged?.invoke()
        onInvalidate?.invoke()
        return true
    }

    fun mergeActiveLayerDown(): Boolean {
        val active = document.activeLayer() ?: return false
        val index = document.stack.indexOf(active)
        if (index <= 0) return false
        val target = document.stack.get(index - 1) ?: return false
        if (target.locked) return false
        val targetBefore = target.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return false
        val removedOpacity = active.opacity
        val removedBlend = active.blend
        val merged = document.stack.mergeDown(active) ?: return false
        document.activeLayerId = merged.id
        document.history.push(
            MergeDownEdit(
                targetId = target.id,
                removed = active,
                removedIndex = index,
                removedOpacity = removedOpacity,
                removedBlend = removedBlend,
                targetBefore = targetBefore,
            ),
        )
        document.notifyStructureChanged()
        onStateChanged?.invoke()
        onInvalidate?.invoke()
        return true
    }

    fun moveActiveLayer(up: Boolean): Boolean {
        val active = document.activeLayer() ?: return false
        val from = document.stack.indexOf(active)
        if (from < 0) return false
        val to = if (up) from + 1 else from - 1
        if (to < 0 || to >= document.stack.size) return false
        document.stack.move(from, to)
        document.history.push(LayerReorderEdit(active.id, from, to))
        document.notifyStructureChanged()
        onStateChanged?.invoke()
        onInvalidate?.invoke()
        return true
    }

    fun selectLayer(layerId: String) {
        document.activeLayerId = layerId
        onStateChanged?.invoke()
        onInvalidate?.invoke()
    }

    fun captureProps(layerId: String): LayerProps? = document.stack.findById(layerId)?.let { LayerProps.of(it) }

    /** Live property change without touching history (use with [commitProps]). */
    fun updateLayerProps(layerId: String, transform: (Layer) -> Unit) {
        val layer = document.stack.findById(layerId) ?: return
        transform(layer)
        document.markDirty()
        onInvalidate?.invoke()
    }

    /** Commits a property change as a single undoable step. */
    fun commitProps(layerId: String, before: LayerProps?, label: String) {
        val layer = document.stack.findById(layerId) ?: return
        val after = LayerProps.of(layer)
        if (before == null || before == after) return
        document.history.push(LayerPropsEdit(layerId, before, after, label))
        document.notifyStructureChanged()
        onStateChanged?.invoke()
    }

    fun renameLayer(layerId: String, name: String) {
        val layer = document.stack.findById(layerId) ?: return
        val before = LayerProps.of(layer)
        layer.name = name.ifBlank { layer.name }
        commitProps(layerId, before, "Rename layer")
    }

    fun clearActiveLayer(): Boolean {
        val layer = document.activeLayer() ?: return false
        if (!document.stack.canClear(layer)) return false
        val before = layer.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return false
        layer.canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
        layer.markThumbnailDirty()
        val after = layer.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return false
        document.history.push(BitmapEdit(layer.id, before, after, "Clear layer"))
        document.notifyContentChanged()
        onStateChanged?.invoke()
        onInvalidate?.invoke()
        return true
    }

    fun setLayerBlend(layerId: String, blend: BlendKind) {
        val layer = document.stack.findById(layerId) ?: return
        val before = LayerProps.of(layer)
        layer.blend = blend
        commitProps(layerId, before, "Blend mode")
    }

    // ---------------------------------------------------------------- tools

    private fun performFill(docX: Float, docY: Float): Boolean {
        val layer = document.activeLayer() ?: return false
        if (!layer.isPaintable()) return false
        val before = layer.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return false
        val changed = FloodFill.fill(
            bitmap = layer.bitmap,
            seedX = docX.toInt(),
            seedY = docY.toInt(),
            color = primaryColor,
            tolerance = fillTolerance,
            alphaLock = layer.alphaLocked,
            contiguous = fillContiguous,
        )
        if (changed == null) {
            before.recycle()
            return false
        }
        layer.markThumbnailDirty()
        val after = layer.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return false
        document.history.push(BitmapEdit(layer.id, before, after, "Fill"))
        document.notifyContentChanged()
        onEditCommitted?.invoke("Fill")
        onStateChanged?.invoke()
        onInvalidate?.invoke()
        return true
    }

    private fun rasteriseShape(shape: ShapePreview) {
        val layer = document.activeLayer() ?: return
        if (!layer.isPaintable()) return
        val dx = kotlin.math.abs(shape.endX - shape.startX)
        val dy = kotlin.math.abs(shape.endY - shape.startY)
        if (dx < 1f && dy < 1f) return
        val before = layer.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = if (shapeFilled) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = activeBrush.size.coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (layer.alphaLocked) {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
            }
        }
        val rect = RectF(
            minOf(shape.startX, shape.endX),
            minOf(shape.startY, shape.endY),
            maxOf(shape.startX, shape.endX),
            maxOf(shape.startY, shape.endY),
        )
        when (shapeKind) {
            ShapeKind.LINE -> layer.canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
            ShapeKind.RECTANGLE -> layer.canvas.drawRect(rect, paint)
            ShapeKind.ELLIPSE -> layer.canvas.drawOval(rect, paint)
        }
        layer.markThumbnailDirty()
        val after = layer.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: return
        document.history.push(BitmapEdit(layer.id, before, after, shapeKind.label))
        document.notifyContentChanged()
        onEditCommitted?.invoke(shapeKind.label)
        onStateChanged?.invoke()
    }

    /** Samples colour under the pointer, top-most non-transparent layer first. */
    fun pickColorAt(docX: Float, docY: Float): Int? {
        val x = docX.toInt()
        val y = docY.toInt()
        if (x < 0 || y < 0 || x >= document.width || y >= document.height) return null
        if (sampleMergedForEyedropper) {
            val composite = document.composite(includeBackground = true)
            val pixel = composite.getPixel(x, y)
            composite.recycle()
            return pixel
        }
        val layers = document.stack.all()
        for (i in layers.indices.reversed()) {
            val layer = layers[i]
            if (!layer.visible) continue
            val pixel = layer.bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 0) return pixel
        }
        return document.background
    }

    fun release() {
        cancelInput()
        renderer.clearCache()
    }
}

/** Live shape drag state; rendered by the canvas view as a vector preview. */
data class ShapePreview(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)
