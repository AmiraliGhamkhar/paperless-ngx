package studio.vela.art.drawing.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import studio.vela.art.drawing.history.HistoryStack
import studio.vela.art.drawing.layers.Layer
import studio.vela.art.drawing.layers.LayerCompositor
import studio.vela.art.drawing.layers.LayerStack
import java.util.UUID

/**
 * The editable artwork. Owns the layer stack, the undo history and document metadata.
 *
 * The UI never mutates layers directly: it goes through [EditorController], which produces
 * undoable edits on this document.
 */
class ArtDocument(
    val projectId: Long,
    var name: String,
    val width: Int,
    val height: Int,
    val dpi: Int,
    /** ARGB paper colour, or null for a transparent canvas. */
    val background: Int?,
    val stack: LayerStack = LayerStack(),
) {
    var activeLayerId: String? = stack.all().lastOrNull()?.id
    val history = HistoryStack()

    var isDirty: Boolean = false
        private set

    var lastSavedAtMillis: Long = 0L
        private set

    /** Raised whenever the rendered result may have changed. */
    var onContentChanged: (() -> Unit)? = null

    /** Raised when the layer list / properties changed (UI panels should refresh). */
    var onStructureChanged: (() -> Unit)? = null

    val megapixels: Float get() = (width.toLong() * height.toLong()) / 1_000_000f

    fun activeLayer(): Layer? {
        val id = activeLayerId ?: return stack.all().lastOrNull()
        return stack.findById(id) ?: stack.all().lastOrNull()
    }

    fun markDirty() {
        isDirty = true
    }

    fun markSaved() {
        isDirty = false
        lastSavedAtMillis = System.currentTimeMillis()
    }

    fun notifyContentChanged() {
        markDirty()
        onContentChanged?.invoke()
    }

    fun notifyStructureChanged() {
        markDirty()
        onStructureChanged?.invoke()
        onContentChanged?.invoke()
    }

    /** Adds a new empty layer above [above] (or at the top). */
    fun createLayer(name: String? = null, above: Layer? = null, fillColor: Int = 0): Layer {
        val index = if (above != null) stack.indexOf(above) + 1 else stack.size
        val bitmap = LayerStack.createBitmap(width, height)
        if (fillColor != 0) LayerStack.fillRect(bitmap, fillColor)
        val layer = Layer(
            id = UUID.randomUUID().toString(),
            name = name ?: nextLayerName(),
            bitmap = bitmap,
        )
        stack.add(layer, index)
        activeLayerId = layer.id
        return layer
    }

    fun nextLayerName(): String {
        var index = stack.size + 1
        val existing = stack.all().map { it.name }.toSet()
        while (existing.contains("Layer $index")) index++
        return "Layer $index"
    }

    /** Flattened bitmap including the paper background (null alpha when transparent). */
    fun composite(includeBackground: Boolean = true): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (includeBackground) {
            canvas.drawColor(background ?: Color.WHITE)
        }
        LayerCompositor.drawStack(canvas, stack.all(), width, height)
        return out
    }

    /** Small flattened preview for the gallery card. */
    fun thumbnail(maxSize: Int = 384): Bitmap {
        val scale = maxSize.toFloat() / maxOf(width, height)
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        val full = composite(includeBackground = true)
        val thumb = Bitmap.createScaledBitmap(full, w, h, true)
        if (thumb !== full) full.recycle()
        return thumb
    }

    /** Replaces the whole stack (used when loading a project). */
    fun adoptLayers(layers: List<Layer>, activeId: String?) {
        stack.recycleAll()
        layers.forEach { stack.add(it) }
        activeLayerId = activeId ?: layers.lastOrNull()?.id
    }

    fun release() {
        stack.recycleAll()
        history.clear()
    }
}
