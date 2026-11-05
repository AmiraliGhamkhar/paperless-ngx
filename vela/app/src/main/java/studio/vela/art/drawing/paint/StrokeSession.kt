package studio.vela.art.drawing.paint

import android.graphics.Color
import studio.vela.art.drawing.layers.Layer
import studio.vela.art.drawing.render.DabRenderer
import studio.vela.art.domain.model.BrushEngineKind
import studio.vela.art.domain.model.BrushPreset
import studio.vela.art.domain.model.Dab
import studio.vela.art.drawing.history.StrokeEdit

/**
 * Live stroke painting.
 *
 * Paint strokes are accumulated in sparse tile buffers and composited onto the layer as a
 * whole at stroke opacity, which is what makes overlapping dabs behave like a single stroke
 * instead of a stack of semi-transparent stamps. Eraser and smudge strokes must read/modify
 * existing pixels, so they paint directly into the layer; their undo data is still captured
 * per tile.
 */
class StrokeSession(
    private val layer: Layer,
    val preset: BrushPreset,
    val color: Int,
    private val renderer: DabRenderer,
    /** Direct sessions write straight into the layer bitmap (eraser, smudge). */
    val direct: Boolean,
) {
    private val docWidth = layer.width
    private val docHeight = layer.height
    private val patches = LinkedHashMap<Long, TilePatch>()
    private val pending = LinkedHashSet<Long>()
    private val tileBounds = IntArray(4)
    private val tileRect = IntArray(4)

    private var minX = Float.MAX_VALUE
    private var minY = Float.MAX_VALUE
    private var maxX = -Float.MAX_VALUE
    private var maxY = -Float.MAX_VALUE

    var dabCount: Int = 0
        private set

    val alphaLocked: Boolean get() = layer.alphaLocked && !preset.isEraser
    val tileCount: Int get() = patches.size
    val layerId: String get() = layer.id
    val strokeOpacity: Float get() = preset.opacity

    /** Tiles holding buffered coverage, used for the live on-screen preview. */
    fun previewTiles(): Collection<TilePatch> = patches.values
    val isEmpty: Boolean get() = patches.isEmpty()

    /** Stamps a dab into the stroke buffers / layer. */
    fun stamp(dab: Dab) {
        val radius = dab.size * 0.5f + 2f
        val bounds = TileGeometry.tilesForCircle(dab.x, dab.y, radius, docWidth, docHeight, out = tileBounds) ?: return
        dabCount++
        for (row in bounds[1]..bounds[3]) {
            for (col in bounds[0]..bounds[2]) {
                val patch = patchFor(col, row) ?: continue
                if (direct) continue
                val strokeCanvas = patch.strokeCanvas ?: continue
                val save = strokeCanvas.save()
                strokeCanvas.translate(-patch.x.toFloat(), -patch.y.toFloat())
                renderer.drawDab(strokeCanvas, dab, preset, color, erase = false, alphaLock = false)
                strokeCanvas.restoreToCount(save)
                pending.add(TileGeometry.key(col, row))
            }
        }
        if (direct) {
            renderer.drawDab(
                layer.canvas,
                dab,
                preset,
                directColor(dab),
                erase = preset.engine == BrushEngineKind.ERASER,
                alphaLock = false,
            )
            layer.markThumbnailDirty()
        }
        expandBounds(dab)
    }

    /** Composites buffered tiles onto the layer. Idempotent, so it can run every frame. */
    fun flush() {
        if (direct || pending.isEmpty()) return
        for (key in pending) {
            val patch = patches[key] ?: continue
            TilePainter.restore(layer, patch)
            TilePainter.compositeStroke(layer, patch, preset.opacity, erase = false, alphaLock = alphaLocked)
        }
        pending.clear()
        layer.markThumbnailDirty()
    }

    /** True when the session holds too many tiles and should be split to bound memory. */
    fun shouldSplit(): Boolean = patches.size >= MAX_TILES_PER_SESSION

    /** Finalises the stroke and transfers tile ownership to a history command. */
    fun commit(label: String): StrokeEdit? {
        flush()
        if (patches.isEmpty() || dabCount == 0) {
            TilePainter.clearPatches(patches.values)
            patches.clear()
            return null
        }
        if (direct) {
            patches.values.forEach { it.captureAfter(layer) }
        }
        val list = patches.values.toList()
        patches.clear()
        return StrokeEdit(
            layerId = layer.id,
            patches = list,
            opacity = preset.opacity,
            erase = preset.engine == BrushEngineKind.ERASER,
            direct = direct,
            alphaLock = alphaLocked,
            label = label,
        )
    }

    /** Abandons the stroke without touching the layer (used for cancelled gestures). */
    fun cancel() {
        if (direct) {
            // Direct edits are already on the layer: roll them back from the snapshots.
            for (patch in patches.values) TilePainter.restore(layer, patch)
        }
        TilePainter.clearPatches(patches.values)
        patches.clear()
        pending.clear()
    }

    /** Document-space bounds touched so far, as [left, top, right, bottom]. */
    fun touchedBounds(): FloatArray = floatArrayOf(minX, minY, maxX, maxY)

    private fun patchFor(col: Int, row: Int): TilePatch? {
        val key = TileGeometry.key(col, row)
        patches[key]?.let { return it }
        TileGeometry.tileRect(col, row, docWidth, docHeight, out = tileRect)
        val w = tileRect[2]
        val h = tileRect[3]
        if (w <= 0 || h <= 0) return null
        val patch = TilePatch(
            col = col,
            row = row,
            x = tileRect[0],
            y = tileRect[1],
            width = w,
            height = h,
            before = TilePainter.copyRegion(layer.bitmap, tileRect[0], tileRect[1], w, h),
        )
        if (!direct) patch.ensureStrokeBuffer()
        patches[key] = patch
        return patch
    }

    /** Smudge picks up existing paint so the dab drags colour instead of adding it. */
    private fun directColor(dab: Dab): Int {
        if (preset.engine != BrushEngineKind.SMUDGE) return color
        val bitmap = layer.bitmap
        var r = 0
        var g = 0
        var b = 0
        var a = 0
        var samples = 0
        val offsets = floatArrayOf(0f, 0f, -dab.size * 0.15f, 0f, dab.size * 0.15f, 0f, 0f, -dab.size * 0.15f, 0f, dab.size * 0.15f)
        var i = 0
        while (i < offsets.size) {
            val px = (dab.x + offsets[i]).toInt()
            val py = (dab.y + offsets[i + 1]).toInt()
            if (px in 0 until bitmap.width && py in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(px, py)
                val alpha = Color.alpha(pixel)
                if (alpha > 0) {
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    a += alpha
                    samples++
                }
            }
            i += 2
        }
        if (samples == 0) return color
        return Color.argb(a / samples, r / samples, g / samples, b / samples)
    }

    private fun expandBounds(dab: Dab) {
        val radius = dab.size * 0.5f
        if (dab.x - radius < minX) minX = dab.x - radius
        if (dab.y - radius < minY) minY = dab.y - radius
        if (dab.x + radius > maxX) maxX = dab.x + radius
        if (dab.y + radius > maxY) maxY = dab.y + radius
    }

    companion object {
        /** Bounds live-stroke memory; long strokes are split into several undo steps. */
        const val MAX_TILES_PER_SESSION = 120
    }
}
