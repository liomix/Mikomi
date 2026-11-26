package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.Serializable

/**
 * 首页内容区的一个分区：一个标题 + 一排条目。
 *
 * 叫「内容区」而不是「推荐」：首页除了推荐还有热播、新番、完结，它们的形状完全一样，
 * 差异只在 [id]（用于区分分区、也用于列表 key）与 [title]。给每种分区各定义一份 DTO，
 * 结果是一堆字段完全相同的类，改一处标题样式要动四个文件。
 *
 * [items] 用基础 [BangumiDto]：分区之间的差别是「挑哪些条目」，不是「条目长什么样」。
 */
@Serializable
data class BangumiHomeContentDto(
    /** 分区标识（如 `recommend` / `airing` / `finished`），同时用作列表 key。 */
    val id: String,
    val title: String,
    val items: List<BangumiDto> = emptyList(),
)

/**
 * 首页内容响应。
 *
 * 与轮播分开取：轮播是「一张图 + 一句标题」的横滑，内容区是「分区标题 + 网格卡片」，
 * 两者刷新节奏不同（轮播换得慢），塞进一个响应会让其中一方的 TTL 被另一方拖着走。
 */
@Serializable
data class BangumiHomeContentResponseDto(
    val data: List<BangumiHomeContentDto> = emptyList(),
)
