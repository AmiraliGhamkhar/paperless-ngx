package studio.vela.art.drawing.paint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import studio.vela.art.drawing.layers.Layer

/**
 * Snapshot of one tile of a layer, taken before the region is modified.
 *
 * [stroke] holds buffered dab coverage for paint strokes; [after] is only used for direct
 * (eraser/smudge/fill) edits where the pixels are modified in place.
 */
class TilePatch(
    val col: Int,
    val row: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    var before: Bitmap,
) {
    var stroke: Bitmap? = null
    var strokeCanvas: Canvas? = null
    var after: Bitmap? = null

    val bytes: Long
        get() {
            var total = width.toLong() * height * 4L
            if (stroke != null) total += width.toLong() * height * 4L
            if (after != null) total += width.toLong() * height * 4L
            return total
        }

    fun ensureStrokeBuffer(): Bitmap {
        val existing = stroke
        if (existing != null) return existing
        val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        stroke = created
        strokeCanvas = Canvas(created)
        return created
    }

    fun captureAfter(layer: Layer) {
        after?.recycle()
        after = TilePainter.copyRegion(layer.bitmap, x, y, width, height)
    }

    /** Releases bitmaps still owned by this patch (callers that hand them to history must not). */
    fun recycle() {
        if (!before.isRecycled) before.recycle()
        stroke?.let { if (!it.isRecycled) it.recycle() }
        after?.let { if (!it.isRecycled) it.recycle() }
        stroke = null
        strokeCanvas = null
        after = null
    }
}

/**
 * Applies tile snapshots to a layer. Shared by live painting, undo and redo so the three
 * paths can never diverge.
 */
object TilePainter {

    private val srcPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    private val strokePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dstOutPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val srcAtopPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    fun copyRegion(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(
            source,
            Rect(x, y, x + width, y + height),
            Rect(0, 0, width, height),
            null,
        )
        return out
    }

    /** Restores the pre-edit pixels of a tile. */
    fun restore(layer: Layer, patch: TilePatch) {
        layer.canvas.drawBitmap(
            patch.before,
            null,
            Rect(patch.x, patch.y, patch.x + patch.width, patch.y + patch.height),
            srcPaint,
        )
    }

    /** Composite of buffered stroke coverage onto the (already restored) layer tile. */
    fun compositeStroke(
        layer: Layer,
        patch: TilePatch,
        opacity: Float,
        erase: Boolean,
        alphaLock: Boolean,
    ) {
        val stroke = patch.stroke ?: return
        val paint = when {
            erase -> dstOutPaint
            alphaLock -> srcAtopPaint
            else -> strokePaint
        }
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(1, 255)
        layer.canvas.drawBitmap(
            stroke,
            null,
            Rect(patch.x, patch.y, patch.x + patch.width, patch.y + patch.height),
            paint,
        )
    }

    /** Draws a captured "after" snapshot back over the layer (redo of direct edits). */
    fun restoreAfter(layer: Layer, patch: TilePatch) {
        val after = patch.after ?: return
        layer.canvas.drawBitmap(
            after,
            null,
            Rect(patch.x, patch.y, patch.x + patch.width, patch.y + patch.height),
            srcPaint,
        )
    }

    fun clearPatches(patches: Collection<TilePatch>) {
        patches.forEach { it.recycle() }
    }
}
