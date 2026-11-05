package studio.vela.art.data.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import studio.vela.art.drawing.layers.Layer
import studio.vela.art.domain.model.BlendKind
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reader/writer for the native `.vela` project format: a ZIP archive holding a JSON manifest,
 * one PNG per layer and a thumbnail.
 *
 * Guarantees:
 *  - writes go to a temporary file in the same directory and are then atomically renamed,
 *  - the previous archive is retained as `document.vela.bak` before being replaced,
 *  - reading validates the manifest, entry CRCs (via [ZipInputStream]) and layer dimensions,
 *    and reports structured failures instead of throwing into the UI.
 */
object DocumentCodec {

    const val FILE_EXTENSION = "vela"
    private const val BUFFER = 32 * 1024

    sealed interface LoadResult {
        data class Success(val manifest: DocumentManifest, val layers: List<Layer>, val thumbnail: Bitmap?) : LoadResult
        data class Failure(val reason: String, val recoverable: Boolean = false) : LoadResult
    }

    /** Writes [manifest] + [layers] atomically to [target]. */
    fun write(
        target: File,
        manifest: DocumentManifest,
        layers: List<Layer>,
        thumbnail: Bitmap?,
        keepBackup: Boolean = true,
    ) {
        val dir = target.parentFile ?: error("Project file has no parent directory")
        if (!dir.exists()) dir.mkdirs()
        val temp = File(dir, "${target.name}.tmp")
        if (temp.exists()) temp.delete()

        FileOutputStream(temp).use { fileOut ->
            ZipOutputStream(BufferedOutputStream(fileOut, BUFFER)).use { zip ->
                zip.setLevel(java.util.zip.Deflater.BEST_SPEED)
                zip.putNextEntry(ZipEntry(DocumentManifest.MANIFEST_ENTRY))
                zip.write(DocumentManifest.json.encodeToString(DocumentManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()

                for (layer in layers) {
                    zip.putNextEntry(ZipEntry(DocumentManifest.LAYER_PREFIX + layer.id + ".png"))
                    layer.bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                    zip.closeEntry()
                }

                if (thumbnail != null) {
                    zip.putNextEntry(ZipEntry(DocumentManifest.THUMBNAIL_ENTRY))
                    thumbnail.compress(Bitmap.CompressFormat.PNG, 90, zip)
                    zip.closeEntry()
                }
            }
            fileOut.fd.sync()
        }

        if (keepBackup && target.exists()) {
            val backup = File(dir, "${target.name}.bak")
            if (backup.exists()) backup.delete()
            target.copyTo(backup, overwrite = true)
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    /** Reads a `.vela` archive. Never throws: failures are returned as [LoadResult.Failure]. */
    fun read(file: File): LoadResult {
        if (!file.exists()) return LoadResult.Failure("Project file not found", recoverable = true)
        if (file.length() == 0L) return LoadResult.Failure("Project file is empty", recoverable = true)
        return try {
            readArchive(file)
        } catch (e: java.util.zip.ZipException) {
            LoadResult.Failure("Project archive is corrupt: ${e.message}", recoverable = true)
        } catch (e: java.io.IOException) {
            LoadResult.Failure("Could not read project: ${e.message}", recoverable = true)
        } catch (e: kotlinx.serialization.SerializationException) {
            LoadResult.Failure("Project manifest is invalid", recoverable = true)
        }
    }

    private fun readArchive(file: File): LoadResult {
        var manifest: DocumentManifest? = null
        var thumbnail: Bitmap? = null
        val bitmaps = LinkedHashMap<String, Bitmap>()

        ZipInputStream(BufferedInputStream(file.inputStream(), BUFFER)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == DocumentManifest.MANIFEST_ENTRY -> {
                        val bytes = zip.readBytes()
                        manifest = DocumentManifest.json.decodeFromString(
                            DocumentManifest.serializer(),
                            bytes.toString(Charsets.UTF_8),
                        )
                    }

                    name == DocumentManifest.THUMBNAIL_ENTRY -> {
                        thumbnail = BitmapFactory.decodeStream(zip)
                    }

                    name.startsWith(DocumentManifest.LAYER_PREFIX) -> {
                        val id = name.removePrefix(DocumentManifest.LAYER_PREFIX).removeSuffix(".png")
                        val bitmap = BitmapFactory.decodeStream(zip)?.let {
                            // Layer bitmaps must be mutable so they can be painted on.
                            if (it.isMutable) it else it.copy(Bitmap.Config.ARGB_8888, true)
                        }
                        if (bitmap != null) bitmaps[id] = bitmap
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val raw = manifest ?: return LoadResult.Failure("Project manifest is missing", recoverable = true)
        when (val validation = raw.validate()) {
            is ManifestValidation.TooNew ->
                return LoadResult.Failure("Project was created by a newer version (format ${validation.version})")
            is ManifestValidation.Corrupt -> return LoadResult.Failure(validation.reason, recoverable = true)
            ManifestValidation.Valid -> Unit
        }

        val migrated = DocumentManifest.migrate(raw)
        val layers = ArrayList<Layer>(migrated.layers.size)
        for (layerManifest in migrated.layers) {
            val bitmap = bitmaps[layerManifest.id]
            if (bitmap == null) {
                layers.forEach { it.recycle() }
                bitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
                thumbnail?.recycle()
                return LoadResult.Failure("Layer '${layerManifest.name}' is missing from the archive", recoverable = true)
            }
            if (bitmap.width != migrated.width || bitmap.height != migrated.height) {
                layers.forEach { it.recycle() }
                bitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
                thumbnail?.recycle()
                return LoadResult.Failure("Layer '${layerManifest.name}' has unexpected dimensions", recoverable = true)
            }
            layers.add(
                Layer(
                    id = layerManifest.id,
                    name = layerManifest.name,
                    bitmap = bitmap,
                    opacity = layerManifest.opacity,
                    blend = BlendKind.fromNameOrNormal(layerManifest.blend),
                    visible = layerManifest.visible,
                    locked = layerManifest.locked,
                    alphaLocked = layerManifest.alphaLocked,
                    clipping = layerManifest.clipping,
                    isBackground = layerManifest.isBackground,
                ),
            )
        }
        return LoadResult.Success(migrated, layers, thumbnail)
    }

    /** Builds the manifest for a document that is about to be written. */
    fun manifestFor(
        projectId: Long,
        name: String,
        width: Int,
        height: Int,
        dpi: Int,
        background: Int?,
        activeLayerId: String?,
        createdAt: Long,
        layers: List<Layer>,
    ): DocumentManifest = DocumentManifest(
        projectId = projectId,
        name = name,
        width = width,
        height = height,
        dpi = dpi,
        background = background,
        activeLayerId = activeLayerId,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        layers = layers.map { layer ->
            LayerManifest(
                id = layer.id,
                name = layer.name,
                file = DocumentManifest.LAYER_PREFIX + layer.id + ".png",
                width = layer.width,
                height = layer.height,
                opacity = layer.opacity,
                blend = layer.blend.name,
                visible = layer.visible,
                locked = layer.locked,
                alphaLocked = layer.alphaLocked,
                clipping = layer.clipping,
                isBackground = layer.isBackground,
            )
        },
    )
}
