package com.liomix.mikomi.data.anime.remote

import com.liomix.mikomi.data.anime.remote.dto.AnimeDto
import retrofit2.http.GET

interface AnimeApi {
    @GET("anime")
    suspend fun getAnimeList(): List<AnimeDto>
}
