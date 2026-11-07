package com.liomix.mikomi.core.model

/**
 * 收藏 / 追番。
 *
 * 除 id 外还带着**收藏那一刻**的展示快照（标题、封面）。收藏页必须能离线、且不依赖
 * 别的表就画出来：番剧表会被首页刷新按来源清理，详情页也不保证可达，只存 id 的话
 * 取消收藏之外的一切（网格、标题、空态判断）都要临时再查一次网络，那不是一张本地列表。
 *
 * 快照只在收藏发生、以及打开详情页发现内容变了时更新；「撤销取消收藏」原样写回，
 * 位次（[addedAt]）不变，所以撤销之后卡片回到原来的位置。
 */
data class Favorite(
    val animeId: String,
    val title: String,
    val coverUrl: String? = null,
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
