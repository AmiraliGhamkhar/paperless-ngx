package studio.vela.art.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasSpecTest {

    @Test
    fun `megapixels and memory are derived from the canvas size`() {
        val spec = CanvasSpec(2048, 2048, 300)
        assertEquals(4.19f, spec.megapixels, 0.01f)
        assertEquals(2048L * 2048L * 4L, spec.bytesPerLayer)
        assertTrue(spec.estimatedBytes(3) > spec.bytesPerLayer * 3)
    }

    @Test
    fun `physical size uses dpi`() {
        val a4 = CanvasSpec(2480, 3508, 300)
        assertEquals(210f, a4.widthMm, 1f)
        assertEquals(297f, a4.heightMm, 1f)
    }

    @Test
    fun `canvas limits are enforced`() {
        val clamped = CanvasSpec(99999, 1, 5000).clampedToLimits()
        assertEquals(CanvasSpec.MAX_SIDE, clamped.width)
        assertEquals(CanvasSpec.MIN_SIDE, clamped.height)
        assertEquals(CanvasSpec.MAX_DPI, clamped.dpi)
    }

    @Test
    fun `large canvases raise the memory warning`() {
        assertTrue(CanvasSpec(5000, 5000).isRiskyForDevice())
        assertTrue(!CanvasSpec(1500, 1500).isRiskyForDevice())
    }

    @Test
    fun `scaling keeps aspect ratio`() {
        val halved = CanvasSpec(2000, 1000).scaled(0.5f)
        assertEquals(1000, halved.width)
        assertEquals(500, halved.height)
    }

    @Test
    fun `presets are unique and within limits`() {
        val ids = CanvasPresets.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        CanvasPresets.all.forEach { preset ->
            assertEquals(
                preset.spec,
                preset.spec.clampedToLimits(),
                )
        }
    }

    @Test
    fun `preset lookup by id`() {
        val a4 = CanvasPresets.byId("a4p")
        assertTrue(a4 != null)
        assertEquals(2480, a4!!.spec.width)
        assertEquals(null, CanvasPresets.byId("missing"))
    }
}
