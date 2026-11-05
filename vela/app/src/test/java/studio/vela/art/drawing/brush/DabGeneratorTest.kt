package studio.vela.art.drawing.brush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.vela.art.domain.model.BrushCategory
import studio.vela.art.domain.model.BrushEngineKind
import studio.vela.art.domain.model.BrushPreset
import studio.vela.art.domain.model.Dab
import studio.vela.art.domain.model.PressureResponse
import kotlin.random.Random

class DabGeneratorTest {

    private fun preset(
        size: Float = 20f,
        spacing: Float = 0.1f,
        stabilizer: Float = 0f,
        pressureSize: PressureResponse = PressureResponse(gamma = 1f),
    ) = BrushPreset(
        id = "test",
        name = "Test",
        category = BrushCategory.INKING,
        engine = BrushEngineKind.ROUND,
        size = size,
        spacing = spacing,
        stabilizer = stabilizer,
        pressureSize = pressureSize,
    )

    @Test
    fun `straight stroke produces evenly spaced dabs`() {
        val generator = DabGenerator(preset(size = 20f, spacing = 0.5f), Random(1))
        generator.begin(0f, 0f, 1f, 0f, 0L)
        val dabs = ArrayList<Dab>()
        var time = 0L
        for (i in 1..200) {
            time += 8
            dabs.addAll(generator.add(i * 0.5f, 0f, 1f, 0f, time))
        }
        assertTrue("stroke should produce dabs", dabs.size > 5)
        // spacing 0.5 * 20px = 10px over ~100px => about 10 dabs.
        assertTrue("expected about 10 dabs, got ${dabs.size}", dabs.size in 8..13)
        for (i in 1 until dabs.size) {
            val delta = dabs[i].x - dabs[i - 1].x
            assertTrue("dab gap $delta out of range", delta in 8f..12f)
        }
    }

    @Test
    fun `pressure changes dab size`() {
        val generator = DabGenerator(preset(size = 100f), Random(2))
        val light = generator.dabSize(0.1f, 0f, 0f)
        val heavy = generator.dabSize(1f, 0f, 0f)
        assertTrue("heavy pressure must be larger", heavy > light * 2f)
    }

    @Test
    fun `pressure gamma curve is applied`() {
        val linear = PressureResponse(gamma = 1f).apply(0.5f)
        val sharp = PressureResponse(gamma = 2f).apply(0.5f)
        assertEquals(0.5f, linear, 0.0001f)
        assertEquals(0.25f, sharp, 0.0001f)
    }

    @Test
    fun `pressure response output window is respected`() {
        val response = PressureResponse(gamma = 1f, min = 0.2f, max = 0.8f)
        assertEquals(0.2f, response.apply(0f), 0.0001f)
        assertEquals(0.8f, response.apply(1f), 0.0001f)
        assertEquals(0.5f, response.apply(0.5f), 0.0001f)
    }

    @Test
    fun `velocity response thins fast strokes`() {
        val noVelocity = DabGenerator(preset(size = 40f), Random(4))
        assertEquals(noVelocity.dabSize(1f, 0f, 0.1f), noVelocity.dabSize(1f, 0f, 5f), 0.001f)

        val thin = DabGenerator(preset(size = 40f).copy(velocitySize = 0.8f), Random(4))
        assertTrue(thin.dabSize(1f, 0f, 5f) < thin.dabSize(1f, 0f, 0.1f))
    }

    @Test
    fun `tilt widens the dab when configured`() {
        val generator = DabGenerator(preset(size = 40f).copy(tiltSize = 0.5f), Random(5))
        val flat = generator.dabSize(1f, 0f, 0f)
        val tilted = generator.dabSize(1f, 1.4f, 0f)
        assertTrue(tilted > flat)
    }

    @Test
    fun `taper shortens the beginning of a stroke`() {
        val generator = DabGenerator(
            preset(size = 20f, spacing = 0.2f).copy(taperStart = 1f),
            Random(6),
        )
        val firstDabs = generator.begin(0f, 0f, 1f, 0f, 0L)
        assertTrue(firstDabs.isNotEmpty())
        val firstSize = firstDabs.first().size
        val laterDabs = ArrayList<Dab>()
        var time = 0L
        for (i in 1..80) {
            time += 8
            laterDabs.addAll(generator.add(i * 1.5f, 0f, 1f, 0f, time))
        }
        assertTrue("later dabs are needed", laterDabs.isNotEmpty())
        assertTrue(
            "taper should start small and grow",
            laterDabs.last().size > firstSize,
        )
    }

    @Test
    fun `scatter offsets dabs but stays within the configured radius`() {
        val generator = DabGenerator(
            preset(size = 20f, spacing = 0.4f).copy(scatter = 1f),
            Random(8),
        )
        generator.begin(0f, 0f, 1f, 0f, 0L)
        val dabs = ArrayList<Dab>()
        var time = 0L
        for (i in 1..60) {
            time += 8
            dabs.addAll(generator.add(i * 2f, 0f, 1f, 0f, time))
        }
        assertTrue(dabs.isNotEmpty())
        val maxOffset = 20f // scatter 1.0 * size
        for (dab in dabs) {
            assertTrue("scatter offset too large", kotlin.math.abs(dab.y) <= maxOffset)
        }
        assertTrue("scatter should move dabs off the axis", dabs.any { kotlin.math.abs(it.y) > 0.01f })
    }

    @Test
    fun `zero alpha dabs are skipped`() {
        val generator = DabGenerator(preset().copy(flow = 0.01f, opacity = 0.01f), Random(9))
        val dabs = generator.begin(10f, 10f, 0f, 0f, 0L)
        assertTrue(dabs.isEmpty())
    }

    @Test
    fun `copy preset is independent of the source`() {
        val base = preset(id = "a", name = "A")
        val copy = base.copy(id = "b", name = "B")
        assertEquals("A", base.name)
        assertEquals("B", copy.name)
    }

    private fun preset(id: String, name: String) = BrushPreset(
        id = id,
        name = name,
        category = BrushCategory.GRAPHITE,
        engine = BrushEngineKind.ROUND,
    )
}
