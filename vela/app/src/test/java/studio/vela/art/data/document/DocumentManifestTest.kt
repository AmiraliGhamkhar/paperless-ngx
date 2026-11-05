package studio.vela.art.data.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentManifestTest {

    private fun manifest(version: Int = DocumentManifest.CURRENT_FORMAT_VERSION) = DocumentManifest(
        formatVersion = version,
        projectId = 42L,
        name = "Portrait study",
        width = 2048,
        height = 1536,
        dpi = 300,
        background = 0xFFFFFFFF.toInt(),
        activeLayerId = "layer-2",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_100_000L,
        layers = listOf(
            LayerManifest(
                id = "layer-1",
                name = "Background",
                file = "layers/layer-1.png",
                width = 2048,
                height = 1536,
                isBackground = true,
            ),
            LayerManifest(
                id = "layer-2",
                name = "Paint",
                file = "layers/layer-2.png",
                width = 2048,
                height = 1536,
                opacity = 0.75f,
                blend = "MULTIPLY",
                alphaLocked = true,
                clipping = true,
            ),
        ),
    )

    @Test
    fun `manifest survives a json round trip`() {
        val original = manifest()
        val json = DocumentManifest.json.encodeToString(DocumentManifest.serializer(), original)
        val decoded = DocumentManifest.json.decodeFromString(DocumentManifest.serializer(), json)
        assertEquals(original, decoded)
    }

    @Test
    fun `valid manifest passes validation`() {
        assertEquals(ManifestValidation.Valid, manifest().validate())
    }

    @Test
    fun `manifest without layers is corrupt`() {
        val result = manifest().copy(layers = emptyList()).validate()
        assertTrue(result is ManifestValidation.Corrupt)
    }

    @Test
    fun `manifest with duplicate layer ids is corrupt`() {
        val source = manifest()
        val duplicated = source.copy(layers = source.layers + source.layers.first())
        assertTrue(duplicated.validate() is ManifestValidation.Corrupt)
    }

    @Test
    fun `manifest with invalid canvas size is corrupt`() {
        assertTrue(manifest().copy(width = 0).validate() is ManifestValidation.Corrupt)
        assertTrue(manifest().copy(height = DocumentManifest.MAX_SIDE + 1).validate() is ManifestValidation.Corrupt)
    }

    @Test
    fun `manifest from a newer format version is reported as too new`() {
        val result = manifest(version = DocumentManifest.CURRENT_FORMAT_VERSION + 5).validate()
        assertTrue(result is ManifestValidation.TooNew)
        assertEquals(
            DocumentManifest.CURRENT_FORMAT_VERSION + 5,
            (result as ManifestValidation.TooNew).version,
        )
    }

    @Test
    fun `migration keeps the current version untouched`() {
        val migrated = DocumentManifest.migrate(manifest())
        assertEquals(DocumentManifest.CURRENT_FORMAT_VERSION, migrated.formatVersion)
        assertEquals(manifest(), migrated)
    }

    @Test
    fun `unknown json fields are ignored for forward compatibility`() {
        val json = """
            {
              "formatVersion": 1,
              "projectId": 7,
              "name": "Future",
              "width": 800,
              "height": 600,
              "dpi": 72,
              "layers": [
                {"id":"l1","name":"L","file":"layers/l1.png","width":800,"height":600,"futureField":true}
              ],
              "unknownTopLevel": {"a": 1}
            }
        """.trimIndent()
        val decoded = DocumentManifest.json.decodeFromString(DocumentManifest.serializer(), json)
        assertEquals("Future", decoded.name)
        assertEquals(1, decoded.layers.size)
        assertEquals(ManifestValidation.Valid, decoded.validate())
    }

    @Test
    fun `blend names fall back to normal`() {
        assertEquals("MULTIPLY", studio.vela.art.domain.model.BlendKind.fromNameOrNormal("MULTIPLY").name)
        assertEquals("NORMAL", studio.vela.art.domain.model.BlendKind.fromNameOrNormal("NOT_A_MODE").name)
        assertEquals("NORMAL", studio.vela.art.domain.model.BlendKind.fromNameOrNormal(null).name)
    }
}
