package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.Anime

/** 搜索结果分页。 */
data class SearchPage(
    /** 已过滤为动画条目的结果。 */
    val items: List<Anime>,
    /** 服务端本页返回的原始条数（含被过滤掉的非动画条目），用于推进分页游标。 */
    val rawCount: Int,
)

/**
 * 搜索数据门面：关键词搜索 + 输入联想 + 热门推荐。
 *
 * 结果只保留动画条目（Bangumi type = 2），书籍 / 游戏 / 三次元等一律剔除；
 * 不落库（时效性强、用户不一定点开），直接以领域模型返回给 UI。
 */
interface SearchRepository {

    /**
     * 关键词搜索。
     *
     * 短词（≤2 字）按热度排序更贴合「搜一个名字」的意图，长词按相关度排序。
     */
    suspend fun search(
        keyword: String,
        limit: Int = SEARCH_PAGE_SIZE,
        offset: Int = 0,
    ): SearchPage

    /** 输入联想：按相关度取少量结果。 */
    suspend fun suggest(
        keyword: String,
        limit: Int = SUGGEST_LIMIT,
    ): List<Anime>

    /** 热门推荐（Bangumi 趋势榜），用于搜索页空态引导。 */
    suspend fun fetchTrending(limit: Int = TRENDING_LIMIT): List<Anime>

    companion object {
        const val SEARCH_PAGE_SIZE = 24
        const val SUGGEST_LIMIT = 10
        const val TRENDING_LIMIT = 10
    }
}
