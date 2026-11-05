package studio.vela.art.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTapRecognizerTest {

    private val policy = InputPolicy()

    @Test
    fun `two finger tap triggers undo`() {
        val recognizer = MultiTapRecognizer(policy)
        recognizer.onGestureStart(0L, 2, 100f, 100f)
        recognizer.onGestureMove(102f, 101f, density = 2f)
        assertEquals(GestureResult.Undo, recognizer.onGestureEnd(150L))
    }

    @Test
    fun `three finger tap triggers redo`() {
        val recognizer = MultiTapRecognizer(policy)
        recognizer.onGestureStart(0L, 3, 100f, 100f)
        assertEquals(GestureResult.Redo, recognizer.onGestureEnd(120L))
    }

    @Test
    fun `dragging is not a tap`() {
        val recognizer = MultiTapRecognizer(policy)
        recognizer.onGestureStart(0L, 2, 100f, 100f)
        recognizer.onGestureMove(400f, 400f, density = 2f)
        assertEquals(GestureResult.None, recognizer.onGestureEnd(120L))
    }

    @Test
    fun `slow gesture is not a tap`() {
        val recognizer = MultiTapRecognizer(policy)
        recognizer.onGestureStart(0L, 2, 10f, 10f)
        assertEquals(GestureResult.None, recognizer.onGestureEnd(5_000L))
    }

    @Test
    fun `single finger tap is not undo`() {
        val recognizer = MultiTapRecognizer(policy)
        recognizer.onGestureStart(0L, 1, 10f, 10f)
        assertEquals(GestureResult.None, recognizer.onGestureEnd(50L))
    }

    @Test
    fun `disabled gestures are ignored`() {
        val disabled = InputPolicy(twoFingerTapUndo = false, threeFingerTapRedo = false)
        val recognizer = MultiTapRecognizer(disabled)
        recognizer.onGestureStart(0L, 2, 10f, 10f)
        assertEquals(GestureResult.None, recognizer.onGestureEnd(40L))
        recognizer.onGestureStart(100L, 3, 10f, 10f)
        assertEquals(GestureResult.None, recognizer.onGestureEnd(120L))
    }

    @Test
    fun `palm rejection blocks finger strokes while the stylus is active`() {
        assertFalse(policy.shouldStartStroke(InputTool.FINGER, stylusActive = true))
        assertTrue(policy.shouldStartStroke(InputTool.FINGER, stylusActive = false))
        assertTrue(policy.shouldStartStroke(InputTool.STYLUS, stylusActive = true))
        assertFalse(policy.allowFingerGestures(stylusActive = true))
    }

    @Test
    fun `palm rejection can be disabled`() {
        val relaxed = InputPolicy(palmRejection = false)
        assertTrue(relaxed.shouldStartStroke(InputTool.FINGER, stylusActive = true))
        assertTrue(relaxed.allowFingerGestures(stylusActive = true))
    }

    @Test
    fun `finger drawing can be disabled entirely`() {
        val stylusOnly = InputPolicy(drawWithFinger = false)
        assertFalse(stylusOnly.shouldStartStroke(InputTool.FINGER, stylusActive = false))
        assertTrue(stylusOnly.shouldStartStroke(InputTool.STYLUS_ERASER, stylusActive = true))
    }

    @Test
    fun `stylus eraser honours the configuration`() {
        assertTrue(policy.useEraserFor(InputTool.STYLUS_ERASER))
        assertFalse(policy.useEraserFor(InputTool.STYLUS))
        assertFalse(InputPolicy(stylusEraserSwitchesTool = false).useEraserFor(InputTool.STYLUS_ERASER))
    }
}
