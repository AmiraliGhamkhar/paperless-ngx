package studio.vela.art.data.storage

import android.content.Context
import java.io.File

/**
 * On-disk layout. Everything lives in app-private storage so the app works fully offline and
 * needs no storage permission.
 *
 * ```
 * files/projects/<id>/document.vela      current project archive
 * files/projects/<id>/document.vela.bak  previous good archive
 * files/projects/<id>/document.vela.tmp  in-progress write (recovery candidate)
 * files/projects/<id>/thumb.png          gallery thumbnail
 * files/exports/<name>.<ext>             exported images
 * ```
 */
class ProjectStorage(context: Context) {

    private val root: File = File(context.filesDir, "projects")
    private val exports: File = File(context.filesDir, "exports")

    fun projectsRoot(): File = root.also { if (!it.exists()) it.mkdirs() }

    fun exportsDir(): File = exports.also { if (!it.exists()) it.mkdirs() }

    fun projectDir(projectId: Long): File =
        File(projectsRoot(), projectId.toString()).also { if (!it.exists()) it.mkdirs() }

    fun documentFile(projectId: Long): File = File(projectDir(projectId), "document.vela")

    fun backupFile(projectId: Long): File = File(projectDir(projectId), "document.vela.bak")

    fun temporaryFile(projectId: Long): File = File(projectDir(projectId), "document.vela.tmp")

    fun thumbnailFile(projectId: Long): File = File(projectDir(projectId), "thumb.png")

    fun deleteProject(projectId: Long) {
        File(projectsRoot(), projectId.toString()).deleteRecursively()
    }

    fun storageUsageBytes(): Long {
        var total = 0L
        fun walk(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) walk(child) else total += child.length()
            }
        }
        walk(root)
        walk(exports)
        return total
    }

    /** Removes stale temporary archives older than [olderThanMillis]. */
    fun cleanupTemporaryFiles(olderThanMillis: Long = 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        projectsRoot().listFiles()?.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp") && file.lastModified() < cutoff) file.delete()
            }
        }
    }
}
