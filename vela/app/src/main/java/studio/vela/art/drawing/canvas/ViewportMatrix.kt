package studio.vela.art.drawing.canvas

import android.graphics.Matrix
import studio.vela.art.core.geometry.Viewport

/**
 * Derives the Android matrix for a [Viewport].
 *
 * The matrix is equivalent to `translate(centre) · rotate(θ) · scale(zoom) · translate(-docCentre)`
 * in document coordinates, matching [Viewport.toScreen].
 */
fun Viewport.toMatrix(out: Matrix = Matrix()): Matrix {
    out.reset()
    out.postTranslate(-docCenterX, -docCenterY)
    out.postScale(zoom, zoom)
    out.postRotate(rotationDeg)
    out.postTranslate(centerX, centerY)
    return out
}

/** Screen-space bounding rectangle of the document, as [left, top, right, bottom]. */
fun Viewport.screenBounds(out: FloatArray = FloatArray(4)): FloatArray {
    val tl = toScreen(0f, 0f)
    val tr = toScreen(docWidth.toFloat(), 0f)
    val br = toScreen(docWidth.toFloat(), docHeight.toFloat())
    val bl = toScreen(0f, docHeight.toFloat())
    out[0] = minOf(tl[0], tr[0], br[0], bl[0])
    out[1] = minOf(tl[1], tr[1], br[1], bl[1])
    out[2] = maxOf(tl[0], tr[0], br[0], bl[0])
    out[3] = maxOf(tl[1], tr[1], br[1], bl[1])
    return out
}
