package studio.vela.art.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Project catalogue row. Pixel data lives in the `.vela` archive, not in the database. */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val width: Int,
    val height: Int,
    val dpi: Int,
    val background: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long = 0L,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "document_path") val documentPath: String,
    val favorite: Boolean = false,
    val collection: String? = null,
)
