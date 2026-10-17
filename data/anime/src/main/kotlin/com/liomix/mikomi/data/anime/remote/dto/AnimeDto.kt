package com.liomix.mikomi.data.anime.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeDto(
    val id: String,
    val title: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val synopsis: String? = null,
    val rating: Double? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
data class EpisodeDto(
    val id: String,
    val number: Int,
    val title: String? = null,
    @SerialName("duration_sec") val durationSec: Long = 0,
)
