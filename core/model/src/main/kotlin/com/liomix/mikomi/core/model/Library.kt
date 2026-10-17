package com.liomix.mikomi.core.model

/** 收藏 / 追番。 */
data class Favorite(
    val animeId: String,
    val addedAt: Long,
)

/** 观看历史 / 断点续播。 */
data class WatchHistory(
    val episodeId: String,
    val animeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
