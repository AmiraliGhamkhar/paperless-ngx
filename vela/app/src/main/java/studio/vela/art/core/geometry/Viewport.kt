package studio.vela.art.core.geometry

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Immutable canvas viewport: maps document pixels to screen pixels.
 *
 * Screen = viewCentre + R(rotation) * (docPoint - docCentre) * zoom
 *
 * The type is deliberately free of Android dependencies so the transform maths can be
 * unit-tested on the JVM (see ViewportTest). The Android [android.graphics.Matrix] is
 * derived from it in `drawing/canvas/ViewportMatrix.kt`.
 */
data class Viewport(
    val docWidth: Int,
    val docHeight: Int,
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val rotationDeg: Float = 0f,
) {
    val centerX: Float get() = viewWidth / 2f + panX
    val centerY: Float get() = viewHeight / 2f + panY

    val docCenterX: Float get() = docWidth / 2f
    val docCenterY: Float get() = docHeight / 2f

    private val radians: Float get() = rotationDeg * (Math.PI.toFloat() / 180f)

    /** Writes the screen-space x/y of a document point into [out] and returns it. */
    fun toScreen(docX: Float, docY: Float, out: FloatArray = FloatArray(2)): FloatArray {
        val rad = radians
        val c = cos(rad)
        val s = sin(rad)
        val qx = (docX - docCenterX) * zoom
        val qy = (docY - docCenterY) * zoom
        out[0] = centerX + qx * c - qy * s
        out[1] = centerY + qx * s + qy * c
        return out
    }

    /** Writes the document-space x/y of a screen point into [out] and returns it. */
    fun toDoc(screenX: Float, screenY: Float, out: FloatArray = FloatArray(2)): FloatArray {
        val rad = radians
        val c = cos(rad)
        val s = sin(rad)
        val vx = screenX - centerX
        val vy = screenY - centerY
        val qx = vx * c + vy * s
        val qy = -vx * s + vy * c
        out[0] = qx / zoom + docCenterX
        out[1] = qy / zoom + docCenterY
        return out
    }

    fun panBy(dx: Float, dy: Float): Viewport = copy(panX = panX + dx, panY = panY + dy)

    /** Zooms by [factor] keeping the given screen point stationary under the fingers. */
    fun zoomAbout(focalX: Float, focalY: Float, factor: Float): Viewport {
        if (factor <= 0f || !factor.isFinite()) return this
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val k = newZoom / zoom
        if (abs(k - 1f) < 1e-5f) return this
        val cx = focalX + k * (centerX - focalX)
        val cy = focalY + k * (centerY - focalY)
        return copy(zoom = newZoom, panX = cx - viewWidth / 2f, panY = cy - viewHeight / 2f)
    }

    /** Rotates by [deltaDeg] keeping the given screen point stationary under the fingers. */
    fun rotateAbout(focalX: Float, focalY: Float, deltaDeg: Float): Viewport {
        if (abs(deltaDeg) < 1e-4f) return this
        val rad = deltaDeg * (Math.PI.toFloat() / 180f)
        val c = cos(rad)
        val s = sin(rad)
        val vx = centerX - focalX
        val vy = centerY - focalY
        val cx = focalX + vx * c - vy * s
        val cy = focalY + vx * s + vy * c
        return copy(
            rotationDeg = normalizeDegrees(rotationDeg + deltaDeg),
            panX = cx - viewWidth / 2f,
            panY = cy - viewHeight / 2f,
        )
    }

    fun setZoom(newZoom: Float, focalX: Float = viewWidth / 2f, focalY: Float = viewHeight / 2f): Viewport =
        zoomAbout(focalX, focalY, newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM) / zoom)

    /** Fits the whole document into the viewport with a small margin. */
    fun fit(marginFraction: Float = 0.06f): Viewport {
        if (viewWidth <= 1f || viewHeight <= 1f) return this
        val availW = viewWidth * (1f - marginFraction * 2f)
        val availH = viewHeight * (1f - marginFraction * 2f)
        val fitZoom = min(availW / docWidth, availH / docHeight)
        return copy(
            zoom = fitZoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
            panX = 0f,
            panY = 0f,
            rotationDeg = 0f,
        )
    }

    /** Keeps the document centre within the viewport so the canvas can never be lost. */
    fun clampedPan(): Viewport {
        if (viewWidth <= 1f || viewHeight <= 1f) return this
        val halfW = max(viewWidth * 0.5f, min(docWidth * zoom, viewWidth) * 0.5f)
        val halfH = max(viewHeight * 0.5f, min(docHeight * zoom, viewHeight) * 0.5f)
        val maxPanX = halfW * 0.98f
        val maxPanY = halfH * 0.98f
        return copy(
            panX = panX.coerceIn(-maxPanX, maxPanX),
            panY = panY.coerceIn(-maxPanY, maxPanY),
        )
    }

    /**
     * Visible document rectangle as [left, top, right, bottom]. Used for culling and for
     * deciding which tiles need to be composited.
     */
    fun visibleDocRect(out: FloatArray = FloatArray(4)): FloatArray {
        val corners = floatArrayOf(
            0f, 0f,
            viewWidth, 0f,
            viewWidth, viewHeight,
            0f, viewHeight,
        )
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        val pt = FloatArray(2)
        var i = 0
        while (i < corners.size) {
            toDoc(corners[i], corners[i + 1], pt)
            if (pt[0] < minX) minX = pt[0]
            if (pt[1] < minY) minY = pt[1]
            if (pt[0] > maxX) maxX = pt[0]
            if (pt[1] > maxY) maxY = pt[1]
            i += 2
        }
        out[0] = max(0f, minX)
        out[1] = max(0f, minY)
        out[2] = min(docWidth.toFloat(), maxX)
        out[3] = min(docHeight.toFloat(), maxY)
        return out
    }

    /** Screen pixels per document pixel, capped so the framebuffer stays affordable. */
    fun effectiveScale(): Float = zoom

    companion object {
        const val MIN_ZOOM = 0.05f
        const val MAX_ZOOM = 32f

        fun normalizeDegrees(deg: Float): Float {
            var d = deg % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }
    }
}
