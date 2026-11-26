package com.liomix.mikomi.data.anime.remote.bangumi.client

import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorCharacterDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorCommentDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorPersonDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchResponseDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorResponseDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiAnimeCommentResponseDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiDetailDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiHomeBannerResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Bangumi Next API（next.bgm.tv）：趋势 / 日历 / 制作人员 / 主创详情。 */
internal interface BangumiTrendApi {

    @GET("p1/trending/subjects")
    suspend fun fetchTrending(
        @Query("type") type: Int,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): BangumiHomeBannerResponseDto

    @GET("p1/subjects/{id}/staffs/persons")
    suspend fun fetchSubjectStaff(@Path("id") subjectId: Int): BangumiCreatorResponseDto

    @GET("p1/characters/{id}")
    suspend fun fetchCharacterDetail(@Path("id") characterId: Int): BangumiCreatorCharacterDto

    @GET("p1/persons/{id}")
    suspend fun fetchPersonDetail(@Path("id") personId: Int): BangumiCreatorPersonDto

    @GET("p1/characters/{id}/comments")
    suspend fun fetchCharacterComments(@Path("id") characterId: Int): List<BangumiCreatorCommentDto>

    @GET("p1/persons/{id}/comments")
    suspend fun fetchPersonComments(@Path("id") personId: Int): List<BangumiCreatorCommentDto>

    @GET("p1/subjects/{id}/comments")
    suspend fun fetchSubjectComments(
        @Path("id") subjectId: Int,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): BangumiAnimeCommentResponseDto
}

/** Bangumi v0 API（api.bgm.tv）：搜索 / 详情 / 角色。 */
internal interface BangumiApi {

    @POST("v0/search/subjects")
    suspend fun search(
        @Body request: BangumiSearchDto,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): BangumiSearchResponseDto

    @GET("v0/subjects/{id}")
    suspend fun getSubject(@Path("id") id: Int): BangumiDetailDto

    @GET("v0/subjects/{id}/characters")
    suspend fun fetchSubjectCharacters(@Path("id") subjectId: Int): List<BangumiCreatorDto>
}
