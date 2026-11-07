package com.liomix.mikomi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.liomix.mikomi.core.database.entity.FavoriteEntity
import com.liomix.mikomi.core.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    /** 收藏列表：最近收藏在前；同一毫秒收藏的按 id 兜底排序，保证顺序稳定。 */
    @Query("SELECT * FROM favorite ORDER BY addedAt DESC, animeId ASC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    /** 按 id 取出若干行（批量取消收藏前先留一份，供「撤销」原样写回）。 */
    @Query("SELECT * FROM favorite WHERE animeId IN (:animeIds)")
    suspend fun findByIds(animeIds: List<String>): List<FavoriteEntity>

    /**
     * 收藏。已存在时**整行不动** —— 包括 `addedAt`：
     * 同一部番被重复收藏（详情页重试、并发点击）不该把它的位次顶到最前。
     */
    @Query(
        """
        INSERT OR IGNORE INTO favorite (animeId, addedAt, title, coverUrl)
        VALUES (:animeId, :addedAt, :title, :coverUrl)
        """,
    )
    suspend fun insertIfAbsent(
        animeId: String,
        addedAt: Long,
        title: String,
        coverUrl: String?,
    )

    /**
     * 更新快照。
     *
     * WHERE 里带着「和现值不同」：内容没变时这条语句不命中任何行，SQLite 的更新触发器
     * 也就不会触发，Room 不会因此发出失效通知 —— 详情页每次打开都调一次也不会自激。
     */
    @Query(
        """
        UPDATE favorite SET title = :title, coverUrl = :coverUrl
        WHERE animeId = :animeId
          AND (title <> :title OR IFNULL(coverUrl, '') <> IFNULL(:coverUrl, ''))
        """,
    )
    suspend fun refreshSnapshot(animeId: String, title: String, coverUrl: String?)

    /** 撤销：把删掉的行连同原 `addedAt` 一起写回。 */
    @Upsert
    suspend fun upsertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorite WHERE animeId = :animeId")
    suspend fun delete(animeId: String)

    /** 批量取消收藏（一条语句，不在事务外逐条删）。 */
    @Query("DELETE FROM favorite WHERE animeId IN (:animeIds)")
    suspend fun deleteByIds(animeIds: List<String>)

    /**
     * 取出并删除：批量取消收藏的原子操作。
     *
     * 读与删必须同属一个事务 —— 分成两次调用的话，中间那一瞬列表仍会带着这些行落库一次，
     * 收藏页可能闪回已删的卡片，且「读到的行」与「真正删掉的行」在极端情况下不是同一批。
     */
    @Transaction
    suspend fun takeOut(animeIds: List<String>): List<FavoriteEntity> {
        val rows = findByIds(animeIds)
        deleteByIds(animeIds)
        return rows
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY updatedAt DESC")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(history: HistoryEntity)

    @Query("DELETE FROM history WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)
}
