package com.liomix.mikomi.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "anime")
data class AnimeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val coverUrl: String? = null,
    val synopsis: String? = null,
    val rating: Double? = null,
    val status: String = "unknown",
    @ColumnInfo(defaultValue = "0") val popularity: Int = 0,
    /** 上映日期（yyyy-MM-dd）。 */
    @ColumnInfo(defaultValue = "") val airDate: String = "",
    /** 站内排行名次（0 表示无排名）。 */
    @ColumnInfo(defaultValue = "0") val rank: Int = 0,
    /** 评分人数（0 表示未知）。 */
    @ColumnInfo(defaultValue = "0") val ratingCount: Int = 0,
    /** 展示用类型标签，多值以 `·` 连接存储。 */
    @ColumnInfo(defaultValue = "") val genres: String = "",
    /**
     * 数据来源标记：`home` = 首页 trending 写入、`detail` = 详情页回写。
     *
     * 首页轮播图 / 推荐列表按「评分 / 热度」在全表取前 N，若不区分来源，
     * 详情页看过的番剧会带着评分混进首页榜单（表现为「搜完回来轮播出现该番」）。
     * 通过本列让首页查询只命中 trending 数据。
     */
    @ColumnInfo(defaultValue = "home") val source: String = "home",
    /**
     * 首页推荐列表的固定位次（写入时按接口返回顺序赋值：第 N 条 = N）。
     *
     * 列表按本列排序而不是按热度：按热度排序时 loadMore 的新一页会「插队」到已显示内容中间，
     * 用户点下去的一瞬间卡片数据已被换成另一部番（表现为「明明点的 A 却打开 B」）。
     * 位次固定后新数据只会追加到末尾，已显示的卡片不会位移。
     */
    @ColumnInfo(defaultValue = "0") val homeRank: Int = 0,
)

@Entity(tableName = "episode")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val animeId: String,
    val number: Int,
    val title: String? = null,
    val durationSec: Long = 0,
)

@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val animeId: String,
    val addedAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/**
 * 时间表：AniList 每周放送计划。非规范化存储展示所需字段，
 * 不与 anime 表关联（anime 表会被首页刷新按前缀清理）。
 */
@Entity(tableName = "schedule")
data class ScheduleEntity(
    /** 主键：anilist id + 集数，同一番剧每周多条放送记录共存。 */
    @PrimaryKey val id: String,
    val animeId: String,
    val title: String,
    val coverUrl: String? = null,
    val synopsis: String? = null,
    val rating: Double? = null,
    val airingAtMillis: Long,
    val episode: Int,
)

/**
 * 综合评分榜：国漫 / 日漫两榜。非规范化存储展示所需字段，
 * 不与 anime 表关联（anime 表会被首页刷新按前缀清理）。
 */
@Entity(tableName = "rank_entry")
data class RankEntity(
    /** 主键：anilist id（带前缀），与 anime 表 id 空间一致。 */
    @PrimaryKey val id: String,
    /** 产地：cn（国漫）/ jp（日漫），见 data 层 RankRepository 常量。 */
    val region: String,
    val title: String,
    val coverUrl: String? = null,
    val synopsis: String? = null,
    val rating: Double? = null,
    val status: String = "unknown",
    val popularity: Int = 0,
    /** 展示用类型标签，多值以 `·` 连接存储。 */
    val genres: String = "",
)
