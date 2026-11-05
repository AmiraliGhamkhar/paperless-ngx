package studio.vela.art.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ViewportTest {

    private fun approx(expected: Float, actual: Float, tolerance: Float = 0.01f) {
        assertTrue(
            "expected $expected but was $actual",
            abs(expected - actual) <= tolerance,
        )
    }

    private val viewport = Viewport(
        docWidth = 1000,
        docHeight = 800,
        viewWidth = 1200f,
        viewHeight = 900f,
        zoom = 1f,
    )

    @Test
    fun `document centre maps to view centre`() {
        val screen = viewport.toScreen(500f, 400f)
        approx(600f, screen[0])
        approx(450f, screen[1])
    }

    @Test
    fun `screen to doc is the inverse of doc to screen`() {
        val rotated = viewport.copy(zoom = 2.3f, rotationDeg = 37f, panX = 44f, panY = -18f)
        val docPoint = floatArrayOf(320f, 655f)
        val screen = rotated.toScreen(docPoint[0], docPoint[1])
        val back = rotated.toDoc(screen[0], screen[1])
        approx(docPoint[0], back[0])
        approx(docPoint[1], back[1])
    }

    @Test
    fun `zoom about a focal point keeps that document point stationary`() {
        val focalX = 300f
        val focalY = 250f
        val before = viewport.toDoc(focalX, focalY)
        val zoomed = viewport.zoomAbout(focalX, focalY, 2.5f)
        val after = zoomed.toDoc(focalX, focalY)
        approx(before[0], after[0], 0.05f)
        approx(before[1], after[1], 0.05f)
        approx(2.5f, zoomed.zoom, 0.001f)
    }

    @Test
    fun `rotation about a focal point keeps that document point stationary`() {
        val focalX = 700f
        val focalY = 300f
        val before = viewport.toDoc(focalX, focalY)
        val rotated = viewport.rotateAbout(focalX, focalY, 42f)
        val after = rotated.toDoc(focalX, focalY)
        approx(before[0], after[0], 0.05f)
        approx(before[1], after[1], 0.05f)
        approx(42f, rotated.rotationDeg, 0.001f)
    }

    @Test
    fun `rotation is normalised into minus 180 to 180`() {
        val rotated = viewport.rotateAbout(600f, 450f, 400f)
        assertTrue(rotated.rotationDeg in -180f..180f)
    }

    @Test
    fun `zoom is clamped to the supported range`() {
        val zoomedIn = viewport.zoomAbout(600f, 450f, 1000f)
        assertEquals(Viewport.MAX_ZOOM, zoomedIn.zoom, 0.001f)
        val zoomedOut = viewport.zoomAbout(600f, 450f, 0.00001f)
        assertEquals(Viewport.MIN_ZOOM, zoomedOut.zoom, 0.001f)
    }

    @Test
    fun `fit centres the document and respects the margin`() {
        val fitted = viewport.fit(marginFraction = 0.1f)
        // Wider than tall document in a wider than tall viewport: limited by width.
        approx(1200f * 0.8f / 1000f, fitted.zoom, 0.001f)
        approx(0f, fitted.panX)
        approx(0f, fitted.panY)
    }

    @Test
    fun `visible document rect is inside the document`() {
        val rect = viewport.visibleDocRect()
        assertTrue(rect[0] >= 0f && rect[1] >= 0f)
        assertTrue(rect[2] <= 1000f && rect[3] <= 800f)
        assertTrue(rect[2] > rect[0] && rect[3] > rect[1])
    }

    @Test
    fun `clamped pan keeps the canvas centre near the viewport`() {
        val panned = viewport.panBy(100000f, 100000f).clampedPan()
        assertTrue(abs(panned.panX) <= 1200f)
        assertTrue(abs(panned.panY) <= 900f)
    }
}
