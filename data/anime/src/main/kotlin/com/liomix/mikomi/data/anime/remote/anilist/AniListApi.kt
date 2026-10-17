package com.liomix.mikomi.data.anime.remote.anilist

import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlRequest
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlResponse
import retrofit2.http.Body
import retrofit2.http.POST

/** AniList GraphQL 端点（https://graphql.anilist.co）。 */
internal interface AniListApi {

    @POST("/")
    suspend fun execute(@Body request: AniListGraphqlRequest): AniListGraphqlResponse
}
