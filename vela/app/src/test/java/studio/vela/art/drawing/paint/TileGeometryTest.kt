package studio.vela.art.drawing.paint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TileGeometryTest {

    @Test
    fun `tile keys round trip`() {
        val key = TileGeometry.key(7, 13)
        assertEquals(7, TileGeometry.col(key))
        assertEquals(13, TileGeometry.row(key))
    }

    @Test
    fun `tile keys are unique for adjacent tiles`() {
        val keys = HashSet<Long>()
        for (row in 0 until 20) {
            for (col in 0 until 20) {
                keys.add(TileGeometry.key(col, row))
            }
        }
        assertEquals(400, keys.size)
    }

    @Test
    fun `tile count matches the document size`() {
        assertEquals(1, TileGeometry.tilesAcross(1))
        assertEquals(1, TileGeometry.tilesAcross(256))
        assertEquals(2, TileGeometry.tilesAcross(257))
        assertEquals(16, TileGeometry.totalTiles(1024, 1024))
    }

    @Test
    fun `circle at the origin touches a single tile`() {
        val bounds = TileGeometry.tilesForCircle(10f, 10f, 5f, 1024, 1024)
        assertNotNull(bounds)
        assertEquals(0, bounds!![0])
        assertEquals(0, bounds[1])
        assertEquals(0, bounds[2])
        assertEquals(0, bounds[3])
    }

    @Test
    fun `circle spanning a tile boundary touches both tiles`() {
        val bounds = TileGeometry.tilesForCircle(250f, 250f, 20f, 1024, 1024)
        assertNotNull(bounds)
        assertEquals(0, bounds!![0])
        assertEquals(0, bounds[1])
        assertEquals(1, bounds[2])
        assertEquals(1, bounds[3])
    }

    @Test
    fun `circle outside the document is rejected`() {
        assertNull(TileGeometry.tilesForCircle(-100f, -100f, 10f, 1024, 1024))
        assertNull(TileGeometry.tilesForCircle(5000f, 5000f, 10f, 1024, 1024))
    }

    @Test
    fun `tile rect is clamped at document edges`() {
        val rect = TileGeometry.tileRect(1, 1, 300, 300)
        assertEquals(256, rect[0])
        assertEquals(256, rect[1])
        assertEquals(44, rect[2])
        assertEquals(44, rect[3])
    }

    @Test
    fun `tiles for rect covers the requested area`() {
        val keys = TileGeometry.tilesForRect(0, 0, 512, 512, 1024, 1024)
        assertEquals(4, keys.size)
    }

    @Test
    fun `empty rect produces no tiles`() {
        assertTrueEmpty(TileGeometry.tilesForRect(10, 10, 10, 10, 1024, 1024))
    }

    private fun assertTrueEmpty(list: List<Long>) {
        assertEquals(0, list.size)
    }
}
