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

    /**
     * 拉取单个番剧的完整详情并回写本地（Bangumi 详情接口）。
     *
     * [animeId] 为带前缀的领域 id（`bangumi:` / `anilist:`）；
     * `anilist:` 前缀先经 id 映射转到 Bangumi，再拉详情。
     * 失败向上抛出，由上层决定降级展示本地已有数据。
     */
    suspend fun refreshAnimeDetail(animeId: String)
}
