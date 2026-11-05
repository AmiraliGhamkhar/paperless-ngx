package studio.vela.art.core.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import studio.vela.art.data.storage.ProjectStorage
import studio.vela.art.drawing.document.ArtDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class ExportFormat(
    val label: String,
    val mimeType: String,
    val extension: String,
    val supportsQuality: Boolean,
) {
    PNG("PNG", "image/png", "png", false),
    JPEG("JPEG", "image/jpeg", "jpg", true),
    WEBP("WebP", "image/webp", "webp", true),
}

data class ExportRequest(
    val format: ExportFormat = ExportFormat.PNG,
    val quality: Int = 95,
    val includeBackground: Boolean = true,
    val scale: Float = 1f,
)

sealed interface ExportResult {
    data class Success(val file: File, val uri: Uri, val bytes: Long) : ExportResult
    data class Failure(val reason: String) : ExportResult
}

/**
 * Renders the document to PNG/JPEG/WebP and exposes it through the system share sheet.
 */
class Exporter(
    private val context: Context,
    private val storage: ProjectStorage,
) {

    fun export(document: ArtDocument, request: ExportRequest): ExportResult {
        return try {
            val composite = document.composite(includeBackground = request.includeBackground)
            val scaled = if (request.scale != 1f) {
                val w = (document.width * request.scale).toInt().coerceAtLeast(1)
                val h = (document.height * request.scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(composite, w, h, true).also { if (it !== composite) composite.recycle() }
            } else {
                composite
            }
            val file = targetFile(document.name, request.format)
            val compressFormat: Bitmap.CompressFormat = when (request.format) {
                ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                // WEBP_LOSSY only exists from API 30; the legacy constant is the fallback.
                ExportFormat.WEBP ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
            }
            val quality = when (request.format) {
                ExportFormat.PNG -> 100
                ExportFormat.JPEG, ExportFormat.WEBP -> request.quality.coerceIn(1, 100)
            }
            FileOutputStream(file).use { out ->
                scaled.compress(compressFormat, quality, out)
                out.fd.sync()
            }
            scaled.recycle()
            ExportResult.Success(file, uriFor(file), file.length())
        } catch (e: IOException) {
            ExportResult.Failure("Could not write the export: ${e.message}")
        } catch (e: OutOfMemoryError) {
            ExportResult.Failure("Not enough memory to export at this size")
        }
    }

    fun shareIntent(file: File, format: ExportFormat): Intent {
        val uri = uriFor(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, context.getString(studio.vela.art.R.string.export_chooser_title))
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun targetFile(projectName: String, format: ExportFormat): File {
        val dir = storage.exportsDir()
        val safeName = projectName
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .trim()
            .ifBlank { "artwork" }
            .replace(' ', '-')
            .lowercase()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        return File(dir, "$safeName-$stamp.${format.extension}")
    }
}
