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
