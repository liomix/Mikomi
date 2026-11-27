package com.liomix.mikomi.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val animeId: String,
    /** 收藏时间，同时是收藏页的排序依据（新的在前）。 */
    val addedAt: Long,
    @ColumnInfo(defaultValue = "") val title: String = "",
    val coverUrl: String? = null,
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
 * 时间表：多源合并后的结果行。非规范化存储展示所需字段，
 * 不与 anime 表关联（anime 表会被首页刷新按前缀清理）。
 *
 * 每行都记录了 canonical 身份键与最终采用的源，便于跨源判重后的追溯。
 */
