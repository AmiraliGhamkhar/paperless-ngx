package studio.vela.art.drawing.layers

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import studio.vela.art.drawing.render.toAndroidBlendMode

/**
 * Composites layers onto a [Canvas] in document space.
 *
 * Normal, fully opaque, unclipped layers are drawn directly (fast path). Everything else
 * goes through `saveLayer` so that per-layer opacity and blend mode apply to the layer as a
 * whole rather than to each pixel individually.
 */
object LayerCompositor {

    private val layerPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun drawStack(
        canvas: Canvas,
        layers: List<Layer>,
        docWidth: Int,
        docHeight: Int,
        skipLayerId: String? = null,
        viewportClip: RectF? = null,
    ) {
        val bounds = viewportClip ?: RectF(0f, 0f, docWidth.toFloat(), docHeight.toFloat())
        // Index of the layer below, tracked so clipping masks can reference it without a search.
        var previousVisible: Layer? = null
        for (layer in layers) {
            if (layer.id == skipLayerId) continue
            if (!layer.visible || layer.opacity <= 0.001f) continue
            drawLayer(canvas, layer, if (layer.clipping) previousVisible else null, bounds)
            previousVisible = layer
        }
    }

    /**
     * Draws a single layer.
     *
     * @param clippingBase when non-null the layer is masked by this layer's alpha (clipping mask).
     */
    fun drawLayer(
        canvas: Canvas,
        layer: Layer,
        clippingBase: Layer? = null,
        bounds: RectF? = null,
    ) {
        val fastPath = layer.blend.isNormal && layer.opacity >= 0.999f && clippingBase == null
        val layerBounds = bounds ?: RectF(0f, 0f, layer.width.toFloat(), layer.height.toFloat())
        if (fastPath) {
            canvas.drawBitmap(layer.bitmap, 0f, 0f, null)
            return
        }
        layerPaint.reset()
        layerPaint.isAntiAlias = true
        layerPaint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
        layerPaint.blendMode = layer.blend.toAndroidBlendMode()
        val saved = canvas.saveLayer(layerBounds, layerPaint)
        canvas.drawBitmap(layer.bitmap, 0f, 0f, null)
        if (clippingBase != null) {
            maskPaint.reset()
            maskPaint.isAntiAlias = true
            maskPaint.blendMode = BlendMode.DST_IN
            canvas.drawBitmap(clippingBase.bitmap, 0f, 0f, maskPaint)
        }
        canvas.restoreToCount(saved)
    }

    /** Flattens the stack into a new bitmap using the given background colour (null = transparent). */
    fun flatten(layers: List<Layer>, width: Int, height: Int, background: Int?): android.graphics.Bitmap {
        val out = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (background != null) canvas.drawColor(background)
        drawStack(canvas, layers, width, height)
        return out
    }
}
