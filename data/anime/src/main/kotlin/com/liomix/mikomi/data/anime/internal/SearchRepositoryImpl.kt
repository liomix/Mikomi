package com.liomix.mikomi.data.anime.internal

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.SearchPage
import com.liomix.mikomi.data.anime.SearchRepository
import com.liomix.mikomi.data.anime.mapper.toDomain
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiApi
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiTrendApi
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchFilter
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchRequest
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSubjectDto
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SearchRepositoryImpl @Inject constructor(
    private val bangumiApi: BangumiApi,
    private val bangumiTrendApi: BangumiTrendApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {

    override suspend fun search(
        keyword: String,
        limit: Int,
        offset: Int,
    ): SearchPage = withContext(ioDispatcher) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return@withContext SearchPage(items = emptyList(), rawCount = 0)
        // 短词按热度（用户多半在找一个确切的名字），长词按相关度。
        val sort = if (trimmed.length <= SHORT_KEYWORD_LENGTH) SORT_HEAT else SORT_MATCH
        val response = bangumiApi.search(
            request = BangumiSearchRequest(
                keyword = trimmed,
                sort = sort,
                filter = BangumiSearchFilter(type = listOf(TYPE_ANIME)),
            ),
            limit = limit,
            offset = offset,
        )
        SearchPage(
            items = response.data.filterAnime().map { it.toDomain() },
            rawCount = response.data.size,
        )
    }

    override suspend fun suggest(
        keyword: String,
        limit: Int,
    ): List<Anime> = withContext(ioDispatcher) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        bangumiApi.search(
            request = BangumiSearchRequest(
                keyword = trimmed,
                sort = SORT_MATCH,
                filter = BangumiSearchFilter(type = listOf(TYPE_ANIME)),
            ),
            limit = limit,
            offset = 0,
        ).data
            .filterAnime()
            .map { it.toDomain() }
    }

    override suspend fun fetchTrending(limit: Int): List<Anime> = withContext(ioDispatcher) {
        bangumiTrendApi.fetchTrending(type = TYPE_ANIME, limit = limit, offset = 0)
            .data
            // 同搜索：兜底剔除非动画条目。
            .filter { it.subject.type == TYPE_ANIME }
            .map { it.toDomain() }
    }

    /**
     * 只保留动画条目。
     *
     * 服务端 filter 已限定 type=2，这里再做一次客户端过滤兜底：
     * 搜索接口在部分排序策略下会带回书籍 / 游戏 / 三次元等条目。
     * `type` 缺省（0）同样视为非动画剔除。
     */
    private fun List<BangumiSubjectDto>.filterAnime(): List<BangumiSubjectDto> =
        filter { it.type == TYPE_ANIME }

    private companion object {
        const val SORT_MATCH = "match"
        const val SORT_HEAT = "heat"

        /** 短关键词阈值（含）：≤2 字时热度排序更准。 */
        const val SHORT_KEYWORD_LENGTH = 2

        /** 条目类型：2 = 动画。 */
        const val TYPE_ANIME = 2
    }
}
