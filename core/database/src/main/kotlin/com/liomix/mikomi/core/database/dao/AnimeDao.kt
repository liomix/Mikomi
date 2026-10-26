package com.liomix.mikomi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.database.entity.EpisodeEntity
import com.liomix.mikomi.core.database.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    @Query("SELECT * FROM anime")
    fun observeAnime(): Flow<List<AnimeEntity>>

    @Query("SELECT * FROM anime WHERE id = :id")
    fun observeAnime(id: String): Flow<AnimeEntity?>

    @Query("SELECT * FROM anime WHERE rating IS NOT NULL ORDER BY rating DESC LIMIT :limit")
    fun observeTopRated(limit: Int): Flow<List<AnimeEntity>>

    @Query("SELECT * FROM anime ORDER BY popularity DESC")
    fun observeAnimeByPopularity(): Flow<List<AnimeEntity>>

    /** 删除指定 id 前缀的数据（供首页数据源刷新前清理旧数据，避免多源混杂）。 */
    @Query("DELETE FROM anime WHERE id LIKE :prefix1 || '%' OR id LIKE :prefix2 || '%'")
    suspend fun deleteByPrefixes(prefix1: String, prefix2: String)

    @Upsert
    suspend fun upsertAll(anime: List<AnimeEntity>)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episode WHERE animeId = :animeId ORDER BY number")
    fun observeEpisodes(animeId: String): Flow<List<EpisodeEntity>>

    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule ORDER BY airingAtMillis")
    fun observeSchedule(): Flow<List<ScheduleEntity>>

    @Upsert
    suspend fun upsertAll(entries: List<ScheduleEntity>)

    /** 删除放送时间在窗口外的过期数据（时间表只保留当前周）。 */
    @Query("DELETE FROM schedule WHERE airingAtMillis < :windowStartMs OR airingAtMillis >= :windowEndMs")
    suspend fun deleteOutsideWindow(windowStartMs: Long, windowEndMs: Long)
}
