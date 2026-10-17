package com.liomix.mikomi.data.user.internal

import com.liomix.mikomi.core.database.dao.FavoriteDao
import com.liomix.mikomi.core.database.dao.HistoryDao
import com.liomix.mikomi.core.database.entity.FavoriteEntity
import com.liomix.mikomi.core.database.entity.HistoryEntity
import com.liomix.mikomi.core.datastore.UserPreferencesDataSource
import com.liomix.mikomi.core.model.Favorite
import com.liomix.mikomi.core.model.UserPreferences
import com.liomix.mikomi.core.model.WatchHistory
import com.liomix.mikomi.data.user.UserDataRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class UserDataRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val userPreferencesDataSource: UserPreferencesDataSource,
) : UserDataRepository {

    override fun observeFavorites(): Flow<List<Favorite>> =
        favoriteDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    override fun observeHistory(): Flow<List<WatchHistory>> =
        historyDao.observeHistory().map { list -> list.map { it.toDomain() } }

    override fun observeUserPreferences(): Flow<UserPreferences> =
        userPreferencesDataSource.userPreferences

    override suspend fun addFavorite(animeId: String) {
        favoriteDao.upsert(FavoriteEntity(animeId = animeId, addedAt = System.currentTimeMillis()))
    }

    override suspend fun removeFavorite(animeId: String) {
        favoriteDao.delete(animeId)
    }

    override suspend fun saveProgress(
        animeId: String,
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        historyDao.upsert(
            HistoryEntity(
                episodeId = episodeId,
                animeId = animeId,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun setTheme(theme: String) {
        userPreferencesDataSource.setTheme(theme)
    }

    private fun FavoriteEntity.toDomain(): Favorite = Favorite(animeId = animeId, addedAt = addedAt)

    private fun HistoryEntity.toDomain(): WatchHistory = WatchHistory(
        episodeId = episodeId,
        animeId = animeId,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
    )
}
