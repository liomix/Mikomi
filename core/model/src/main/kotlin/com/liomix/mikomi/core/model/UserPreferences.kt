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
)
