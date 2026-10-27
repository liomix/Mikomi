package com.liomix.mikomi.data.anime.remote.bangumi

import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCharacterDetailDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCharacterDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCommentDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiPersonDetailDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchRequest
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchResponse
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiStaffResponseDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSubjectDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiTrendResponse
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
    ): BangumiTrendResponse

    @GET("p1/subjects/{id}/staffs/persons")
    suspend fun fetchSubjectStaff(@Path("id") subjectId: Int): BangumiStaffResponseDto

    @GET("p1/characters/{id}")
    suspend fun fetchCharacterDetail(@Path("id") characterId: Int): BangumiCharacterDetailDto

    @GET("p1/persons/{id}")
    suspend fun fetchPersonDetail(@Path("id") personId: Int): BangumiPersonDetailDto

    @GET("p1/characters/{id}/comments")
    suspend fun fetchCharacterComments(@Path("id") characterId: Int): List<BangumiCommentDto>

    @GET("p1/persons/{id}/comments")
    suspend fun fetchPersonComments(@Path("id") personId: Int): List<BangumiCommentDto>
}

/** Bangumi v0 API（api.bgm.tv）：搜索 / 详情 / 角色。 */
internal interface BangumiApi {

    @POST("v0/search/subjects")
    suspend fun search(
        @Body request: BangumiSearchRequest,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): BangumiSearchResponse

    @GET("v0/subjects/{id}")
    suspend fun getSubject(@Path("id") id: Int): BangumiSubjectDto

    @GET("v0/subjects/{id}/characters")
    suspend fun fetchSubjectCharacters(@Path("id") subjectId: Int): List<BangumiCharacterDto>
}
