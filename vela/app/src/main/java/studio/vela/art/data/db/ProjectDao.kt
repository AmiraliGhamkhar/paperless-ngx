package studio.vela.art.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE favorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun all(): List<ProjectEntity>

    @Upsert
    suspend fun upsert(entity: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE projects SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE projects SET updatedAt = :updatedAt, lastOpenedAt = :lastOpenedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long, lastOpenedAt: Long)

    @Query("UPDATE projects SET updatedAt = :updatedAt, thumbnailPath = :thumbnailPath WHERE id = :id")
    suspend fun updateAfterSave(id: Long, updatedAt: Long, thumbnailPath: String?)
}
