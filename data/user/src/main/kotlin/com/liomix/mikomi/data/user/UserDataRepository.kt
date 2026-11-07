package com.liomix.mikomi.data.user

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.Favorite
import com.liomix.mikomi.core.model.UserPreferences
import com.liomix.mikomi.core.model.WatchHistory
import kotlinx.coroutines.flow.Flow

/** 用户数据门面：收藏、历史、偏好。 */
interface UserDataRepository {
    /** 收藏列表，最近收藏在前。 */
    fun observeFavorites(): Flow<List<Favorite>>
    fun observeHistory(): Flow<List<WatchHistory>>
    fun observeUserPreferences(): Flow<UserPreferences>

    /** 搜索历史（最新在前，最多 20 条）。 */
    fun observeSearchHistory(): Flow<List<String>>

    /**
     * 收藏。
     *
     * 传入整部番剧而不是 id：收藏表要存一份展示快照（标题 / 封面），否则收藏页得回头
     * 去查一张随时会被清理的表、或者等一次网络请求才画得出来。
     * 已收藏的重复调用不会改变位次。
     */
    suspend fun addFavorite(anime: Anime)

    suspend fun removeFavorite(animeId: String)

    /**
     * 批量取消收藏，返回被删掉的行。
     *
     * 返回值是「撤销」的全部依据：调用方留着它，撤销时原样写回，[Favorite.addedAt]
     * 不变，卡片回到原来的位次。
     */
    suspend fun removeFavorites(animeIds: List<String>): List<Favorite>

    /** 撤销取消收藏：把 [favorites] 原样写回。 */
    suspend fun restoreFavorites(favorites: List<Favorite>)

    /** 详情页拿到最新数据后自愈收藏快照；内容与存量一致时这条更新不落行。 */
    suspend fun refreshFavoriteSnapshot(anime: Anime)
    suspend fun saveProgress(animeId: String, episodeId: String, positionMs: Long, durationMs: Long)
    /** 设置日 / 夜：`light` / `dark`（`system` 为默认值，仅在用户从未拨过开关时生效）。 */
    suspend fun setTheme(theme: String)

    /** 设置主题配色预设 id（见 ThemePalette）。 */
    suspend fun setColorScheme(colorScheme: String)

    /** 设置桌面应用图标样式 id（见 AppIconStyle）。 */
    suspend fun setAppIcon(appIcon: String)

    /** 设置封面动效 id（见 TransitionStyle）。 */
    suspend fun setTransitionStyle(style: String)

    /** 设置页面动效 id（见 PageTransitionStyle）。 */
    suspend fun setPageTransition(style: String)


    /** 设置 Bangumi 连接方式 id（见 BangumiApiMode）。 */
    suspend fun setBangumiApiMode(mode: String)

    /** 设置镜像基地址（仅镜像模式使用）。 */
    suspend fun setBangumiMirrorUrl(url: String)

    /** 设置每集开播的默认倍速（1f = 原速）。 */
    suspend fun setPlayerDefaultSpeed(speed: Float)

    /** 设置长按画面的临时倍速。 */
    suspend fun setPlayerLongPressSpeed(speed: Float)

    /** 设置左右滑动整屏宽度对应的快进 / 快退秒数。 */
    suspend fun setPlayerSeekSweepSeconds(seconds: Int)

    /** 记录一次搜索关键词。 */
    suspend fun addSearchHistory(keyword: String)

    /** 删除单条搜索历史。 */
    suspend fun removeSearchHistory(keyword: String)

    /** 清空搜索历史。 */
    suspend fun clearSearchHistory()
}
