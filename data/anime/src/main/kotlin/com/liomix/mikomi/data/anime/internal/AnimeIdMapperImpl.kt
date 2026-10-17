package com.liomix.mikomi.data.anime.internal

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.data.anime.AnimeIdMapper
import com.liomix.mikomi.data.anime.remote.anilist.AniListApi
import com.liomix.mikomi.data.anime.remote.anilist.AniListQueries
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlRequest
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiApi
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchRequest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class AnimeIdMapperImpl @Inject constructor(
    private val bangumiApi: BangumiApi,
    private val anilistApi: AniListApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AnimeIdMapper {

    override suspend fun bangumiToAniList(bangumiId: Int): Int? = withContext(ioDispatcher) {
        val subject = bangumiApi.getSubject(bangumiId)
        // Bangumi 原名多为日文，与 AniList 的 native 字段对齐。
        searchAniList(subject.name)
    }

    override suspend fun aniListToBangumi(aniListId: Int): Int? = withContext(ioDispatcher) {
        val media = anilistApi.execute(
            AniListGraphqlRequest(
                query = AniListQueries.MEDIA_BY_ID,
                variables = buildJsonObject { put("id", aniListId) },
            ),
        ).data?.media ?: return@withContext null

        val title = media.title?.nativeTitle
            ?: media.title?.romaji
            ?: media.title?.english
            ?: return@withContext null
        searchBangumi(title)
    }

    private suspend fun searchAniList(keyword: String): Int? {
        val media = anilistApi.execute(
            AniListGraphqlRequest(
                query = AniListQueries.SEARCH,
                variables = buildJsonObject { put("search", keyword) },
            ),
        ).data?.page?.media.orEmpty()

        return media.firstOrNull {
            matches(it.title?.nativeTitle, keyword) ||
                matches(it.title?.romaji, keyword) ||
                matches(it.title?.english, keyword)
        }?.id
    }

    private suspend fun searchBangumi(keyword: String): Int? {
        val result = bangumiApi.search(
            request = BangumiSearchRequest(keyword = keyword),
            limit = SEARCH_LIMIT,
            offset = 0,
        )
        return result.data.firstOrNull {
            matches(it.name, keyword) ||
                matches(it.nameCn, keyword) ||
                matches(it.nameCnNext, keyword)
        }?.id
    }

    /** 忽略大小写与空白的名字匹配。 */
    private fun matches(candidate: String?, target: String): Boolean {
        if (candidate.isNullOrBlank()) return false
        return candidate.equals(target, ignoreCase = true) ||
            normalize(candidate) == normalize(target)
    }

    private fun normalize(value: String): String = value.lowercase().filterNot { it.isWhitespace() }

    private companion object {
        const val SEARCH_LIMIT = 10
    }
}
