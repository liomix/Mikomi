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
    /** Bangumi 连接方式 id（见 BangumiApiMode）；`direct` 为默认。 */
    val bangumiApiMode: String = "direct",
    /** 镜像基地址（仅镜像模式使用）；空串表示未配置。 */
    val bangumiMirrorUrl: String = "",
    /** 每集开播时的默认倍速，1f 为原速（见播放设置页）。 */
    val playerDefaultSpeed: Float = 1f,
    /** 长按画面时的临时倍速；松开恢复。 */
    val playerLongPressSpeed: Float = 2f,
    /** 左右滑动的幅度：手指横移**整屏宽度**对应的快进 / 快退秒数。 */
    val playerSeekSweepSeconds: Int = 90,
)
