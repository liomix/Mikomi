package com.liomix.mikomi.core.model

/** 用户偏好（非结构化，走 DataStore）。 */
data class UserPreferences(
    val theme: String = "system",
    val defaultQuality: String = "auto",
    val autoPlayNext: Boolean = true,
    /** 主题配色预设 id（见 ThemePalette）；`default` 为默认蓝色。 */
    val colorScheme: String = "default",
    /** 桌面应用图标样式 id（见 AppIconStyle）；`default` 为默认图标。 */
    val appIcon: String = "default",
    /** 封面动效 id（见 TransitionStyle，作用在封面飞行上）；`translate` 为默认。 */
    val transitionStyle: String = "translate",
    /** 页面动效 id（见 PageTransitionStyle，作用在页面过渡上）；`standard` 为默认。 */
    val pageTransitionStyle: String = "standard",
)
