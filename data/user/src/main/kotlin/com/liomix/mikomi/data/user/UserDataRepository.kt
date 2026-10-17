package com.liomix.mikomi.data.user

import com.liomix.mikomi.core.model.Favorite
import com.liomix.mikomi.core.model.UserPreferences
import com.liomix.mikomi.core.model.WatchHistory
import kotlinx.coroutines.flow.Flow

/** 用户数据门面：收藏、历史、偏好。 */
interface UserDataRepository {
    fun observeFavorites(): Flow<List<Favorite>>
    fun observeHistory(): Flow<List<WatchHistory>>
    fun observeUserPreferences(): Flow<UserPreferences>

    suspend fun addFavorite(animeId: String)
    suspend fun removeFavorite(animeId: String)
    suspend fun saveProgress(animeId: String, episodeId: String, positionMs: Long, durationMs: Long)
    suspend fun setTheme(theme: String)
}
