package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题配色预设。
 *
 * 枚举顺序即设置页色卡顺序：蓝色（默认）→ 粉色 → 绿色 → 暖阳 → 柠檬。
 * 色卡用 [lightPrimary] 作纯色底；light/dark 两套容器色保证各自背景下的对比度。
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
        lightPrimary = Color(0xFF1565C0),
        lightPrimaryContainer = Color(0xFFD6E4FF),
        lightOnPrimaryContainer = Color(0xFF001B3F),
        darkPrimary = Color(0xFF90CAF9),
        darkPrimaryContainer = Color(0xFF0D47A1),
        darkOnPrimaryContainer = Color(0xFFD6E4FF),
    ),

    /** 粉色。 */
    Sakura(
        id = "sakura",
        label = "粉色",
        lightPrimary = Color(0xFFC2185B),
        lightPrimaryContainer = Color(0xFFFFD9E2),
        lightOnPrimaryContainer = Color(0xFF3E0021),
        darkPrimary = Color(0xFFFFB1C8),
        darkPrimaryContainer = Color(0xFF8E0F45),
        darkOnPrimaryContainer = Color(0xFFFFD9E2),
    ),

    /** 绿色。 */
    Mint(
        id = "mint",
        label = "绿色",
        lightPrimary = Color(0xFF2E7D32),
        lightPrimaryContainer = Color(0xFFC8E6C9),
        lightOnPrimaryContainer = Color(0xFF0A2E0C),
        darkPrimary = Color(0xFF81C784),
        darkPrimaryContainer = Color(0xFF1B5E20),
        darkOnPrimaryContainer = Color(0xFFC8E6C9),
    ),

    /** 暖阳（橙）。 */
    Sunrise(
        id = "sunrise",
        label = "暖阳",
        lightPrimary = Color(0xFFB45309),
        lightPrimaryContainer = Color(0xFFFFE0B2),
        lightOnPrimaryContainer = Color(0xFF3E2500),
        darkPrimary = Color(0xFFFFB870),
        darkPrimaryContainer = Color(0xFF7A3E00),
        darkOnPrimaryContainer = Color(0xFFFFDCBF),
    ),

    /** 柠檬（黄）。 */
    Lemon(
        id = "lemon",
        label = "柠檬",
        lightPrimary = Color(0xFF8A6D00),
        lightPrimaryContainer = Color(0xFFFFF0B3),
        lightOnPrimaryContainer = Color(0xFF2E2800),
        darkPrimary = Color(0xFFE6C34A),
        darkPrimaryContainer = Color(0xFF5C4A00),
        darkOnPrimaryContainer = Color(0xFFFFF0B3),
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
