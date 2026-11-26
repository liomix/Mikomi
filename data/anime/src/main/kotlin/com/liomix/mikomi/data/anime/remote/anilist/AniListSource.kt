package com.liomix.mikomi.data.anime.remote.anilist

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.remote.anilist.client.AniListApi
import com.liomix.mikomi.data.anime.remote.anilist.client.AniListQueries
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * AniList 数据源的**唯一出口**，与 `BangumiSource` 同构：对外只给领域模型。
 *
 * ## 为什么需要这一层
 *
 * AniList 是 GraphQL：每个请求都要自己拼 `query` + `variables`、再从 `data.Page` 里
 * 取出那一层。没有这一层的话，每个调用点都要写一遍 `buildJsonObject { put("page", ...) }`，
 * 一处漏了 `type: ANIME` 就会混进漫画。
 *
 * ## 与 Bangumi 的关系
 *
 * 两个源**不混用**：它们的条目 id 不在同一命名空间，混着展示会让详情页在两条路径间跳。
 * 首页只走 Bangumi，AniList 现在只剩 id 反查（`AniListQueries.SEARCH`）在用。
 *
 * 不 try/catch：GraphQL 的错误在响应体里（`errors` 字段），网络错误是异常 —— 两者都该
 * 由调用方决定怎么处理。
 */
@Singleton
internal class AniListSource @Inject constructor(
    private val api: AniListApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * 趋势榜一页。
     *
     * `hasNextPage` 直接来自 `pageInfo`：GraphQL 的分页没有总数，只能靠它判断还有没有下一页。
     */
    suspend fun trending(page: Int = 1, perPage: Int = PAGE_SIZE): AniListPage =
        withContext(ioDispatcher) {
            val pageData = api.execute(
                AniListGraphqlRequest(
                    query = AniListQueries.TRENDING,
                    variables = buildJsonObject {
                        put("page", page)
                        put("perPage", perPage)
                    },
                ),
            ).data?.page

            AniListPage(
                items = pageData?.media.orEmpty().map { it.toDomain() },
                hasNextPage = pageData?.pageInfo?.hasNextPage ?: false,
            )
        }

    /**
     * 按标题搜索，用于「AniList id → Bangumi id」这条反查。
     *
     * 固定取前 10 条：这个查询的用途是找「最像的那一个」，而不是给用户翻页。
     */
    suspend fun searchByTitle(title: String): List<Anime> = withContext(ioDispatcher) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        api.execute(
            AniListGraphqlRequest(
                query = AniListQueries.SEARCH,
                variables = buildJsonObject { put("search", trimmed) },
            ),
        ).data?.page?.media.orEmpty().map { it.toDomain() }
    }

    /** 一页结果：[hasNextPage] 是 GraphQL 唯一能给的「还有没有」信号。 */
    data class AniListPage(
        val items: List<Anime> = emptyList(),
        val hasNextPage: Boolean = false,
    )

    private companion object {
        /** 一页 20 条：与首页网格的 3 列 × 7 行大致对齐。 */
        const val PAGE_SIZE = 20
    }
}
