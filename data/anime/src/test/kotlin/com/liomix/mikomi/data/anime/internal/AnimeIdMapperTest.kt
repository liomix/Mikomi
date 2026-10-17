package com.liomix.mikomi.data.anime.internal

import com.liomix.mikomi.data.anime.remote.anilist.AniListApi
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListData
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlRequest
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlResponse
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListMediaDto
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListPage
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListTitleDto
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiApi
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchRequest
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchResponse
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSubjectDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeIdMapperTest {

    @Test
    fun `bangumiToAniList resolves via name search`() = runTest {
        val bangumiApi = FakeBangumiApi(
            subject = BangumiSubjectDto(id = 10000, name = "NARUTO -ナルト-"),
        )
        val anilistApi = FakeAniListApi(
            searchResult = listOf(
                AniListMediaDto(id = 600, title = AniListTitleDto(nativeTitle = "NARUTO -ナルト-")),
            ),
        )
        val mapper = AnimeIdMapperImpl(bangumiApi, anilistApi, Dispatchers.Unconfined)

        assertEquals(600, mapper.bangumiToAniList(10000))
    }

    @Test
    fun `aniListToBangumi resolves via title search`() = runTest {
        val bangumiApi = FakeBangumiApi(
            searchResult = listOf(BangumiSubjectDto(id = 10000, name = "Naruto")),
        )
        val anilistApi = FakeAniListApi(
            media = AniListMediaDto(id = 600, title = AniListTitleDto(romaji = "Naruto")),
        )
        val mapper = AnimeIdMapperImpl(bangumiApi, anilistApi, Dispatchers.Unconfined)

        assertEquals(10000, mapper.aniListToBangumi(600))
    }

    @Test
    fun `bangumiToAniList returns null when no match`() = runTest {
        val bangumiApi = FakeBangumiApi(subject = BangumiSubjectDto(id = 1, name = "Unknown"))
        val anilistApi = FakeAniListApi(searchResult = emptyList())
        val mapper = AnimeIdMapperImpl(bangumiApi, anilistApi, Dispatchers.Unconfined)

        assertNull(mapper.bangumiToAniList(1))
    }

    private class FakeBangumiApi(
        private val subject: BangumiSubjectDto? = null,
        private val searchResult: List<BangumiSubjectDto> = emptyList(),
    ) : BangumiApi {
        override suspend fun search(
            request: BangumiSearchRequest,
            limit: Int,
            offset: Int,
        ): BangumiSearchResponse = BangumiSearchResponse(data = searchResult, total = searchResult.size)

        override suspend fun getSubject(id: Int): BangumiSubjectDto = requireNotNull(subject)
    }

    private class FakeAniListApi(
        private val media: AniListMediaDto? = null,
        private val searchResult: List<AniListMediaDto> = emptyList(),
    ) : AniListApi {
        override suspend fun execute(request: AniListGraphqlRequest): AniListGraphqlResponse =
            AniListGraphqlResponse(
                data = AniListData(
                    page = if (searchResult.isNotEmpty()) AniListPage(media = searchResult) else null,
                    media = media,
                ),
            )
    }
}
