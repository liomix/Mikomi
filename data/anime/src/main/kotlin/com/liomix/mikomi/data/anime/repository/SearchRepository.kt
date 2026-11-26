package com.liomix.mikomi.data.anime.repository

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.Search

/**
 * 搜索：关键词搜索 + 输入联想。
 *
 * 这一层只做「要什么」的判断，不碰 Bangumi 的任何形状 —— 数据源（`BangumiSource`）
 * 已经把它整理成领域模型，这里拿到的直接就是 [Anime]。
 *
 * 结果不落库：搜索是时效性的，用户可能搜一次就点开某条，把每次输入都写进缓存的代价
 * 远大于收益。真正需要留存的是用户点进去的那一条，那由详情页自己负责。
 */
interface SearchRepository {

    /**
     * 搜索一页。
     *
     * 排序随词长变：短词（≤2 字）按热度 —— 用户多半在找一个确切的名字，热度高的更可能
     * 是他要的；长词按相关度 —— 长词已经足够具体，相关性排序才不会把冷门结果排到后面。
     */
    suspend fun search(
        keyword: String,
        limit: Int = SEARCH_PAGE_SIZE,
        offset: Int = 0,
    ): Search

    /** 输入联想：只取少量、按相关度。 */
    suspend fun suggest(
        keyword: String,
        limit: Int = SUGGEST_LIMIT,
    ): List<Anime>

    companion object {
        /** 一页 24 条：3 列网格正好 8 行。 */
        const val SEARCH_PAGE_SIZE = 24

        /** 联想最多 10 条：再多就不是「联想」而是「结果」了。 */
        const val SUGGEST_LIMIT = 10
    }
}
