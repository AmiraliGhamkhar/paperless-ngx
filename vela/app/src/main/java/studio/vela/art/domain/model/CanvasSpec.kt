package studio.vela.art.domain.model

import kotlin.math.roundToInt

/**
 * Physical description of a canvas. Pure data, used by the new-canvas workflow to estimate
 * memory before a document is actually allocated.
 */
data class CanvasSpec(
    val width: Int,
    val height: Int,
    val dpi: Int = 300,
) {
    val megapixels: Float get() = (width.toLong() * height.toLong()) / 1_000_000f

    val aspectRatio: Float get() = if (height == 0) 1f else width.toFloat() / height.toFloat()

    /** Physical size in millimetres, derived from DPI. */
    val widthMm: Float get() = width / dpi * 25.4f
    val heightMm: Float get() = height / dpi * 25.4f

    /** Bytes for one ARGB_8888 layer bitmap. */
    val bytesPerLayer: Long get() = width.toLong() * height.toLong() * 4L

    /** Bytes for [layerCount] layers, the composite cache and scratch buffers. */
    fun estimatedBytes(layerCount: Int = 3): Long = bytesPerLayer * (layerCount.toLong() + 1L) + 8L * 1024 * 1024

    fun formattedPhysicalSize(): String {
        val w = widthMm / 10f
        val h = heightMm / 10f
        return String.format("%.1f × %.1f cm", w, h)
    }

    fun formattedMegapixels(): String = String.format("%.1f MP", megapixels)

    fun formattedMemory(layerCount: Int = 3): String {
        val mb = estimatedBytes(layerCount) / (1024.0 * 1024.0)
        return String.format("%.0f MB", mb)
    }

    /** True when the canvas is likely to exhaust memory on a mid-range tablet. */
    fun isRiskyForDevice(): Boolean = megapixels > RISKY_MEGAPIXELS

    fun clampedToLimits(): CanvasSpec = copy(
        width = width.coerceIn(MIN_SIDE, MAX_SIDE),
        height = height.coerceIn(MIN_SIDE, MAX_SIDE),
        dpi = dpi.coerceIn(MIN_DPI, MAX_DPI),
    )

    fun withDpi(newDpi: Int): CanvasSpec = copy(dpi = newDpi.coerceIn(MIN_DPI, MAX_DPI))

    fun scaled(factor: Float): CanvasSpec = copy(
        width = (width * factor).roundToInt().coerceIn(MIN_SIDE, MAX_SIDE),
        height = (height * factor).roundToInt().coerceIn(MIN_SIDE, MAX_SIDE),
    )

    companion object {
        const val MIN_SIDE = 16
        const val MAX_SIDE = 8192
        const val MIN_DPI = 36
        const val MAX_DPI = 1200
        const val RISKY_MEGAPIXELS = 16f

        fun fromMegapixels(mp: Float): CanvasSpec {
            val side = (kotlin.math.sqrt(mp * 1_000_000f)).roundToInt()
            return CanvasSpec(side, side).clampedToLimits()
        }
    }
}

/** Canonical canvas presets offered by the new-canvas sheet. */
data class CanvasPreset(
    val id: String,
    val label: String,
    val spec: CanvasSpec,
    val category: String,
)

object CanvasPresets {
    val SQUARE_2048 = CanvasPreset("sq2048", "Square 2048", CanvasSpec(2048, 2048, 300), "Square")
    val SQUARE_1500 = CanvasPreset("sq1500", "Square 1500", CanvasSpec(1500, 1500, 300), "Square")
    val FHD = CanvasPreset("fhd", "Screen 1920×1080", CanvasSpec(1920, 1080, 96), "Screen")
    val HD = CanvasPreset("hd", "Screen 1280×800", CanvasSpec(1280, 800, 96), "Screen")
    val A4_PORTRAIT = CanvasPreset("a4p", "A4 portrait", CanvasSpec(2480, 3508, 300), "Print")
    val A4_LANDSCAPE = CanvasPreset("a4l", "A4 landscape", CanvasSpec(3508, 2480, 300), "Print")
    val LETTER = CanvasPreset("letter", "US Letter", CanvasSpec(2550, 3300, 300), "Print")
    val RATIO_16_9 = CanvasPreset("r169", "16:9 3000×1688", CanvasSpec(3000, 1688, 300), "Ratio")
    val RATIO_4_3 = CanvasPreset("r43", "4:3 2400×1800", CanvasSpec(2400, 1800, 300), "Ratio")
    val RATIO_2_3 = CanvasPreset("r23", "2:3 2000×3000", CanvasSpec(2000, 3000, 300), "Ratio")
    val INSTA = CanvasPreset("insta", "Social 2048×2048", CanvasSpec(2048, 2048, 144), "Social")
    val WEBTOON = CanvasPreset("webtoon", "Webtoon 1600×8000", CanvasSpec(1600, 8000, 144), "Comic")

    val all: List<CanvasPreset> = listOf(
        SQUARE_2048, SQUARE_1500, FHD, HD, A4_PORTRAIT, A4_LANDSCAPE, LETTER,
        RATIO_16_9, RATIO_4_3, RATIO_2_3, INSTA, WEBTOON,
    )

    val categories: List<String> = all.map { it.category }.distinct()

    fun byId(id: String): CanvasPreset? = all.firstOrNull { it.id == id }
}
