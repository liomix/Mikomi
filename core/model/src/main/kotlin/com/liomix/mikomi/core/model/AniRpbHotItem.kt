package com.liomix.mikomi.core.model

import kotlinx.serialization.Serializable

/**
 * AniRpb 热播榜条目。
 *
 * 这是独立数据源的专用模型：AniRpb 的视频 id 与 Bangumi / AniList 的条目 id
 * 不在同一命名空间，因此不塞进通用 [Anime]，避免详情页误把两套 id 混用。
 */
@Serializable
data class AniRpbHotItem(
    /** AniRpb 视频 id（后续可用于 `video.detail` / 播放链路）。 */
    val id: String,
    /**
     * 榜单名次，从 1 开始。
     *
     * **不来自报文**：名次是「这一条在列表里的位置」，由数据层在组装完列表后统一赋值
     * （见 `AniRpbRepositoryImpl` 的解析函数）。解析阶段先留 0。
     */
    val rank: Int = 0,
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
    /**
     * 动漫标签（年份 / 类型 / 关键词，如「2026」「动漫」「萧炎」）。
     *
     * 报文没带这个字段时是空列表，界面上整行不出现 —— 不用占位文本假装有数据。
     */
    val tags: List<String> = emptyList(),
)

