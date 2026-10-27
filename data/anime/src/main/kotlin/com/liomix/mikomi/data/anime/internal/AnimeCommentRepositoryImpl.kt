package com.liomix.mikomi.data.anime.internal

import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.data.anime.AnimeCommentRepository
import com.liomix.mikomi.data.anime.AnimeIdMapper
import com.liomix.mikomi.data.anime.TeasingPage
import com.liomix.mikomi.data.anime.mapper.ANILIST_ID_PREFIX
import com.liomix.mikomi.data.anime.mapper.BANGUMI_ID_PREFIX
import com.liomix.mikomi.data.anime.mapper.toDomain
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiTrendApi
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class AnimeCommentRepositoryImpl @Inject constructor(
    private val bangumiTrendApi: BangumiTrendApi,
    private val animeIdMapper: AnimeIdMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AnimeCommentRepository {

    override suspend fun fetchTeasingComments(
        animeId: String,
        limit: Int,
        offset: Int,
    ): TeasingPage = withContext(ioDispatcher) {
        val bangumiId = resolveBangumiId(animeId)
            ?: return@withContext TeasingPage(comments = emptyList(), hasMore = false)
        val items = bangumiTrendApi.fetchSubjectComments(bangumiId, limit, offset).data
        TeasingPage(
            comments = items.map { it.toDomain() },
            hasMore = items.size >= limit,
        )
    }

    /** 解析领域 id → Bangumi 数字 id；AniList 前缀先做 id 映射。 */
    private suspend fun resolveBangumiId(animeId: String): Int? {
        val bangumiRaw = animeId.removePrefix(BANGUMI_ID_PREFIX)
        if (bangumiRaw != animeId) {
            // 原 id 已是 bangumi: 前缀，直接解析数字。
            return bangumiRaw.toIntOrNull()
        }
        val anilistRaw = animeId.removePrefix(ANILIST_ID_PREFIX)
        if (anilistRaw != animeId) {
            // anilist: 前缀，映射到 Bangumi。
            val anilistId = anilistRaw.toIntOrNull() ?: return null
            return try {
                animeIdMapper.aniListToBangumi(anilistId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "AniList→Bangumi id mapping failed", e)
                null
            }
        }
        return null
    }

    private companion object {
        const val TAG = "AnimeCommentRepository"
    }
}
