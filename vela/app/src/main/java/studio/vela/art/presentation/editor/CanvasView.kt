package studio.vela.art.presentation.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import studio.vela.art.core.geometry.Viewport
import studio.vela.art.core.input.GestureResult
import studio.vela.art.core.input.InputPolicy
import studio.vela.art.core.input.InputTool
import studio.vela.art.core.input.MultiTapRecognizer
import studio.vela.art.core.input.PointerSample
import studio.vela.art.drawing.canvas.screenBounds
import studio.vela.art.drawing.canvas.toMatrix
import studio.vela.art.drawing.document.EditorController
import studio.vela.art.drawing.document.ShapeKind
import studio.vela.art.drawing.layers.Layer
import studio.vela.art.drawing.layers.LayerCompositor
import studio.vela.art.drawing.paint.StrokeSession
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The painting surface.
 *
 * Implemented as a classic [View] rather than a Compose canvas: stylus input, historical
 * pointer samples and per-frame invalidation need direct `MotionEvent` access, and this keeps
 * recomposition out of the drawing hot path.
 *
 * Rendering order: paper → layers (bottom to top, with clipping masks) → live stroke preview →
 * shape preview → canvas border.
 */
class CanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Optional callback so Compose can mirror the zoom level. */
    var onViewportChanged: ((zoomPercent: Float) -> Unit)? = null

    private var controller: EditorController? = null
    private var boundDocumentId: Long? = null
    private var policy: InputPolicy = InputPolicy()
    private var checkerboardEnabled = true
    private var shadowEnabled = true
    private var hapticsEnabled = true

    private var viewport = Viewport(1, 1)
    private var fitted = false

    private val viewMatrix = Matrix()
    private val layerBounds = RectF()
    private val previewPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paperPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val checkerPaint = Paint()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var checkerShader: BitmapShader? = null
    private var checkerMatrix = Matrix()

    // Pointer state
    private var drawing = false
    private var activePointerId = -1
    private var activeTool = InputTool.STYLUS
    private var gestureActive = false
    private var gesturePointerCount = 0
    private var lastFocalX = 0f
    private var lastFocalY = 0f
    private var lastSpread = 0f
    private var lastAngle = 0f
    private var stylusDown = false
    private var stylusHover = false
    private var hoverX = 0f
    private var hoverY = 0f
    private var showHoverCursor = false
    private var tapRecognizer = MultiTapRecognizer(InputPolicy())

    private val sampleBuffer = ArrayList<PointerSample>(24)

    init {
        isFocusable = true
        isHapticFeedbackEnabled = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** Document currently bound, so Compose does not re-fit on every recomposition. */
    fun boundControllerId(): Long? = boundDocumentId

    fun bind(controller: EditorController?) {
        val newId = controller?.document?.projectId
        val sameDocument = newId != null && newId == boundDocumentId
        this.controller = controller
        boundDocumentId = newId
        controller?.onInvalidate = { requestRender() }
        if (!sameDocument) {
            fitted = false
            syncDocumentSize(fit = true)
        }
        requestRender()
    }

    fun applySettings(
        inputPolicy: InputPolicy,
        checkerboard: Boolean,
        showCanvasShadow: Boolean,
        haptics: Boolean,
    ) {
        if (policy != inputPolicy) {
            policy = inputPolicy
            tapRecognizer = MultiTapRecognizer(inputPolicy)
        }
        checkerboardEnabled = checkerboard
        shadowEnabled = showCanvasShadow
        hapticsEnabled = haptics
        requestRender()
    }

    fun requestRender() {
        postInvalidateOnAnimation()
    }

    // ------------------------------------------------------------------ viewport API

    fun zoomPercent(): Float = viewport.zoom * 100f

    fun zoomBy(factor: Float) {
        viewport = viewport.zoomAbout(width / 2f, height / 2f, factor).clampedPan()
        notifyViewport()
    }

    fun fitToScreen() {
        viewport = viewport.fit().clampedPan()
        notifyViewport()
    }

    fun resetView() {
        viewport = viewport.copy(rotationDeg = 0f).fit().clampedPan()
        notifyViewport()
    }

    private fun notifyViewport() {
        onViewportChanged?.invoke(zoomPercent())
        requestRender()
    }

    private fun syncDocumentSize(fit: Boolean = false) {
        val doc = controller?.document ?: return
        if (viewport.docWidth != doc.width || viewport.docHeight != doc.height) {
            viewport = viewport.copy(docWidth = doc.width, docHeight = doc.height)
            fitted = false
        }
        viewport = viewport.copy(viewWidth = width.toFloat(), viewHeight = height.toFloat())
        if (!fitted && width > 1 && height > 1) {
            viewport = viewport.fit()
            fitted = true
            notifyViewport()
        } else if (fit && width > 1) {
            viewport = viewport.fit()
            notifyViewport()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewport = viewport.copy(viewWidth = w.toFloat(), viewHeight = h.toFloat())
        if (w > 1 && h > 1) {
            if (!fitted || oldw == 0) {
                viewport = viewport.fit()
                fitted = true
            } else {
                // Keep the visible centre stable across rotation/resize.
                viewport = viewport.copy(panX = viewport.panX, panY = viewport.panY)
            }
            notifyViewport()
        }
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val controller = controller ?: return
        val document = controller.document
        syncDocumentSize()

        canvas.drawColor(0xFF14121B.toInt())

        viewport.toMatrix(viewMatrix)
        val save = canvas.save()
        canvas.concat(viewMatrix)

        layerBounds.set(0f, 0f, document.width.toFloat(), document.height.toFloat())
        drawPaper(canvas, document.width.toFloat(), document.height.toFloat(), document.background)

        val session = controller.activeStrokeSession
        var previousVisible: Layer? = null
        for (layer in document.stack.all()) {
            if (!layer.visible || layer.opacity <= 0.001f) continue
            LayerCompositor.drawLayer(
                canvas = canvas,
                layer = layer,
                clippingBase = if (layer.clipping) previousVisible else null,
                bounds = layerBounds,
            )
            previousVisible = layer
            if (session != null && !session.direct && session.layerId == layer.id) {
                drawStrokePreview(canvas, session)
            }
        }

        controller.shapePreview?.let { drawShapePreview(canvas, it, controller) }

        canvas.restoreToCount(save)
        drawCanvasBorder(canvas)
        drawHoverCursor(canvas, controller)
    }

    private fun drawPaper(canvas: Canvas, w: Float, h: Float, background: Int?) {
        if (background != null) {
            paperPaint.reset()
            paperPaint.color = background
            canvas.drawRect(0f, 0f, w, h, paperPaint)
            return
        }
        if (!checkerboardEnabled) {
            paperPaint.reset()
            paperPaint.color = 0x1AFFFFFF
            canvas.drawRect(0f, 0f, w, h, paperPaint)
            return
        }
        val shader = ensureCheckerShader()
        // Keep squares a constant size on screen regardless of zoom.
        val tileDoc = 16f / viewport.zoom.coerceAtLeast(0.01f)
        val scale = tileDoc / CHECKER_TILE_PX
        checkerMatrix.reset()
        checkerMatrix.setScale(scale, scale)
        shader.setLocalMatrix(checkerMatrix)
        checkerPaint.reset()
        checkerPaint.shader = shader
        canvas.drawRect(0f, 0f, w, h, checkerPaint)
    }

    private fun ensureCheckerShader(): BitmapShader {
        checkerShader?.let { return it }
        val size = CHECKER_TILE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val dark = Paint().apply { color = 0xFF1E1B26.toInt() }
        c.drawColor(0xFF2A2734.toInt())
        c.drawRect(0f, 0f, size / 2f, size / 2f, dark)
        c.drawRect(size / 2f, size / 2f, size.toFloat(), size.toFloat(), dark)
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        checkerShader = shader
        return shader
    }

    private fun drawStrokePreview(canvas: Canvas, session: StrokeSession) {
        previewPaint.reset()
        previewPaint.isFilterBitmap = true
        previewPaint.alpha = (session.strokeOpacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(1, 255)
        for (patch in session.previewTiles()) {
            val stroke = patch.stroke ?: continue
            if (stroke.isRecycled) continue
            val dst = RectF(
                patch.x.toFloat(),
                patch.y.toFloat(),
                (patch.x + patch.width).toFloat(),
                (patch.y + patch.height).toFloat(),
            )
            canvas.drawBitmap(stroke, null, dst, previewPaint)
        }
    }

    private fun drawShapePreview(canvas: Canvas, shape: studio.vela.art.drawing.document.ShapePreview, controller: EditorController) {
        shapePaint.reset()
        shapePaint.isAntiAlias = true
        shapePaint.color = controller.primaryColor
        shapePaint.style = if (controller.shapeFilled) Paint.Style.FILL else Paint.Style.STROKE
        shapePaint.strokeWidth = controller.activeBrush.size.coerceAtLeast(1f)
        shapePaint.strokeCap = Paint.Cap.ROUND
        val rect = RectF(
            minOf(shape.startX, shape.endX),
            minOf(shape.startY, shape.endY),
            maxOf(shape.startX, shape.endX),
            maxOf(shape.startY, shape.endY),
        )
        when (controller.shapeKind) {
            ShapeKind.LINE -> canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, shapePaint)
            ShapeKind.RECTANGLE -> canvas.drawRect(rect, shapePaint)
            ShapeKind.ELLIPSE -> canvas.drawOval(rect, shapePaint)
        }
    }

    private fun drawCanvasBorder(canvas: Canvas) {
        val bounds = viewport.screenBounds()
        borderPaint.color = 0x66FFFFFF
        borderPaint.strokeWidth = 1.5f * resources.displayMetrics.density
        val pad = 1f
        canvas.drawRect(
            bounds[0] - pad,
            bounds[1] - pad,
            bounds[2] + pad,
            bounds[3] + pad,
            borderPaint,
        )
        if (shadowEnabled) {
            borderPaint.color = 0x33000000
            borderPaint.strokeWidth = 6f * resources.displayMetrics.density
            canvas.drawRect(bounds[0], bounds[1], bounds[2], bounds[3], borderPaint)
        }
    }

    private fun drawHoverCursor(canvas: Canvas, controller: EditorController) {
        if (!showHoverCursor || !stylusHover) return
        val brush = controller.activeBrush
        val radiusDoc = brush.size / 2f
        val radiusScreen = radiusDoc * viewport.zoom
        if (radiusScreen < 2f || radiusScreen > width) return
        borderPaint.color = 0xCCFFFFFF.toInt()
        borderPaint.strokeWidth = 1.5f * resources.displayMetrics.density
        canvas.drawCircle(hoverX, hoverY, radiusScreen, borderPaint)
        borderPaint.color = 0x55000000
        borderPaint.strokeWidth = 1f
        canvas.drawCircle(hoverX, hoverY, radiusScreen + 1f, borderPaint)
    }

    // ------------------------------------------------------------------ input

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val controller = controller ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event, controller)
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event, controller)
            MotionEvent.ACTION_MOVE -> handleMove(event, controller)
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_UP -> handleUp(event, controller)
            MotionEvent.ACTION_CANCEL -> handleCancel(controller)
        }
        return true
    }

    private fun toolOf(event: MotionEvent, index: Int): InputTool = when (event.getToolType(index)) {
        MotionEvent.TOOL_TYPE_STYLUS -> InputTool.STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> InputTool.STYLUS_ERASER
        MotionEvent.TOOL_TYPE_MOUSE -> InputTool.MOUSE
        MotionEvent.TOOL_TYPE_FINGER -> InputTool.FINGER
        else -> InputTool.UNKNOWN
    }

    private fun handleDown(event: MotionEvent, controller: EditorController) {
        tapRecognizer.cancel()
        gestureActive = false
        gesturePointerCount = 0
        activeTool = toolOf(event, 0)
        if (activeTool == InputTool.STYLUS || activeTool == InputTool.STYLUS_ERASER) {
            stylusDown = true
        }
        val stylusActive = stylusDown || stylusHover
        if (!policy.shouldStartStroke(activeTool, stylusActive)) {
            // Fingers that may not draw are treated as a gesture until more pointers arrive.
            return
        }
        activePointerId = event.getPointerId(0)
        val sample = sampleOf(event, 0, event.eventTime, historical = false)
        drawing = controller.beginInput(sample, eraserStylus = activeTool == InputTool.STYLUS_ERASER)
        if (!drawing) {
            activePointerId = -1
            requestRender()
        }
    }

    private fun handlePointerDown(event: MotionEvent, controller: EditorController) {
        val pointerCount = event.pointerCount
        if (drawing) {
            if (policy.palmRejection && (activeTool == InputTool.STYLUS || activeTool == InputTool.STYLUS_ERASER)) {
                // Palm rejection: contact while the stylus is painting is ignored entirely.
                return
            }
            // Otherwise a second contact always means "navigate", never "draw".
            controller.cancelInput()
            drawing = false
            activePointerId = -1
        }
        if (!policy.allowFingerGestures(stylusDown || stylusHover)) return
        beginGestureIfNeeded(event, pointerCount)
    }

    private fun beginGestureIfNeeded(event: MotionEvent, pointerCount: Int) {
        if (pointerCount < 2) return
        val (focalX, focalY) = focalPoint(event)
        val spread = spreadOf(event)
        val angle = angleOf(event)
        if (!gestureActive) {
            gestureActive = true
            gesturePointerCount = pointerCount
            lastFocalX = focalX
            lastFocalY = focalY
            lastSpread = spread
            lastAngle = angle
            tapRecognizer.onGestureStart(event.eventTime, pointerCount, focalX, focalY)
        } else {
            gesturePointerCount = maxOf(gesturePointerCount, pointerCount)
            tapRecognizer.onGestureStart(event.eventTime, pointerCount, focalX, focalY)
        }
    }

    private fun handleMove(event: MotionEvent, controller: EditorController) {
        if (drawing) {
            val index = event.findPointerIndex(activePointerId)
            if (index < 0) return
            sampleBuffer.clear()
            for (h in 0 until event.historySize) {
                sampleBuffer.add(sampleOf(event, index, event.getHistoricalEventTime(h), historical = true, historyIndex = h))
            }
            sampleBuffer.add(sampleOf(event, index, event.eventTime, historical = false))
            controller.continueInput(sampleBuffer)
            return
        }
        if (event.pointerCount >= 2 && policy.allowFingerGestures(stylusDown || stylusHover)) {
            if (!gestureActive) beginGestureIfNeeded(event, event.pointerCount)
            val (focalX, focalY) = focalPoint(event)
            val spread = spreadOf(event)
            val angle = angleOf(event)
            tapRecognizer.onGestureMove(focalX, focalY, resources.displayMetrics.density)

            var updated = viewport.panBy(focalX - lastFocalX, focalY - lastFocalY)
            if (lastSpread > 1f && spread > 1f) {
                updated = updated.zoomAbout(focalX, focalY, spread / lastSpread)
            }
            if (policy.twoFingerRotate) {
                var delta = angle - lastAngle
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                if (kotlin.math.abs(delta) < 60f) {
                    updated = updated.rotateAbout(focalX, focalY, delta)
                }
            }
            viewport = updated.clampedPan()
            lastFocalX = focalX
            lastFocalY = focalY
            lastSpread = spread
            lastAngle = angle
            notifyViewport()
        }
    }

    private fun handlePointerUp(event: MotionEvent) {
        val pointerCount = event.pointerCount
        if (gestureActive && pointerCount >= 2) {
            tapRecognizer.onGestureStart(event.eventTime, pointerCount - 1, lastFocalX, lastFocalY)
        }
    }

    private fun handleUp(event: MotionEvent, controller: EditorController) {
        if (activeTool == InputTool.STYLUS || activeTool == InputTool.STYLUS_ERASER) {
            stylusDown = false
        }
        if (drawing) {
            drawing = false
            activePointerId = -1
            controller.endInput()
            return
        }
        if (gestureActive) {
            gestureActive = false
            when (val result = tapRecognizer.onGestureEnd(event.eventTime)) {
                GestureResult.Undo -> {
                    if (controller.undo() && hapticsEnabled) {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                }

                GestureResult.Redo -> {
                    if (controller.redo() && hapticsEnabled) {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                }

                else -> Unit
            }
            notifyViewport()
        }
    }

    private fun handleCancel(controller: EditorController) {
        if (drawing) {
            drawing = false
            activePointerId = -1
            controller.cancelInput()
        }
        gestureActive = false
        stylusDown = false
        tapRecognizer.cancel()
        requestRender()
    }

    /** Hover tracking drives the brush-size cursor and stylus proximity for palm rejection. */
    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                val index = event.actionIndex
                stylusHover = toolOf(event, index) == InputTool.STYLUS || toolOf(event, index) == InputTool.STYLUS_ERASER
                hoverX = event.getX(index)
                hoverY = event.getY(index)
                showHoverCursor = stylusHover
                requestRender()
            }

            MotionEvent.ACTION_HOVER_EXIT -> {
                stylusHover = false
                showHoverCursor = false
                requestRender()
            }
        }
        return true
    }

    private fun sampleOf(
        event: MotionEvent,
        index: Int,
        timeMs: Long,
        historical: Boolean,
        historyIndex: Int = 0,
    ): PointerSample {
        val screenX = if (historical) event.getHistoricalX(index, historyIndex) else event.getX(index)
        val screenY = if (historical) event.getHistoricalY(index, historyIndex) else event.getY(index)
        val doc = viewport.toDoc(screenX, screenY)
        val rawPressure = if (historical) event.getHistoricalPressure(index, historyIndex) else event.getPressure(index)
        val pressure = when {
            rawPressure <= 0.001f -> if (activeTool == InputTool.FINGER) 0.55f else 0.5f
            rawPressure > 1f -> 1f
            else -> rawPressure
        }
        val tilt = event.getAxisValue(MotionEvent.AXIS_TILT, index).let { if (it.isFinite()) it else 0f }
        val orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION, index).let { if (it.isFinite()) it else 0f }
        return PointerSample(
            x = doc[0],
            y = doc[1],
            pressure = pressure,
            tiltRadians = tilt,
            orientationRadians = orientation,
            timeMs = timeMs,
            tool = activeTool,
            historical = historical,
        )
    }

    private fun focalPoint(event: MotionEvent): Pair<Float, Float> {
        val count = minOf(2, event.pointerCount)
        if (count == 0) return 0f to 0f
        var x = 0f
        var y = 0f
        for (i in 0 until count) {
            x += event.getX(i)
            y += event.getY(i)
        }
        return (x / count) to (y / count)
    }

    private fun spreadOf(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return sqrt(dx * dx + dy * dy)
    }

    private fun angleOf(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        controller?.onInvalidate = null
        checkerShader = null
    }

    companion object {
        private const val CHECKER_TILE_PX = 16
    }
}
