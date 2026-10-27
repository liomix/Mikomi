package com.liomix.mikomi.core.model

/** 用户偏好（非结构化，走 DataStore）。 */
data class UserPreferences(
    val theme: String = "system",
    val defaultQuality: String = "auto",
    val autoPlayNext: Boolean = true,
    /** 主题配色预设 id（见 ThemePalette）；`default` 表示跟随系统动态取色。 */
    val colorScheme: String = "default",
)
