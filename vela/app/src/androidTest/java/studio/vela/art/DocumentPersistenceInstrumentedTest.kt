package studio.vela.art

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import studio.vela.art.data.document.DocumentCodec
import studio.vela.art.drawing.layers.Layer
import studio.vela.art.drawing.tools.FloodFill
import studio.vela.art.domain.model.BlendKind
import java.io.File
import java.util.UUID

/**
 * On-device tests for the parts that need real bitmaps and the framework Canvas.
 */
@RunWith(AndroidJUnit4::class)
class DocumentPersistenceInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun layer(width: Int, height: Int, fill: Int, name: String): Layer {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(fill)
        return Layer(id = UUID.randomUUID().toString(), name = name, bitmap = bitmap)
    }

    @Test
    fun projectArchiveRoundTripsLayersAndProperties() {
        val width = 128
        val height = 96
        val bottom = layer(width, height, Color.WHITE, "Background")
        val top = layer(width, height, Color.argb(128, 255, 0, 0), "Red")
        top.opacity = 0.6f
        top.blend = BlendKind.MULTIPLY
        top.alphaLocked = true

        val file = File(context.cacheDir, "roundtrip-${System.nanoTime()}.vela")
        val manifest = DocumentCodec.manifestFor(
            projectId = 1L,
            name = "Round trip",
            width = width,
            height = height,
            dpi = 300,
            background = Color.WHITE,
            activeLayerId = top.id,
            createdAt = 0L,
            layers = listOf(bottom, top),
        )
        DocumentCodec.write(file, manifest, listOf(bottom, top), thumbnail = null)

        val result = DocumentCodec.read(file)
        assertTrue("read failed: $result", result is DocumentCodec.LoadResult.Success)
        val success = result as DocumentCodec.LoadResult.Success
        assertEquals(2, success.layers.size)
        assertEquals("Round trip", success.manifest.name)
        assertEquals(width, success.manifest.width)
        val restoredTop = success.layers.first { it.name == "Red" }
        assertEquals(0.6f, restoredTop.opacity, 0.001f)
        assertEquals(BlendKind.MULTIPLY, restoredTop.blend)
        assertTrue(restoredTop.alphaLocked)
        assertEquals(top.id, success.manifest.activeLayerId)

        success.layers.forEach { it.recycle() }
        file.delete()
    }

    @Test
    fun corruptArchiveIsReportedNotThrown() {
        val file = File(context.cacheDir, "corrupt-${System.nanoTime()}.vela")
        file.writeText("this is not a zip archive")
        val result = DocumentCodec.read(file)
        assertTrue(result is DocumentCodec.LoadResult.Failure)
        file.delete()
    }

    @Test
    fun floodFillFillsTheContiguousRegionOnly() {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        // Vertical wall dividing the canvas into two regions.
        for (y in 0 until 40) bitmap.setPixel(20, y, Color.BLACK)

        val dirty = FloodFill.fill(
            bitmap = bitmap,
            seedX = 5,
            seedY = 5,
            color = Color.RED,
            tolerance = 0f,
            alphaLock = false,
            contiguous = true,
        )

        assertTrue("fill should report a dirty rect", dirty != null)
        assertEquals(Color.RED, bitmap.getPixel(5, 5))
        assertEquals(Color.RED, bitmap.getPixel(19, 39))
        assertEquals(Color.BLACK, bitmap.getPixel(20, 10))
        assertEquals("fill must not cross the wall", Color.WHITE, bitmap.getPixel(21, 5))
    }

    @Test
    fun globalFillReplacesEveryMatchingPixel() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(0, 0, Color.BLACK)

        FloodFill.fill(
            bitmap = bitmap,
            seedX = 5,
            seedY = 5,
            color = Color.BLUE,
            tolerance = 0f,
            contiguous = false,
        )

        assertEquals(Color.BLUE, bitmap.getPixel(15, 15))
        assertEquals(Color.BLACK, bitmap.getPixel(0, 0))
    }
}
