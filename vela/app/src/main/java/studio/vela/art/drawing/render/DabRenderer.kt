package studio.vela.art.drawing.render

import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import studio.vela.art.core.common.bucket
import studio.vela.art.core.common.clamp01
import studio.vela.art.domain.model.BrushEngineKind
import studio.vela.art.domain.model.BrushPreset
import studio.vela.art.domain.model.Dab
import kotlin.math.cos
import kotlin.random.Random

/**
 * Rasterises [Dab]s.
 *
 * Performance design: dabs are drawn in a *unit space* (a circle of radius [UNIT_RADIUS]
 * centred on the origin) and the canvas is transformed per dab. Shaders are therefore
 * independent of dab size and colour, so they are cached per (hardness, grain, grain scale)
 * instead of being rebuilt for every stamp. Colour is applied with a
 * [BlendModeColorFilter], also avoiding per-dab shader allocation.
 */
class DabRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderCache = LinkedHashMap<Long, Shader>(24, 0.75f, true)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val dotBuffer = FloatArray(MAX_SPRAY_DOTS * 2)

    private var lastGrain: android.graphics.Bitmap? = null
    private var lastGrainShader: BitmapShader? = null

    /**
     * Draws one dab.
     *
     * @param color the ARGB paint colour (already includes alpha 0..255 in the alpha channel
     *   only when [useColorAlpha] is true).
     * @param erase when true the dab removes destination alpha (DST_OUT).
     */
    fun drawDab(
        canvas: Canvas,
        dab: Dab,
        preset: BrushPreset,
        color: Int,
        erase: Boolean = false,
        alphaLock: Boolean = false,
    ) {
        if (dab.size < 0.4f || dab.alpha <= 0.001f) return
        val alphaInt = (clamp01(dab.alpha) * 255f).toInt().coerceIn(1, 255)
        val tint = shade(color, dab)
        paint.reset()
        paint.isAntiAlias = true
        paint.alpha = alphaInt
        paint.colorFilter = BlendModeColorFilter(tint, BlendMode.SRC_IN)
        paint.xfermode = when {
            erase -> PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            alphaLock -> PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            else -> null
        }

        val radius = dab.size * 0.5f
        val scale = radius / UNIT_RADIUS
        val vScale = scale * dab.roundness.coerceIn(0.05f, 1f)

        canvas.save()
        canvas.translate(dab.x, dab.y)
        canvas.rotate(dab.angleDeg)
        canvas.scale(scale, vScale)

        when (preset.engine) {
            BrushEngineKind.ROUND, BrushEngineKind.ERASER, BrushEngineKind.SMUDGE ->
                drawRoundDab(canvas, preset)
            BrushEngineKind.TEXTURED -> drawTexturedDab(canvas, preset)
            BrushEngineKind.CHISEL -> drawChiselDab(canvas, preset)
            BrushEngineKind.SPRAY -> drawSprayDab(canvas, preset, dab)
            BrushEngineKind.STAMP -> drawStampDab(canvas, preset, dab)
        }
        canvas.restore()
    }

    private fun drawRoundDab(canvas: Canvas, preset: BrushPreset) {
        paint.shader = radialShader(preset.hardness)
        canvas.drawCircle(0f, 0f, UNIT_RADIUS, paint)
    }

    private fun drawChiselDab(canvas: Canvas, preset: BrushPreset) {
        paint.shader = radialShader(preset.hardness)
        canvas.drawCircle(0f, 0f, UNIT_RADIUS, paint)
    }

    private fun drawTexturedDab(canvas: Canvas, preset: BrushPreset) {
        paint.shader = texturedShader(preset)
        canvas.drawCircle(0f, 0f, UNIT_RADIUS, paint)
    }

    private fun drawSprayDab(canvas: Canvas, preset: BrushPreset, dab: Dab) {
        // Dots are laid out in unit space, so the canvas scale already sizes them.
        val random = Random(dab.seed.toLong())
        val count = 14
        var i = 0
        val dotRadius = 0.055f + 0.05f * (1f - preset.hardness)
        while (i < count) {
            val angle = random.nextFloat() * Math.PI.toFloat() * 2f
            // sqrt keeps the distribution uniform across the disc.
            val r = kotlin.math.sqrt(random.nextFloat()) * (UNIT_RADIUS - 2f)
            dotBuffer[i * 2] = cos(angle) * r
            dotBuffer[i * 2 + 1] = kotlin.math.sin(angle) * r
            i++
        }
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeWidth = dotRadius * UNIT_RADIUS * 2f
        strokePaint.color = Color.WHITE
        strokePaint.alpha = paint.alpha
        strokePaint.colorFilter = paint.colorFilter
        strokePaint.xfermode = paint.xfermode
        canvas.drawPoints(dotBuffer, 0, count * 2, strokePaint)
    }

    private fun drawStampDab(canvas: Canvas, preset: BrushPreset, dab: Dab) {
        // Procedural stamp shapes: a leaf for the "stamp" brushes and a clustered blob for
        // grunge textures. Both are deterministic per dab seed.
        val random = Random(dab.seed.toLong())
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = (clamp01(dab.alpha) * 255f).toInt().coerceIn(1, 255)
        if (preset.grain == studio.vela.art.domain.model.GrainKind.NONE) {
            val path = leafPath()
            canvas.drawPath(path, paint)
        } else {
            val blobs = 5 + random.nextInt(4)
            repeat(blobs) {
                val ox = (random.nextFloat() * 2f - 1f) * UNIT_RADIUS * 0.45f
                val oy = (random.nextFloat() * 2f - 1f) * UNIT_RADIUS * 0.45f
                val r = UNIT_RADIUS * (0.18f + random.nextFloat() * 0.35f)
                canvas.drawCircle(ox, oy, r, paint)
            }
        }
    }

    private fun leafPath(): Path {
        if (leaf != null) return leaf!!
        val path = Path()
        path.moveTo(0f, -UNIT_RADIUS)
        path.quadTo(UNIT_RADIUS * 0.95f, -UNIT_RADIUS * 0.15f, 0f, UNIT_RADIUS)
        path.quadTo(-UNIT_RADIUS * 0.95f, -UNIT_RADIUS * 0.15f, 0f, -UNIT_RADIUS)
        path.close()
        leaf = path
        return path
    }

    private var leaf: Path? = null

    /** Soft/hard radial falloff. Hardness 1 ⇒ nearly binary edge. */
    private fun radialShader(hardness: Float): Shader {
        val h = hardness.bucket(0.05f)
        val key = 1_000L + (h * 1000f).toLong()
        shaderCache[key]?.let { return it }
        val solidEdge = clamp01(h) * 0.94f
        val positions = floatArrayOf(0f, solidEdge.coerceAtLeast(0.0001f), 1f)
        val colors = intArrayOf(WHITE, WHITE, TRANSPARENT_WHITE)
        val shader = RadialGradient(0f, 0f, UNIT_RADIUS, colors, positions, Shader.TileMode.CLAMP)
        shaderCache[key] = shader
        return shader
    }

    /** Grain texture masked by the radial falloff. */
    private fun texturedShader(preset: BrushPreset): Shader {
        val grain = GrainTextures.texture(preset.grain) ?: return radialShader(preset.hardness)
        val grainKey = (preset.grainScale.bucket(0.1f) * 100f).toLong() + preset.grain.ordinal * 100_000L
        val hardnessKey = (preset.hardness.bucket(0.05f) * 1000f).toLong()
        val key = 500_000L + grainKey * 7L + hardnessKey
        shaderCache[key]?.let { return it }

        val bitmapShader = grainShader(grain, preset.grainScale)
        val falloff = radialShader(preset.hardness)
        // DST_IN keeps the grain only inside the dab falloff, tinted later by the colour filter.
        val composed = if (preset.grainStrength >= 0.5f) {
            ComposeShader(bitmapShader, falloff, BlendMode.DST_IN)
        } else {
            // Weaker grain: blend the two instead of hard-masking.
            ComposeShader(bitmapShader, falloff, BlendMode.SRC_IN)
        }
        shaderCache[key] = composed
        return composed
    }

    private fun grainShader(grain: android.graphics.Bitmap, grainScale: Float): BitmapShader {
        if (lastGrain === grain && lastGrainShader != null && lastGrainScale == grainScale) {
            return lastGrainShader!!
        }
        val shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        // Map the grain tile so that one tile covers roughly the dab diameter scaled by grainScale.
        val scale = (UNIT_RADIUS * 2f) / (GrainTextures.TILE_SIZE * grainScale.coerceIn(0.25f, 4f))
        val matrix = android.graphics.Matrix()
        matrix.setScale(scale, scale)
        shader.setLocalMatrix(matrix)
        lastGrain = grain
        lastGrainShader = shader
        lastGrainScale = grainScale
        return shader
    }

    private var lastGrainScale: Float = Float.NaN

    /** Applies per-dab colour dynamics to the base colour. */
    private fun shade(baseColor: Int, dab: Dab): Int {
        if (dab.hueShiftDeg == 0f && dab.saturationShift == 0f && dab.valueShift == 0f) return baseColor
        val hsv = FloatArray(3)
        Color.colorToHSV(baseColor, hsv)
        hsv[0] = (hsv[0] + dab.hueShiftDeg + 360f) % 360f
        hsv[1] = (hsv[1] + dab.saturationShift).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] + dab.valueShift).coerceIn(0f, 1f)
        return Color.HSVToColor(Color.alpha(baseColor), hsv)
    }

    fun clearCache() {
        shaderCache.clear()
        lastGrain = null
        lastGrainShader = null
    }

    companion object {
        /** Radius of the unit dab in local pixels. */
        const val UNIT_RADIUS = 64f
        private const val MAX_SPRAY_DOTS = 16
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val TRANSPARENT_WHITE = 0x00FFFFFF
    }
}
