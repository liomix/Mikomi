package com.liomix.mikomi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.database.entity.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    @Query("SELECT * FROM anime")
    fun observeAnime(): Flow<List<AnimeEntity>>

    @Query("SELECT * FROM anime WHERE id = :id")
    fun observeAnime(id: String): Flow<AnimeEntity?>

    @Query("SELECT * FROM anime ORDER BY popularity DESC LIMIT :limit")
    fun observeTopAnime(limit: Int): Flow<List<AnimeEntity>>

    @Upsert
    suspend fun upsertAll(anime: List<AnimeEntity>)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episode WHERE animeId = :animeId ORDER BY number")
    fun observeEpisodes(animeId: String): Flow<List<EpisodeEntity>>

    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)
}
