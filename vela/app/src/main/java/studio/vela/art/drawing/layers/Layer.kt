package studio.vela.art.drawing.layers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import studio.vela.art.domain.model.BlendKind

/**
 * A raster layer: one mutable ARGB bitmap at document resolution plus its compositing
 * properties. Layers own their bitmap; the document owns the layer list.
 */
class Layer(
    val id: String,
    var name: String,
    val bitmap: Bitmap,
    var opacity: Float = 1f,
    var blend: BlendKind = BlendKind.NORMAL,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var alphaLocked: Boolean = false,
    var clipping: Boolean = false,
    val isBackground: Boolean = false,
) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height

    /** Canvas bound to the layer bitmap; mutable bitmaps are required for painting. */
    val canvas: Canvas = Canvas(bitmap)

    var thumbnail: Bitmap? = null
        private set
    private var thumbnailDirty = true

    fun markThumbnailDirty() {
        thumbnailDirty = true
    }

    /** Small preview used by the layers panel; cached until the layer is painted again. */
    fun ensureThumbnail(sizePx: Int = 96): Bitmap {
        val existing = thumbnail
        if (existing != null && !thumbnailDirty && existing.width == sizePx) return existing
        val scale = sizePx.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val target = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        existing?.recycle()
        thumbnail = target
        thumbnailDirty = false
        return target
    }

    /** True when painting on this layer is currently allowed. */
    fun isPaintable(): Boolean = !locked && visible

    /** Copies compositing properties onto another layer (used by duplicate). */
    fun copyPropertiesTo(other: Layer) {
        other.opacity = opacity
        other.blend = blend
        other.visible = visible
        other.locked = locked
        other.alphaLocked = alphaLocked
        other.clipping = clipping
    }

    fun recycle() {
        thumbnail?.recycle()
        thumbnail = null
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}
