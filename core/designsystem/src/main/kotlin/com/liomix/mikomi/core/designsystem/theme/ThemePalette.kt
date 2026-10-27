package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题配色预设。
 *
 * [Default] 表示跟随系统动态取色（Android 12+）或内置紫色；
 * 其余为固定主色方案：[gradient] 用于设置页色卡预览，
 * light/dark 两套主色保证各自背景下的对比度。
 */
enum class ThemePalette(
    val id: String,
    val label: String,
    /** 色卡渐变（3 色，左上到右下）。 */
    val gradient: List<Color>,
    val lightPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
) {
    Default(
        id = "default",
        label = "默认",
        gradient = listOf(Color(0xFFD0BCFF), Color(0xFFEFB8C8), Color(0xFFB8C8FF)),
        lightPrimary = Color(0xFF6650A4),
        lightPrimaryContainer = Color(0xFFEADDFF),
        lightOnPrimaryContainer = Color(0xFF21005D),
        darkPrimary = Color(0xFFD0BCFF),
        darkPrimaryContainer = Color(0xFF4F378B),
        darkOnPrimaryContainer = Color(0xFFEADDFF),
    ),
    Sakura(
        id = "sakura",
        label = "樱花",
        gradient = listOf(Color(0xFFFFC1D9), Color(0xFFFF9EC4), Color(0xFFFFD8E6)),
        lightPrimary = Color(0xFFC2185B),
        lightPrimaryContainer = Color(0xFFFFD9E2),
        lightOnPrimaryContainer = Color(0xFF3E0021),
        darkPrimary = Color(0xFFFFB1C8),
        darkPrimaryContainer = Color(0xFF8E0F45),
        darkOnPrimaryContainer = Color(0xFFFFD9E2),
    ),
    Sunrise(
        id = "sunrise",
        label = "暖阳",
        gradient = listOf(Color(0xFFFFD9A8), Color(0xFFFFB347), Color(0xFFFFCB9A)),
        lightPrimary = Color(0xFFB45309),
        lightPrimaryContainer = Color(0xFFFFE0B2),
        lightOnPrimaryContainer = Color(0xFF3E2500),
        darkPrimary = Color(0xFFFFB870),
        darkPrimaryContainer = Color(0xFF7A3E00),
        darkOnPrimaryContainer = Color(0xFFFFDCBF),
    ),
    Lemon(
        id = "lemon",
        label = "柠檬",
        gradient = listOf(Color(0xFFFFF3B0), Color(0xFFFFD54F), Color(0xFFFFE8A3)),
        lightPrimary = Color(0xFF8A6D00),
        lightPrimaryContainer = Color(0xFFFFF0B3),
        lightOnPrimaryContainer = Color(0xFF2E2800),
        darkPrimary = Color(0xFFE6C34A),
        darkPrimaryContainer = Color(0xFF5C4A00),
        darkOnPrimaryContainer = Color(0xFFFFF0B3),
    ),
    Mint(
        id = "mint",
        label = "薄荷",
        gradient = listOf(Color(0xFFA8E6CF), Color(0xFF4DD0C0), Color(0xFFB8F0D8)),
        lightPrimary = Color(0xFF00796B),
        lightPrimaryContainer = Color(0xFFB2DFDB),
        lightOnPrimaryContainer = Color(0xFF00201C),
        darkPrimary = Color(0xFF4DD0C0),
        darkPrimaryContainer = Color(0xFF00504A),
        darkOnPrimaryContainer = Color(0xFFB2DFDB),
    ),
    Ocean(
        id = "ocean",
        label = "海洋",
        gradient = listOf(Color(0xFFA8CFFF), Color(0xFF4A90FF), Color(0xFF9AD8FF)),
        lightPrimary = Color(0xFF1565C0),
        lightPrimaryContainer = Color(0xFFD6E4FF),
        lightOnPrimaryContainer = Color(0xFF001B3F),
        darkPrimary = Color(0xFF90CAF9),
        darkPrimaryContainer = Color(0xFF0D47A1),
        darkOnPrimaryContainer = Color(0xFFD6E4FF),
    ),
    ;

    companion object {
        /** 按 id 取配色；未知 id 回退 [Default]。 */
        fun fromId(id: String?): ThemePalette =
            entries.firstOrNull { it.id == id } ?: Default
    }
}
