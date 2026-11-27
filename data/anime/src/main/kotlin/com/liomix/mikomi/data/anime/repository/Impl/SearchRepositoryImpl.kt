package com.liomix.mikomi.data.anime.repository.Impl

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.Search
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiSource
import com.liomix.mikomi.data.anime.repository.SearchRepository
import javax.inject.Inject

/**
 * 搜索实现：**只决定「怎么搜」，不管「搜到的是什么形状」**。
 *
 * [BangumiSource] 已经交出领域模型，所以这里看不到 DTO、看不到 mapper、也不做过滤 ——
 * 整个类只剩一条真正的规则：按词长选排序。
 *
 * 这就是把映射下沉到数据源换来的东西：Repository 里没有一行「对象转换」的噪音。
 */
internal class SearchRepositoryImpl @Inject constructor(
    private val source: BangumiSource,
) : SearchRepository {

    override suspend fun search(
        keyword: String,
        limit: Int,
        offset: Int,
    ): Search {
        val trimmed = keyword.trim()
        // 空词直接短路，不发请求：数据源那边虽然也拦了一道，但这一层先返回空结果，
        // 连挂起都不用。
        if (trimmed.isEmpty()) return Search()

        return source.search(
            keyword = trimmed,
            sort = if (trimmed.length <= SHORT_KEYWORD_LENGTH) SORT_HEAT else SORT_MATCH,
            limit = limit,
            offset = offset,
        )
    }

    override suspend fun suggest(
        keyword: String,
        limit: Int,
    ): List<Anime> = source.suggest(keyword = keyword, limit = limit)

    private companion object {
        /** 两字以内算短词。中文一个字往往就能定位，两个字已能表达名字的大半。 */
        const val SHORT_KEYWORD_LENGTH = 2

        /** 热度：短词时用户多半在找「那个有名的」。 */
        const val SORT_HEAT = "heat"

        /** 相关度：长词时用户已经很明确，按匹配程度排才不会把冷门挤到后面。 */
        const val SORT_MATCH = "match"
    }
}
