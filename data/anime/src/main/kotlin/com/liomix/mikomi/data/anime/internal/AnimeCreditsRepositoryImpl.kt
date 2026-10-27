package com.liomix.mikomi.data.anime.internal

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.model.CreditComment
import com.liomix.mikomi.core.model.CreditDetail
import com.liomix.mikomi.data.anime.AnimeCredits
import com.liomix.mikomi.data.anime.AnimeCreditsRepository
import com.liomix.mikomi.data.anime.mapper.BANGUMI_ID_PREFIX
import com.liomix.mikomi.data.anime.mapper.ANILIST_ID_PREFIX
import com.liomix.mikomi.data.anime.mapper.sortedByPosition
import com.liomix.mikomi.data.anime.mapper.sortedByRelation
import com.liomix.mikomi.data.anime.mapper.toCreditDetail
import com.liomix.mikomi.data.anime.mapper.toDomain
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiApi
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiTrendApi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal class AnimeCreditsRepositoryImpl @Inject constructor(
    private val bangumiApi: BangumiApi,
    private val bangumiTrendApi: BangumiTrendApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AnimeCreditsRepository {

    override suspend fun fetchCredits(animeId: String): AnimeCredits = withContext(ioDispatcher) {
        // 解析 Bangumi 数字 id；非 bangumi 前缀（如 anilist）无法直接查角色/人员。
        val bangumiId = animeId.removePrefix(BANGUMI_ID_PREFIX).toIntOrNull()
            ?: animeId.removePrefix(ANILIST_ID_PREFIX).toIntOrNull()
            ?: return@withContext AnimeCredits(emptyList(), emptyList())

        coroutineScope {
            val charactersDeferred = async { fetchCharacters(bangumiId) }
            val staffDeferred = async { fetchStaff(bangumiId) }
            AnimeCredits(
                characters = charactersDeferred.await(),
                staff = staffDeferred.await(),
            )
        }
    }

    override suspend fun fetchCharacterDetail(characterId: Int): CreditDetail =
        withContext(ioDispatcher) {
            bangumiTrendApi.fetchCharacterDetail(characterId).toCreditDetail()
        }

    override suspend fun fetchPersonDetail(personId: Int): CreditDetail =
        withContext(ioDispatcher) {
            bangumiTrendApi.fetchPersonDetail(personId).toCreditDetail()
        }

    override suspend fun fetchCharacterComments(characterId: Int): List<CreditComment> =
        withContext(ioDispatcher) {
            bangumiTrendApi.fetchCharacterComments(characterId).map { it.toDomain() }
        }

    override suspend fun fetchPersonComments(personId: Int): List<CreditComment> =
        withContext(ioDispatcher) {
            bangumiTrendApi.fetchPersonComments(personId).map { it.toDomain() }
        }

    private suspend fun fetchCharacters(bangumiId: Int) =
        bangumiApi.fetchSubjectCharacters(bangumiId)
            .map { it.toDomain() }
            .sortedByRelation()

    private suspend fun fetchStaff(bangumiId: Int) =
        bangumiTrendApi.fetchSubjectStaff(bangumiId)
            .data
            .map { it.toDomain() }
            .sortedByPosition()
}
