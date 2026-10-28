package com.liomix.mikomi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.database.entity.EpisodeEntity
import com.liomix.mikomi.core.database.entity.RankEntity
import com.liomix.mikomi.core.database.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    @Query("SELECT * FROM anime")
    fun observeAnime(): Flow<List<AnimeEntity>>

    @Query("SELECT * FROM anime WHERE id = :id")
    fun observeAnime(id: String): Flow<AnimeEntity?>

    /** 按 id 取当前行（详情回写前用于保留首页归属与位次）。 */
    @Query("SELECT * FROM anime WHERE id = :id")
    suspend fun findById(id: String): AnimeEntity?

    /**
     * 首页轮播图候选：评分降序，`id` 作为稳定次序。
     *
     * 排序必须带确定性次序（不能只按 rating）：刷新是「整页删除 + 重新插入」，
     * 行序（rowid）每次都会变，同分项若靠 rowid 兜底，已经显示过的列表会在
     * 数据回写后无端漂移（表现为「封面都出来了又重排一次」）。
     */
    @Query(
        "SELECT * FROM anime WHERE rating IS NOT NULL AND source = 'home' " +
            "ORDER BY rating DESC, id ASC LIMIT :limit",
    )
    fun observeTopRated(limit: Int): Flow<List<AnimeEntity>>

    /**
     * 首页推荐列表：按写入时的固定位次 [AnimeEntity.homeRank] 排序。
     *
     * 不能用「热度降序」：分页的新一页会按热度插进已显示内容中间，已显示的卡片整体位移，
     * 用户点下去时卡片数据已经换成另一部番。位次固定后新页只会追加到末尾。
     */
    @Query("SELECT * FROM anime WHERE source = 'home' ORDER BY homeRank ASC, id ASC")
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
    @Query(
        "DELETE FROM schedule WHERE airingAtMillis < :windowStartMs " +
            "OR airingAtMillis >= :windowEndMs",
    )
    suspend fun deleteOutsideWindow(windowStartMs: Long, windowEndMs: Long)
}

@Dao
interface RankDao {
    /** 指定产地的榜单（评分降序；无评分条目排最后）。 */
    @Query(
        "SELECT * FROM rank_entry WHERE region = :region ORDER BY rating DESC",
    )
    fun observeByRegion(region: String): Flow<List<RankEntity>>

    /** 删除指定产地的榜单：刷新时按产地整体替换，避免一榜失败把另一榜也清掉。 */
    @Query("DELETE FROM rank_entry WHERE region = :region")
    suspend fun deleteByRegion(region: String)

    @Upsert
    suspend fun upsertAll(entries: List<RankEntity>)
}
