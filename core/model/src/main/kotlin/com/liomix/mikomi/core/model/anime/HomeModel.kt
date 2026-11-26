package com.liomix.mikomi.core.model.anime

import com.liomix.mikomi.core.model.Anime

/**
 * 首页的两个概念：**轮播项**与**内容区**。
 *
 * 都不复用裸 [Anime]，因为各自带着「为什么在这儿」的信息：
 * 轮播项有热度（[HomeBanner.heat]，决定它排第几），内容区有分区标识与标题。
 * 把这些塞进 [Anime]，同一个模型在不同页面就会有不同的含义。
 */

/**
 * 轮播项。
 *
 * [heat] 是趋势榜的计数：它解释「这张图为什么在第一位」，排序完就没用，
 * 所以不进 [Anime]。
 */
data class HomeBanner(
    val anime: Anime,
    /** 趋势热度；0 表示数据源没给。 */
    val heat: Int = 0,
)

/**
 * 内容区的一个分区：标题 + 一排条目。
 *
 * [id] 同时作为列表 key：分区标题可能重复（两个「热门推荐」），但 id 不会。
 */
data class HomeSection(
    val id: String,
    val title: String,
    val items: List<Anime> = emptyList(),
)
