package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.Episode
import kotlinx.coroutines.flow.Flow

/** 动漫数据门面。UI 层只观察本地数据。 */
interface AnimeRepository {
    fun observeAnimeList(): Flow<List<Anime>>
    fun observeAnime(id: String): Flow<Anime?>
    fun observeEpisodes(animeId: String): Flow<List<Episode>>

    /** 从网络拉取并回写本地（Room SSOT）。 */
    suspend fun refreshAnimeList()
}
