package studio.vela.art.data.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Metadata stored inside a `.vela` project archive.
 *
 * The manifest is pure data (no Android types) so format compatibility, validation and
 * migration can be unit-tested on the JVM.
 */
@Serializable
data class DocumentManifest(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val projectId: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val dpi: Int,
    /** ARGB paper colour, or null for a transparent canvas. */
    val background: Int? = null,
    val activeLayerId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val layers: List<LayerManifest> = emptyList(),
    val appVersion: String = "1.0.0",
) {
    fun validate(): ManifestValidation {
        if (formatVersion <= 0) return ManifestValidation.Corrupt("Invalid format version")
        if (formatVersion > CURRENT_FORMAT_VERSION) {
            return ManifestValidation.TooNew(formatVersion)
        }
        if (width <= 0 || height <= 0) return ManifestValidation.Corrupt("Invalid canvas size")
        if (width > MAX_SIDE || height > MAX_SIDE) return ManifestValidation.Corrupt("Canvas too large")
        if (layers.isEmpty()) return ManifestValidation.Corrupt("Project has no layers")
        if (layers.any { it.file.isBlank() }) return ManifestValidation.Corrupt("Layer file missing")
        if (layers.map { it.id }.toSet().size != layers.size) return ManifestValidation.Corrupt("Duplicate layer ids")
        return ManifestValidation.Valid
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val MAX_SIDE = 16384
        const val MANIFEST_ENTRY = "manifest.json"
        const val THUMBNAIL_ENTRY = "thumb.png"
        const val LAYER_PREFIX = "layers/"

        val json: Json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun migrate(manifest: DocumentManifest): DocumentManifest = when (manifest.formatVersion) {
            CURRENT_FORMAT_VERSION -> manifest
            // Future migrations slot in here, e.g. 1 -> 2 renames blend mode strings.
            else -> manifest.copy(formatVersion = CURRENT_FORMAT_VERSION)
        }
    }
}

@Serializable
data class LayerManifest(
    val id: String,
    val name: String,
    val file: String,
    val width: Int,
    val height: Int,
    val opacity: Float = 1f,
    val blend: String = "NORMAL",
    val visible: Boolean = true,
    val locked: Boolean = false,
    val alphaLocked: Boolean = false,
    val clipping: Boolean = false,
    val isBackground: Boolean = false,
)

sealed interface ManifestValidation {
    data object Valid : ManifestValidation
    data class Corrupt(val reason: String) : ManifestValidation
    data class TooNew(val version: Int) : ManifestValidation
}
