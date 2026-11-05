package studio.vela.art.drawing.layers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import java.util.UUID

/**
 * Ordered collection of layers: index 0 is the bottom-most layer.
 *
 * Structural operations are plain list manipulation so they can be wrapped in undoable
 * commands by the document.
 */
class LayerStack(initial: List<Layer> = emptyList()) {

    private val layers = ArrayList<Layer>(initial)

    val size: Int get() = layers.size

    fun all(): List<Layer> = layers

    fun isEmpty(): Boolean = layers.isEmpty()

    fun get(index: Int): Layer? = layers.getOrNull(index)

    fun indexOf(layer: Layer): Int = layers.indexOfFirst { it.id == layer.id }

    fun indexOfId(id: String): Int = layers.indexOfFirst { it.id == id }

    fun findById(id: String): Layer? = layers.firstOrNull { it.id == id }

    fun add(layer: Layer, index: Int = layers.size): Int {
        val target = index.coerceIn(0, layers.size)
        layers.add(target, layer)
        return target
    }

    fun remove(layer: Layer): Boolean = layers.removeAll { it.id == layer.id }

    fun move(from: Int, to: Int): Boolean {
        if (from !in layers.indices) return false
        val target = to.coerceIn(0, layers.size - 1)
        if (from == target) return false
        val layer = layers.removeAt(from)
        layers.add(target, layer)
        return true
    }

    fun moveUp(layer: Layer): Boolean {
        val index = indexOf(layer)
        return if (index >= 0 && index < layers.size - 1) move(index, index + 1) else false
    }

    fun moveDown(layer: Layer): Boolean {
        val index = indexOf(layer)
        return if (index > 0) move(index, index - 1) else false
    }

    /** Layer directly below [layer], used as the clipping mask base. */
    fun below(layer: Layer): Layer? {
        val index = indexOf(layer)
        if (index <= 0) return null
        for (i in index - 1 downTo 0) {
            val candidate = layers[i]
            if (candidate.visible) return candidate
        }
        return null
    }

    /** Layers that must be drawn for the given layer to appear (itself + visible below). */
    fun visibleLayers(): List<Layer> = layers.filter { it.visible && it.opacity > 0f }

    /**
     * Merges [layer] into the layer below it, honouring opacity and blend mode of the upper
     * layer. Returns the merged layer, or null when there is nothing below.
     */
    fun mergeDown(layer: Layer): Layer? {
        val index = indexOf(layer)
        if (index <= 0) return null
        val target = layers[index - 1]
        if (target.locked) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
        paint.blendMode = layer.blend.toAndroidBlendMode()
        target.canvas.drawBitmap(layer.bitmap, 0f, 0f, paint)
        target.markThumbnailDirty()
        layers.removeAt(index)
        // The removed layer stays alive: MergeDownEdit in history owns it for undo.
        return target
    }

    fun duplicate(layer: Layer): Layer? {
        val index = indexOf(layer)
        if (index < 0) return null
        val copyBitmap = layer.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val copy = Layer(
            id = UUID.randomUUID().toString(),
            name = "${layer.name} copy",
            bitmap = copyBitmap,
        )
        layer.copyPropertiesTo(copy)
        copy.visible = true
        copy.locked = false
        layers.add(index + 1, copy)
        return copy
    }

    /** Flattens every visible layer into a single bitmap (export, flatten command). */
    fun flatten(width: Int, height: Int, background: Int? = null): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (background != null) canvas.drawColor(background)
        for (layer in layers) {
            if (!layer.visible || layer.opacity <= 0f) continue
            LayerCompositor.drawLayer(canvas, layer, clippingBase = below(layer))
        }
        return out
    }

    /** True when the layer can be cleared without touching a locked/background layer. */
    fun canClear(layer: Layer): Boolean = !layer.locked && !layer.isBackground

    fun clear(layer: Layer, color: Int = 0) {
        layer.canvas.drawColor(color, PorterDuff.Mode.CLEAR)
        if (color != 0) layer.canvas.drawColor(color)
        layer.markThumbnailDirty()
    }

    fun recycleAll() {
        layers.forEach { it.recycle() }
        layers.clear()
    }

    companion object {
        /** Creates a transparent layer bitmap at document size. */
        fun createBitmap(width: Int, height: Int): Bitmap =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        /** Fills a rectangle with a solid colour (used for background layers). */
        fun fillRect(bitmap: Bitmap, color: Int, left: Int = 0, top: Int = 0, right: Int = bitmap.width, bottom: Int = bitmap.height) {
            val canvas = Canvas(bitmap)
            val paint = Paint()
            paint.color = color
            canvas.drawRect(
                Rect(left, top, right, bottom),
                paint,
            )
        }
    }
}
