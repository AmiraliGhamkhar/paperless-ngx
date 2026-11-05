package studio.vela.art.drawing.render

import android.graphics.Bitmap
import studio.vela.art.domain.model.GrainKind
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedurally generated grain textures.
 *
 * Textures are generated at runtime instead of shipping third-party image assets, which keeps
 * the licence surface clean and lets grain scale/sharpness be tuned per density bucket.
 */
object GrainTextures {

    const val TILE_SIZE = 256

    private val cache = HashMap<GrainKind, Bitmap>()

    @Synchronized
    fun texture(kind: GrainKind): Bitmap? {
        if (kind == GrainKind.NONE) return null
        cache[kind]?.let { if (!it.isRecycled) return it }
        val bitmap = generate(kind)
        cache[kind] = bitmap
        return bitmap
    }

    fun clear() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
    }

    private fun generate(kind: GrainKind): Bitmap {
        val size = TILE_SIZE
        val pixels = IntArray(size * size)
        val random = Random(kind.name.hashCode().toLong())
        when (kind) {
            GrainKind.PAPER_FINE -> fillPaper(pixels, size, random, coarse = 0.35f, contrast = 0.35f)
            GrainKind.PAPER_ROUGH -> fillPaper(pixels, size, random, coarse = 1.1f, contrast = 0.6f)
            GrainKind.CANVAS -> fillCanvas(pixels, size, random)
            GrainKind.CHALK -> fillChalk(pixels, size, random)
            GrainKind.NOISE -> fillNoise(pixels, size, random)
            GrainKind.SPECKLE -> fillSpeckle(pixels, size, random)
            GrainKind.NONE -> fillPaper(pixels, size, random, coarse = 0.5f, contrast = 0.3f)
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /** Value-noise paper grain with a couple of octaves. */
    private fun fillPaper(pixels: IntArray, size: Int, random: Random, coarse: Float, contrast: Float) {
        val octaves = listOf(4f, 8f, 16f, 32f)
        val grids = octaves.map { freq ->
            val cell = (size / freq).toInt().coerceAtLeast(2)
            val gw = size / cell + 2
            val values = FloatArray(gw * gw) { random.nextFloat() }
            Triple(cell, gw, values)
        }
        for (y in 0 until size) {
            for (x in 0 until size) {
                var value = 0f
                var amplitude = 1f
                var total = 0f
                for ((index, grid) in grids.withIndex()) {
                    val (cell, gw, values) = grid
                    val gx = x / cell
                    val gy = y / cell
                    val fx = (x % cell).toFloat() / cell
                    val fy = (y % cell).toFloat() / cell
                    val v00 = values[gy * gw + gx]
                    val v10 = values[gy * gw + gx + 1]
                    val v01 = values[(gy + 1) * gw + gx]
                    val v11 = values[(gy + 1) * gw + gx + 1]
                    val top = v00 + (v10 - v00) * fx
                    val bottom = v01 + (v11 - v01) * fx
                    val n = top + (bottom - top) * fy
                    // Higher octaves contribute less, coarse grain is stronger.
                    val weight = amplitude * if (index == 0) coarse else 1f
                    value += n * weight
                    total += weight
                    amplitude *= 0.55f
                }
                value /= total.coerceAtLeast(0.0001f)
                val shaped = 1f - contrast * (1f - value)
                pixels[y * size + x] = alphaPixel(shaped)
            }
        }
    }

    /** Woven canvas: two orthogonal thread patterns. */
    private fun fillCanvas(pixels: IntArray, size: Int, random: Random) {
        val thread = 8
        for (y in 0 until size) {
            for (x in 0 until size) {
                val wx = sin(x.toFloat() / thread * Math.PI.toFloat())
                val wy = sin(y.toFloat() / thread * Math.PI.toFloat())
                val weave = if (((x / thread) + (y / thread)) % 2 == 0) wx else wy
                val grain = 0.75f + random.nextFloat() * 0.25f
                val value = (0.55f + 0.45f * abs(weave)) * grain
                pixels[y * size + x] = alphaPixel(value)
            }
        }
    }

    /** Chalky, high-contrast speckle with soft clumps. */
    private fun fillChalk(pixels: IntArray, size: Int, random: Random) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val r = random.nextFloat()
                val value = when {
                    r < 0.18f -> 0.05f + random.nextFloat() * 0.1f
                    r < 0.55f -> 0.45f + random.nextFloat() * 0.2f
                    else -> 0.8f + random.nextFloat() * 0.2f
                }
                pixels[y * size + x] = alphaPixel(value)
            }
        }
    }

    private fun fillNoise(pixels: IntArray, size: Int, random: Random) {
        for (i in pixels.indices) {
            pixels[i] = alphaPixel(0.35f + random.nextFloat() * 0.65f)
        }
    }

    /** Sparse speckles used by spray and particle brushes. */
    private fun fillSpeckle(pixels: IntArray, size: Int, random: Random) {
        for (i in pixels.indices) pixels[i] = 0
        val dots = size * size / 22
        repeat(dots) {
            val cx = random.nextInt(size)
            val cy = random.nextInt(size)
            val radius = 1 + random.nextInt(2)
            val alpha = alphaPixel(0.6f + random.nextFloat() * 0.4f)
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val px = (cx + dx + size) % size
                    val py = (cy + dy + size) % size
                    if (dx * dx + dy * dy <= radius * radius) {
                        pixels[py * size + px] = alpha
                    }
                }
            }
        }
    }

    private fun alphaPixel(value: Float): Int {
        val a = (value.coerceIn(0f, 1f) * 255f).toInt()
        // White RGB: colour comes from the dab tint, alpha from the texture.
        return (a shl 24) or 0x00FFFFFF
    }
}
