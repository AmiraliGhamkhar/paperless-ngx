package studio.vela.art.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import studio.vela.art.data.db.ProjectDao
import studio.vela.art.data.db.ProjectEntity
import studio.vela.art.data.document.DocumentCodec
import studio.vela.art.data.storage.ProjectStorage
import studio.vela.art.domain.model.CanvasSpec
import studio.vela.art.drawing.document.ArtDocument
import studio.vela.art.drawing.layers.Layer
import studio.vela.art.drawing.layers.LayerStack
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Lightweight catalogue row used by the gallery. */
data class ProjectSummary(
    val id: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val updatedAt: Long,
    val createdAt: Long,
    val favorite: Boolean,
    val collection: String?,
    val thumbnailPath: String?,
)

class ProjectRepository(
    private val context: Context,
    private val dao: ProjectDao,
    private val storage: ProjectStorage,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    sealed interface OpenResult {
        data class Success(val document: ArtDocument, val recoveryNote: String? = null) : OpenResult
        data class Failure(val reason: String) : OpenResult
    }

    fun observeProjects(): Flow<List<ProjectSummary>> =
        dao.observeAll().map { rows -> rows.map(::toSummary) }

    fun observeFavorites(): Flow<List<ProjectSummary>> =
        dao.observeFavorites().map { rows -> rows.map(::toSummary) }

    private fun toSummary(entity: ProjectEntity) = ProjectSummary(
        id = entity.id,
        name = entity.name,
        width = entity.width,
        height = entity.height,
        updatedAt = entity.updatedAt,
        createdAt = entity.createdAt,
        favorite = entity.favorite,
        collection = entity.collection,
        thumbnailPath = entity.thumbnailPath,
    )

    suspend fun createProject(
        name: String,
        spec: CanvasSpec,
        background: Int?,
        collection: String? = null,
    ): ArtDocument = withContext(io) {
        val now = System.currentTimeMillis()
        val safeName = name.ifBlank { "Untitled" }.trim()
        val provisional = ProjectEntity(
            name = safeName,
            width = spec.width,
            height = spec.height,
            dpi = spec.dpi,
            background = background,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = now,
            documentPath = "",
            collection = collection,
        )
        val id = dao.upsert(provisional)
        val entity = provisional.copy(
            id = id,
            documentPath = storage.documentFile(id).absolutePath,
        )
        dao.upsert(entity)

        val document = ArtDocument(
            projectId = id,
            name = safeName,
            width = spec.width,
            height = spec.height,
            dpi = spec.dpi,
            background = background,
        )
        if (background != null) {
            val backgroundBitmap = LayerStack.createBitmap(spec.width, spec.height)
            LayerStack.fillRect(backgroundBitmap, background)
            val backgroundLayer = Layer(
                id = UUID.randomUUID().toString(),
                name = "Background",
                bitmap = backgroundBitmap,
                isBackground = true,
            )
            document.stack.add(backgroundLayer)
        }
        val paintLayer = document.createLayer(name = "Layer 1")
        document.activeLayerId = paintLayer.id
        saveInternal(document)
        document
    }

    /** Opens a project, falling back to the temporary/backup archive when the main file is bad. */
    suspend fun openProject(projectId: Long): OpenResult = withContext(io) {
        val entity = dao.findById(projectId)
            ?: return@withContext OpenResult.Failure("This project is no longer in the library")

        val primary = storage.documentFile(projectId)
        val attempts = listOf(
            primary to null,
            storage.backupFile(projectId) to "Recovered from backup",
            storage.temporaryFile(projectId) to "Recovered from temporary save",
        )
        var lastError = "Project file is missing"
        for ((file, note) in attempts) {
            if (!file.exists()) continue
            when (val result = DocumentCodec.read(file)) {
                is DocumentCodec.LoadResult.Success -> {
                    val document = ArtDocument(
                        projectId = entity.id,
                        name = entity.name,
                        width = result.manifest.width,
                        height = result.manifest.height,
                        dpi = result.manifest.dpi,
                        background = result.manifest.background,
                    )
                    document.adoptLayers(result.layers, result.manifest.activeLayerId)
                    document.markSaved()
                    dao.touch(entity.id, System.currentTimeMillis(), System.currentTimeMillis())
                    result.thumbnail?.recycle()
                    return@withContext OpenResult.Success(document, note)
                }

                is DocumentCodec.LoadResult.Failure -> lastError = result.reason
            }
        }
        OpenResult.Failure(lastError)
    }

    /**
     * Persists the document. Callers must ensure no stroke is in flight, so layer bitmaps are
     * not being mutated while they are compressed.
     */
    suspend fun save(document: ArtDocument, withThumbnail: Boolean = true): Boolean = withContext(io) {
        try {
            saveInternal(document, withThumbnail)
            document.markSaved()
            true
        } catch (e: IOException) {
            false
        }
    }

    private suspend fun saveInternal(document: ArtDocument, withThumbnail: Boolean = true) {
        val layers = document.stack.all()
        if (layers.isEmpty()) return
        val manifest = DocumentCodec.manifestFor(
            projectId = document.projectId,
            name = document.name,
            width = document.width,
            height = document.height,
            dpi = document.dpi,
            background = document.background,
            activeLayerId = document.activeLayerId,
            createdAt = System.currentTimeMillis(),
            layers = layers,
        )
        var thumbnail: Bitmap? = null
        if (withThumbnail) {
            thumbnail = document.thumbnail()
        }
        DocumentCodec.write(storage.documentFile(document.projectId), manifest, layers, thumbnail)

        var thumbnailPath: String? = null
        if (thumbnail != null) {
            val file = storage.thumbnailFile(document.projectId)
            FileOutputStream(file).use { out ->
                thumbnail.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            thumbnailPath = file.absolutePath
            thumbnail.recycle()
        }
        dao.updateAfterSave(document.projectId, System.currentTimeMillis(), thumbnailPath)
    }

    suspend fun deleteProject(projectId: Long) = withContext(io) {
        dao.deleteById(projectId)
        storage.deleteProject(projectId)
    }

    suspend fun setFavorite(projectId: Long, favorite: Boolean) = withContext(io) {
        dao.setFavorite(projectId, favorite)
    }

    suspend fun renameProject(projectId: Long, name: String) {
        withContext(io) {
            val entity = dao.findById(projectId) ?: return@withContext
            dao.upsert(entity.copy(name = name.trim().ifBlank { entity.name }, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun duplicateProject(projectId: Long): Long? = withContext(io) {
        val entity = dao.findById(projectId) ?: return@withContext null
        when (val result = openProject(projectId)) {
            is OpenResult.Success -> {
                val copy = createProject(
                    name = "${entity.name} copy",
                    spec = CanvasSpec(result.document.width, result.document.height, result.document.dpi),
                    background = result.document.background,
                    collection = entity.collection,
                )
                // Replace the placeholder layer with the source layers.
                val sourceLayers = result.document.stack.all().mapNotNull { layer ->
                    val copied = layer.bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return@mapNotNull null
                    Layer(
                        id = UUID.randomUUID().toString(),
                        name = layer.name,
                        bitmap = copied,
                    ).also { layer.copyPropertiesTo(it) }
                }
                copy.adoptLayers(sourceLayers, sourceLayers.lastOrNull()?.id)
                saveInternal(copy)
                result.document.stack.recycleAll()
                copy.projectId
            }

            is OpenResult.Failure -> null
        }
    }

    /** Imports an image from a content Uri as a new project. */
    suspend fun importImage(uri: Uri, name: String): Long? = withContext(io) {
        val bitmap = decodeSampled(uri) ?: return@withContext null
        val spec = CanvasSpec(bitmap.width, bitmap.height, 300).clampedToLimits()
        val project = createProject(name = name, spec = spec, background = Color.WHITE)
        val layer = project.activeLayer() ?: return@withContext null
        layer.canvas.drawBitmap(bitmap, 0f, 0f, null)
        layer.name = "Imported art"
        layer.markThumbnailDirty()
        bitmap.recycle()
        project.notifyContentChanged()
        saveInternal(project)
        project.projectId
    }

    private fun decodeSampled(uri: Uri): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)?.let { decoded ->
                    if (decoded.isMutable) {
                        decoded
                    } else {
                        decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
                    }
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    fun storageUsageBytes(): Long = storage.storageUsageBytes()

    fun cleanupTemporaryFiles() = storage.cleanupTemporaryFiles()

    fun thumbnailFile(projectId: Long): File = storage.thumbnailFile(projectId)
}
