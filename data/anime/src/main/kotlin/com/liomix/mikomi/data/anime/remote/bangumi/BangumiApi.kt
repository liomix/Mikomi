package com.liomix.mikomi.data.anime.remote.bangumi

import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchRequest
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchResponse
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSubjectDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiTrendResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Bangumi Next API（next.bgm.tv）：趋势 / 日历。 */
internal interface BangumiTrendApi {

    @GET("p1/trending/subjects")
    suspend fun fetchTrending(
        @Query("type") type: Int,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): BangumiTrendResponse
}

/** Bangumi v0 API（api.bgm.tv）：搜索 / 详情。 */
internal interface BangumiApi {

    @POST("v0/search/subjects")
    suspend fun search(
        @Body request: BangumiSearchRequest,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): BangumiSearchResponse

    @GET("v0/subjects/{id}")
    suspend fun getSubject(@Path("id") id: Int): BangumiSubjectDto
}
