package com.liomix.mikomi.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 导航栏顶级目的地路由。 */
@Serializable
data object HomeDestination : NavKey

@Serializable
data object AniMapDestination : NavKey

@Serializable
data object ProfileDestination : NavKey

/** 二级目的地路由（由顶级页面内跳转）。 */
@Serializable
data object SearchDestination : NavKey

/** 搜索结果页路由：搜索页的第二级页面（返回即回到搜索页）。 */
@Serializable
data class SearchResultDestination(val keyword: String) : NavKey

@Serializable
data object LibraryDestination : NavKey

@Serializable
data object HistoryDestination : NavKey

@Serializable
data object SettingsDestination : NavKey

/** 主题设置二级页面（我的页左上角图标进入）。 */
@Serializable
data object ThemeDestination : NavKey

/** 网络设置二级页面（设置页的第二页）。 */
@Serializable
data object NetworkDestination : NavKey

/** 时间表二级页面（首页快捷入口跳转）。 */
@Serializable
data object ScheduleDestination : NavKey

/** 排行榜二级页面（首页快捷入口跳转）。 */
@Serializable
data object RankDestination : NavKey

/** 详情页路由。 */
@Serializable
data class DetailDestination(val animeId: String) : NavKey

/** 播放器路由。 */
@Serializable
data class PlayerDestination(val animeId: String, val episodeId: String) : NavKey

/** 导航栏顶级目的地（从左到右）。 */
val topLevelDestinations: Set<NavKey> = setOf(
    HomeDestination,
    AniMapDestination,
    ProfileDestination,
)
