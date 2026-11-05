package studio.vela.art.drawing.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/**
 * Scanline flood fill on a layer bitmap.
 *
 * Pixels are read once into an int array and written back once, which keeps a full-canvas
 * fill at a single allocation instead of per-pixel JNI calls.
 */
object FloodFill {

    /**
     * Fills the contiguous region containing ([seedX], [seedY]).
     *
     * @param tolerance 0..1 colour distance tolerance.
     * @param alphaLock when true only pixels that already have alpha are filled.
     * @return the dirty rectangle that changed, or null when nothing was filled.
     */
    fun fill(
        bitmap: Bitmap,
        seedX: Int,
        seedY: Int,
        color: Int,
        tolerance: Float = 0.1f,
        alphaLock: Boolean = false,
        contiguous: Boolean = true,
    ): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        if (seedX < 0 || seedY < 0 || seedX >= width || seedY >= height) return null
        if (bitmap.isRecycled) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val target = pixels[seedY * width + seedX]
        if (target == color && tolerance <= 0f) return null

        val maxDistance = (tolerance.coerceIn(0f, 1f) * 255f * 4f).toInt()
        val fillColor = if (alphaLock) {
            // Preserve existing alpha: replace RGB only.
            Color.argb(Color.alpha(target), Color.red(color), Color.green(color), Color.blue(color))
        } else {
            color
        }

        fun matches(index: Int): Boolean {
            val px = pixels[index]
            if (alphaLock && Color.alpha(px) == 0) return false
            if (px == target) return true
            val d = kotlin.math.abs(Color.alpha(px) - Color.alpha(target)) +
                kotlin.math.abs(Color.red(px) - Color.red(target)) +
                kotlin.math.abs(Color.green(px) - Color.green(target)) +
                kotlin.math.abs(Color.blue(px) - Color.blue(target))
            return d <= maxDistance
        }

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        var filled = 0

        if (!contiguous) {
            for (i in pixels.indices) {
                if (!matches(i)) continue
                val px = if (alphaLock) {
                    Color.argb(Color.alpha(pixels[i]), Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor))
                } else {
                    fillColor
                }
                pixels[i] = px
                filled++
                val x = i % width
                val y = i / width
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        } else {
            val visited = BooleanArray(width * height)
            val stack = IntArray(width * height)
            var stackSize = 0
            stack[stackSize++] = seedY * width + seedX
            while (stackSize > 0) {
                val index = stack[--stackSize]
                if (visited[index]) continue
                val startY = index / width
                var x = index % width
                // Walk left.
                while (x > 0 && !visited[startY * width + x - 1] && matches(startY * width + x - 1)) x--
                var spanAbove = false
                var spanBelow = false
                while (x < width && !visited[startY * width + x] && matches(startY * width + x)) {
                    val current = startY * width + x
                    visited[current] = true
                    pixels[current] = if (alphaLock) {
                        Color.argb(Color.alpha(pixels[current]), Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor))
                    } else {
                        fillColor
                    }
                    filled++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (startY < minY) minY = startY
                    if (startY > maxY) maxY = startY

                    if (startY > 0) {
                        val up = (startY - 1) * width + x
                        val upMatches = !visited[up] && matches(up)
                        if (!spanAbove && upMatches) {
                            if (stackSize < stack.size) stack[stackSize++] = up
                            spanAbove = true
                        } else if (spanAbove && !upMatches) {
                            spanAbove = false
                        }
                    }
                    if (startY < height - 1) {
                        val down = (startY + 1) * width + x
                        val downMatches = !visited[down] && matches(down)
                        if (!spanBelow && downMatches) {
                            if (stackSize < stack.size) stack[stackSize++] = down
                            spanBelow = true
                        } else if (spanBelow && !downMatches) {
                            spanBelow = false
                        }
                    }
                    x++
                }
            }
        }

        if (filled == 0) return null
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return Rect(minX, minY, maxX + 1, maxY + 1)
    }
}
