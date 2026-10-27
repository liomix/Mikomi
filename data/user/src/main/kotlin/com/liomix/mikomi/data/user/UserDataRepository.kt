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

    /** 搜索历史（最新在前，最多 20 条）。 */
    fun observeSearchHistory(): Flow<List<String>>

    suspend fun addFavorite(animeId: String)
    suspend fun removeFavorite(animeId: String)
    suspend fun saveProgress(animeId: String, episodeId: String, positionMs: Long, durationMs: Long)
    suspend fun setTheme(theme: String)

    /** 设置主题配色预设 id（见 ThemePalette）。 */
    suspend fun setColorScheme(colorScheme: String)

    /** 设置桌面应用图标样式 id（见 AppIconStyle）。 */
    suspend fun setAppIcon(appIcon: String)

    /** 记录一次搜索关键词。 */
    suspend fun addSearchHistory(keyword: String)

    /** 删除单条搜索历史。 */
    suspend fun removeSearchHistory(keyword: String)

    /** 清空搜索历史。 */
    suspend fun clearSearchHistory()
}
