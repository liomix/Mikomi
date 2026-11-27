package com.liomix.mikomi.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.liomix.mikomi.core.database.entity.AnimeIdentityEntity
import com.liomix.mikomi.core.database.entity.ScheduleConflictEntity
import com.liomix.mikomi.core.database.entity.ScheduleSourceEntity
import com.liomix.mikomi.core.database.entity.ScheduleSourceEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 时间表的**状态**表：分源版本、分源缓存行、合并冲突队列。
 *
 * 合并结果本身不进这里（它在缓存里，可以整块丢掉重建）；这三张表存的是「需要被查询、
 * 被逐条标记」的东西 —— 源这次抓到了几条、下一次刷新要不要重新解析、哪些冲突待复核。
 */

@Dao
interface ScheduleSourceDao {
    @Query("SELECT * FROM schedule_source ORDER BY priority ASC, sourceId ASC")
    fun observeSources(): Flow<List<ScheduleSourceEntity>>

    @Query("SELECT * FROM schedule_source ORDER BY priority ASC, sourceId ASC")
    suspend fun sources(): List<ScheduleSourceEntity>

    @Query("SELECT * FROM schedule_source WHERE sourceId = :sourceId")
    suspend fun find(sourceId: String): ScheduleSourceEntity?

    @Upsert
    suspend fun upsertAll(sources: List<ScheduleSourceEntity>)

    @Query("SELECT * FROM schedule_source_entry WHERE sourceId = :sourceId")
    suspend fun entriesOf(sourceId: String): List<ScheduleSourceEntryEntity>

    @Upsert
    suspend fun upsertEntries(entries: List<ScheduleSourceEntryEntity>)

    @Query("DELETE FROM schedule_source_entry WHERE sourceId = :sourceId")
    suspend fun deleteEntriesOf(sourceId: String)

    /** 丢弃版本号落后于源当前版本的缓存（源成功刷新后清理历史行）。 */
    @Query("DELETE FROM schedule_source_entry WHERE sourceId = :sourceId AND sourceVersion != :version")
    suspend fun deleteStaleEntries(sourceId: String, version: Int)
}

@Dao
interface AnimeIdentityDao {
    @Query("SELECT * FROM anime_identity")
    suspend fun all(): List<AnimeIdentityEntity>

    @Query("SELECT * FROM anime_identity WHERE canonicalKey = :canonicalKey")
    suspend fun find(canonicalKey: String): AnimeIdentityEntity?

    @Upsert
    suspend fun upsertAll(identities: List<AnimeIdentityEntity>)
}

@Dao
interface ScheduleConflictDao {
    @Upsert
    suspend fun upsertAll(conflicts: List<ScheduleConflictEntity>)

    @Query("SELECT * FROM schedule_conflict ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ScheduleConflictEntity>

    /** 冲突总数（UI 不展示，供监控与运营复核面板观察趋势）。 */
    @Query("SELECT COUNT(*) FROM schedule_conflict")
    fun observeCount(): Flow<Int>

    /** 只保留最近 [keep] 条，避免复核队列无限增长。 */
    @Query(
        "DELETE FROM schedule_conflict WHERE id NOT IN " +
            "(SELECT id FROM schedule_conflict ORDER BY createdAt DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)
}
