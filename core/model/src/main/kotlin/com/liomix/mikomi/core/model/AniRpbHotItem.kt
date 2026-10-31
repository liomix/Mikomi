package com.liomix.mikomi.core.model

/**
 * AniRpb 热播榜条目。
 *
 * 这是独立数据源的专用模型：AniRpb 的视频 id 与 Bangumi / AniList 的条目 id
 * 不在同一命名空间，因此不塞进通用 [Anime]，避免详情页误把两套 id 混用。
 */
data class AniRpbHotItem(
    /** AniRpb 视频 id（后续可用于 `video.detail` / 播放链路）。 */
    val id: String,
    /** 榜单名次，从 1 开始。 */
    val rank: Int,
    val title: String,
    val coverUrl: String?,
    /** 服务端现成更新文案，例如 `7#周五16:00更`。 */
    val updateText: String,
    /** 当前已更新集数。 */
    val currentEpisodes: Int,
    /** 评分（0–10，未知为 null）。 */
    val score: Double?,
    /** 播放量（未知为 0）。 */
    val playCount: Long,
)

