package com.liomix.mikomi.data.anime.remote.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** AniList GraphQL 请求体（标准 GraphQL over HTTP，variables 按查询动态组装）。 */
@Serializable
data class AniListGraphqlRequest(
    val query: String,
    val variables: JsonObject = JsonObject(emptyMap()),
)

/** GraphQL 响应外层。 */
@Serializable
data class AniListGraphqlResponse(
    val data: AniListData? = null,
)

@Serializable
data class AniListData(
    @SerialName("Page") val page: AniListPage? = null,
    @SerialName("Media") val media: AniListMediaDto? = null,
)

@Serializable
data class AniListPage(
    val media: List<AniListMediaDto> = emptyList(),
    val pageInfo: AniListPageInfo? = null,
)

@Serializable
data class AniListPageInfo(
    val hasNextPage: Boolean = false,
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
