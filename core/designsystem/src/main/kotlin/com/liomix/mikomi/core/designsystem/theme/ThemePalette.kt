package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题配色预设。
 *
 * 枚举顺序即设置页色卡顺序：蓝色（默认）→ 粉色 → 绿色 → 紫色。
 * 色卡用 [lightPrimary] 作纯色底；light/dark 两套容器色保证各自背景下的对比度。
 *
 * 预设主色：蓝 `#229AF0` / 粉 `#F84890` / 绿 `#16BF6E` / 紫 `#CD54F1`。
 *
 * 作用域（切换后实时生效，统一取 primary / primaryContainer）：
 * - 按钮、开关、单选框等控件的启用 / 选中态；
 * - 底部导航栏选中项的图标与文字（及淡化的胶囊底）；
 * - 详情页 Tab、搜索页可交互文字、设置页色卡选中描边等 primary 用色；
 * - 主页下拉刷新指示器（primaryContainer / onPrimaryContainer）。
 *
 * 不在作用域内：夜间模式的背景与表面色（固定纯黑 / #181818）。
 */
enum class ThemePalette(
    val id: String,
    val label: String,
    val lightPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    /** 夜间主色变体：当前夜间统一沿用 [lightPrimary]（按对比度自动提亮），此字段留作高对比备用。 */
    val darkPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
) {
    /** 蓝色：默认配色。 */
    Default(
        id = "default",
        label = "蓝色",
        lightPrimary = Color(0xFF229AF0),
        lightPrimaryContainer = Color(0xFFD6E7FF),
        lightOnPrimaryContainer = Color(0xFF001B3F),
        darkPrimary = Color(0xFF7CC4FF),
        darkPrimaryContainer = Color(0xFF00497A),
        darkOnPrimaryContainer = Color(0xFFD6E7FF),
    ),

    /** 粉色。 */
    Sakura(
        id = "sakura",
        label = "粉色",
        lightPrimary = Color(0xFFF84890),
        lightPrimaryContainer = Color(0xFFFFD9E4),
        lightOnPrimaryContainer = Color(0xFF3E0021),
        darkPrimary = Color(0xFFFFB1C8),
        darkPrimaryContainer = Color(0xFF8E0F45),
        darkOnPrimaryContainer = Color(0xFFFFD9E4),
    ),

    /** 绿色。 */
    Mint(
        id = "mint",
        label = "绿色",
        lightPrimary = Color(0xFF16BF6E),
        lightPrimaryContainer = Color(0xFFC8F7DC),
        lightOnPrimaryContainer = Color(0xFF00210F),
        darkPrimary = Color(0xFF6FE0A5),
        darkPrimaryContainer = Color(0xFF00522C),
        darkOnPrimaryContainer = Color(0xFFC8F7DC),
    ),

    /** 紫色。 */
    Purple(
        id = "purple",
        label = "紫色",
        lightPrimary = Color(0xFFCD54F1),
        lightPrimaryContainer = Color(0xFFF2DAFB),
        lightOnPrimaryContainer = Color(0xFF2E0043),
        darkPrimary = Color(0xFFE3A8F7),
        darkPrimaryContainer = Color(0xFF6A1B8A),
        darkOnPrimaryContainer = Color(0xFFF2DAFB),
    ),
    ;

    /** 色卡纯色块颜色：直接取该配色的主色。 */
    val swatch: Color get() = lightPrimary

    companion object {
        /** 按 id 取配色；未知 id 回退 [Default]（蓝色）。 */
        fun fromId(id: String?): ThemePalette =
            entries.firstOrNull { it.id == id } ?: Default
    }
}
