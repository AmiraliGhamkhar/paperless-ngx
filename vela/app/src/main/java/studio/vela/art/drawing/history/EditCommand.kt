package studio.vela.art.drawing.history

import android.graphics.Bitmap
import studio.vela.art.drawing.document.ArtDocument
import studio.vela.art.drawing.layers.Layer
import studio.vela.art.drawing.layers.LayerProps
import studio.vela.art.drawing.paint.TilePainter
import studio.vela.art.drawing.paint.TilePatch
import studio.vela.art.drawing.render.toAndroidBlendMode
import studio.vela.art.domain.model.BlendKind

/** An undoable edit. Every command is symmetric: [undo] must restore the exact prior state. */
interface EditCommand {
    val label: String

    /** Memory held while the command is in the history stack. */
    val approxBytes: Long

    fun undo(doc: ArtDocument)
    fun redo(doc: ArtDocument)

    /** Frees bitmaps when the command is evicted from history. */
    fun release() = Unit
}

/**
 * A brush/eraser stroke stored as per-tile snapshots.
 *
 * Buffered strokes keep the pre-stroke tile plus the stroke coverage tile, so redo replays
 * the identical composite instead of storing a second full snapshot.
 */
class StrokeEdit(
    private val layerId: String,
    private val patches: List<TilePatch>,
    private val opacity: Float,
    private val erase: Boolean,
    private val direct: Boolean,
    private val alphaLock: Boolean,
    override val label: String,
) : EditCommand {

    override val approxBytes: Long = patches.fold(0L) { acc, patch -> acc + patch.bytes }

    override fun undo(doc: ArtDocument) {
        val layer = doc.stack.findById(layerId) ?: return
        patches.forEach { TilePainter.restore(layer, it) }
        layer.markThumbnailDirty()
    }

    override fun redo(doc: ArtDocument) {
        val layer = doc.stack.findById(layerId) ?: return
        if (direct) {
            patches.forEach { TilePainter.restoreAfter(layer, it) }
        } else {
            patches.forEach {
                TilePainter.restore(layer, it)
                TilePainter.compositeStroke(layer, it, opacity, erase, alphaLock)
            }
        }
        layer.markThumbnailDirty()
    }

    override fun release() {
        patches.forEach { it.recycle() }
    }

    val tiles: Int get() = patches.size
}

/** Whole-layer pixel edit (flood fill, shape rasterisation, clear, merge). */
class BitmapEdit(
    private val layerId: String,
    private val before: Bitmap,
    private val after: Bitmap,
    override val label: String,
) : EditCommand {

    override val approxBytes: Long = before.byteCount.toLong() + after.byteCount.toLong()

    override fun undo(doc: ArtDocument) {
        val layer = doc.stack.findById(layerId) ?: return
        layer.canvas.drawBitmap(before, 0f, 0f, SRC_PAINT)
        layer.markThumbnailDirty()
    }

    override fun redo(doc: ArtDocument) {
        val layer = doc.stack.findById(layerId) ?: return
        layer.canvas.drawBitmap(after, 0f, 0f, SRC_PAINT)
        layer.markThumbnailDirty()
    }

    override fun release() {
        if (!before.isRecycled) before.recycle()
        if (!after.isRecycled) after.recycle()
    }

    companion object {
        private val SRC_PAINT = android.graphics.Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC)
        }
    }
}

class LayerAddEdit(
    private val layer: Layer,
    private val index: Int,
    override val label: String = "Add layer",
) : EditCommand {
    override val approxBytes: Long = layer.bitmap.byteCount.toLong()

    override fun undo(doc: ArtDocument) {
        doc.stack.remove(layer)
        if (doc.activeLayerId == layer.id) doc.activeLayerId = doc.stack.all().lastOrNull()?.id
    }

    override fun redo(doc: ArtDocument) {
        if (doc.stack.findById(layer.id) == null) doc.stack.add(layer, index)
        doc.activeLayerId = layer.id
    }
}

class LayerRemoveEdit(
    private val layer: Layer,
    private val index: Int,
    override val label: String = "Delete layer",
) : EditCommand {
    override val approxBytes: Long = layer.bitmap.byteCount.toLong()

    override fun undo(doc: ArtDocument) {
        if (doc.stack.findById(layer.id) == null) doc.stack.add(layer, index)
        doc.activeLayerId = layer.id
    }

    override fun redo(doc: ArtDocument) {
        doc.stack.remove(layer)
        if (doc.activeLayerId == layer.id) doc.activeLayerId = doc.stack.all().lastOrNull()?.id
    }
}

class LayerReorderEdit(
    private val layerId: String,
    private val fromIndex: Int,
    private val toIndex: Int,
    override val label: String = "Reorder layer",
) : EditCommand {
    override val approxBytes: Long = 0L

    override fun undo(doc: ArtDocument) {
        doc.stack.move(toIndex, fromIndex)
    }

    override fun redo(doc: ArtDocument) {
        doc.stack.move(fromIndex, toIndex)
    }
}

class LayerPropsEdit(
    private val layerId: String,
    private val before: LayerProps,
    private val after: LayerProps,
    override val label: String,
) : EditCommand {
    override val approxBytes: Long = 0L

    override fun undo(doc: ArtDocument) {
        doc.stack.findById(layerId)?.let { before.applyTo(it) }
    }

    override fun redo(doc: ArtDocument) {
        doc.stack.findById(layerId)?.let { after.applyTo(it) }
    }
}

/** Merge of [removed] into the layer below, keeping both sides of the operation reversible. */
class MergeDownEdit(
    private val targetId: String,
    private val removed: Layer,
    private val removedIndex: Int,
    private val removedOpacity: Float,
    private val removedBlend: BlendKind,
    private val targetBefore: Bitmap,
) : EditCommand {
    override val label: String = "Merge down"
    override val approxBytes: Long = targetBefore.byteCount.toLong() + removed.bitmap.byteCount.toLong()

    override fun undo(doc: ArtDocument) {
        val target = doc.stack.findById(targetId) ?: return
        target.canvas.drawBitmap(targetBefore, 0f, 0f, SRC_PAINT)
        target.markThumbnailDirty()
        if (doc.stack.findById(removed.id) == null) doc.stack.add(removed, removedIndex)
        doc.activeLayerId = removed.id
    }

    override fun redo(doc: ArtDocument) {
        val target = doc.stack.findById(targetId) ?: return
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        paint.alpha = (removedOpacity.coerceIn(0f, 1f) * 255f).toInt()
        paint.blendMode = removedBlend.toAndroidBlendMode()
        target.canvas.drawBitmap(removed.bitmap, 0f, 0f, paint)
        target.markThumbnailDirty()
        doc.stack.remove(removed)
        doc.activeLayerId = target.id
    }

    companion object {
        private val SRC_PAINT = android.graphics.Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC)
        }
    }
}
