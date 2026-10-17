package com.liomix.mikomi.data.anime.remote.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** AniList GraphQL 请求体（标准 GraphQL over HTTP）。 */
@Serializable
data class AniListGraphqlRequest(
    val query: String,
    val variables: AniListPageVariables,
)

@Serializable
data class AniListPageVariables(
    val page: Int,
    val perPage: Int,
)

/** GraphQL 响应外层。 */
@Serializable
data class AniListGraphqlResponse(
    val data: AniListData? = null,
)

@Serializable
data class AniListData(
    @SerialName("Page") val page: AniListPage? = null,
)

@Serializable
data class AniListPage(
    val media: List<AniListMediaDto> = emptyList(),
)

@Serializable
data class AniListMediaDto(
    val id: Int,
    val title: AniListTitleDto? = null,
    @SerialName("coverImage") val coverImage: AniListCoverImageDto? = null,
    @SerialName("averageScore") val averageScore: Int? = null,
    val popularity: Int? = null,
    val description: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
)

@Serializable
data class AniListTitleDto(
    val romaji: String? = null,
    val english: String? = null,
    @SerialName("native") val nativeTitle: String? = null,
)

@Serializable
data class AniListCoverImageDto(
    val large: String? = null,
    val medium: String? = null,
)
