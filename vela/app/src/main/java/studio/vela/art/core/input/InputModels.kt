package studio.vela.art.core.input

/** Which kind of pointer produced a sample. */
enum class InputTool { STYLUS, STYLUS_ERASER, FINGER, MOUSE, UNKNOWN }

/**
 * One pointer sample in document space, already corrected for the viewport transform.
 */
data class PointerSample(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltRadians: Float,
    val orientationRadians: Float,
    val timeMs: Long,
    val tool: InputTool = InputTool.STYLUS,
    /** True for samples recovered from MotionEvent history (higher fidelity strokes). */
    val historical: Boolean = false,
)

/**
 * User-configurable input behaviour. Pure logic so stylus/palm rules are unit-testable.
 */
data class InputPolicy(
    val palmRejection: Boolean = true,
    val drawWithFinger: Boolean = true,
    val stylusEraserSwitchesTool: Boolean = true,
    val twoFingerTapUndo: Boolean = true,
    val threeFingerTapRedo: Boolean = true,
    val twoFingerRotate: Boolean = true,
    val invertGestureDirection: Boolean = false,
) {
    /**
     * @param stylusActive true while a stylus pointer is down or hovered nearby.
     */
    fun shouldStartStroke(tool: InputTool, stylusActive: Boolean): Boolean = when (tool) {
        InputTool.STYLUS, InputTool.STYLUS_ERASER, InputTool.MOUSE -> true
        InputTool.FINGER -> drawWithFinger && !(palmRejection && stylusActive)
        InputTool.UNKNOWN -> false
    }

    /** Finger gestures are ignored while a stylus is painting when palm rejection is on. */
    fun allowFingerGestures(stylusActive: Boolean): Boolean = !(palmRejection && stylusActive)

    fun useEraserFor(tool: InputTool): Boolean =
        stylusEraserSwitchesTool && tool == InputTool.STYLUS_ERASER
}

/** Result of a multi-finger gesture recognition step. */
sealed interface GestureResult {
    data object None : GestureResult
    data class Pan(val dx: Float, val dy: Float) : GestureResult
    data class Zoom(val factor: Float, val focusX: Float, val focusY: Float) : GestureResult
    data class Rotate(val degrees: Float, val focusX: Float, val focusY: Float) : GestureResult
    data object Undo : GestureResult
    data object Redo : GestureResult
}

/**
 * Recognises two/three finger taps for undo/redo while ignoring drags.
 *
 * Kept separate from [android.view.MotionEvent] so the timing rules can be tested.
 */
class MultiTapRecognizer(
    private val policy: InputPolicy,
    private val tapMaxDurationMs: Long = 320L,
    private val tapMaxMovementDp: Float = 18f,
) {
    private var startTime = 0L
    private var maxPointers = 0
    private var moved = false
    private var startX = 0f
    private var startY = 0f
    private var active = false

    fun onGestureStart(timeMs: Long, pointerCount: Int, x: Float, y: Float) {
        if (!active) {
            active = true
            startTime = timeMs
            maxPointers = pointerCount
            moved = false
            startX = x
            startY = y
        } else {
            maxPointers = maxOf(maxPointers, pointerCount)
        }
    }

    fun onGestureMove(x: Float, y: Float, density: Float) {
        if (!active) return
        val threshold = tapMaxMovementDp * density
        val dx = x - startX
        val dy = y - startY
        if (dx * dx + dy * dy > threshold * threshold) moved = true
    }

    /** @return the recognised tap gesture, if any. */
    fun onGestureEnd(timeMs: Long): GestureResult {
        if (!active) return GestureResult.None
        active = false
        if (moved || timeMs - startTime > tapMaxDurationMs) return GestureResult.None
        return when (maxPointers) {
            2 -> if (policy.twoFingerTapUndo) GestureResult.Undo else GestureResult.None
            3 -> if (policy.threeFingerTapRedo) GestureResult.Redo else GestureResult.None
            else -> GestureResult.None
        }
    }

    fun cancel() {
        active = false
    }
}
