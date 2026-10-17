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

@Serializable
data object LibraryDestination : NavKey

@Serializable
data object HistoryDestination : NavKey

@Serializable
data object SettingsDestination : NavKey

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
