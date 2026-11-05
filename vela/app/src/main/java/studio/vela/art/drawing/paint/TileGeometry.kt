package studio.vela.art.drawing.paint

import kotlin.math.ceil

/**
 * Tile addressing used by the incremental stroke buffer.
 *
 * Painting is performed in tiles so that
 *  - only the region a stroke actually touches is snapshotted for undo,
 *  - the live stroke can be previewed without re-uploading whole layer bitmaps.
 *
 * This class is pure maths and is covered by unit tests.
 */
object TileGeometry {

    const val TILE_SIZE = 256

    /** Packs a (col,row) pair into a single [Long] key. */
    fun key(col: Int, row: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL)

    fun col(key: Long): Int = (key and 0xFFFFFFFFL).toInt()

    fun row(key: Long): Int = (key ushr 32).toInt()

    fun tilesAcross(sizePx: Int, tileSize: Int = TILE_SIZE): Int = ceil(sizePx.toFloat() / tileSize).toInt()

    fun totalTiles(width: Int, height: Int, tileSize: Int = TILE_SIZE): Int =
        tilesAcross(width, tileSize) * tilesAcross(height, tileSize)

    /**
     * Intersects a document-space circle with the tile grid and returns the affected tile
     * bounds as `[col0, row0, col1, row1]` (inclusive), or null when fully outside the document.
     */
    fun tilesForCircle(
        cx: Float,
        cy: Float,
        radius: Float,
        docWidth: Int,
        docHeight: Int,
        tileSize: Int = TILE_SIZE,
        out: IntArray = IntArray(4),
    ): IntArray? {
        val left = (cx - radius).coerceIn(0f, docWidth.toFloat())
        val top = (cy - radius).coerceIn(0f, docHeight.toFloat())
        val right = (cx + radius).coerceIn(0f, docWidth.toFloat())
        val bottom = (cy + radius).coerceIn(0f, docHeight.toFloat())
        if (right <= left || bottom <= top) return null
        out[0] = (left / tileSize).toInt()
        out[1] = (top / tileSize).toInt()
        out[2] = ((right - 0.001f) / tileSize).toInt().coerceAtMost(tilesAcross(docWidth, tileSize) - 1)
        out[3] = ((bottom - 0.001f) / tileSize).toInt().coerceAtMost(tilesAcross(docHeight, tileSize) - 1)
        return out
    }

    /** Tile origin and clamped size for a tile in a document of the given size. */
    fun tileRect(col: Int, row: Int, docWidth: Int, docHeight: Int, tileSize: Int = TILE_SIZE, out: IntArray = IntArray(4)): IntArray {
        val x = col * tileSize
        val y = row * tileSize
        out[0] = x
        out[1] = y
        out[2] = (tileSize).coerceAtMost(docWidth - x)
        out[3] = (tileSize).coerceAtMost(docHeight - y)
        return out
    }

    /** All tile keys covering the given document rectangle. */
    fun tilesForRect(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        docWidth: Int,
        docHeight: Int,
        tileSize: Int = TILE_SIZE,
    ): List<Long> {
        val keys = ArrayList<Long>()
        if (right <= left || bottom <= top) return keys
        val c0 = left.coerceAtLeast(0) / tileSize
        val r0 = top.coerceAtLeast(0) / tileSize
        val c1 = ((right - 1).coerceAtMost(docWidth - 1)) / tileSize
        val r1 = ((bottom - 1).coerceAtMost(docHeight - 1)) / tileSize
        for (row in r0..r1) {
            for (col in c0..c1) {
                keys.add(key(col, row))
            }
        }
        return keys
    }
}
