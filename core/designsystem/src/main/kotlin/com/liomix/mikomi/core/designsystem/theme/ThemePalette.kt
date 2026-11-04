package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题配色预设。
 *
 * 枚举顺序即设置页色卡顺序：蓝色（默认）→ 粉色 → 绿色 → 紫色 → 橙色。
 * 色卡底用 [swatchGradient] 的四色色斑（主色打底），light/dark 两套容器色保证各自背景下的对比度。
 *
 * 预设主色：蓝 `#229AF0` / 粉 `#F84890` / 绿 `#16BF6E` / 紫 `#CD54F1` / 橙 `#E8760C`。
 * 五个主色的相对亮度（WCAG relative luminance）都在 0.26～0.39 之间，橙色取 0.30 ≈ 其余四色的均值，
 * 所以并排时不会有某一块明显更亮或更暗。
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
    /**
     * 色卡渐变的四色，顺序即 Telegram `MotionBackgroundDrawable` 的 `colors[0..3]`。
     *
     * 第 1 个永远等于 [lightPrimary]（主色打底），其余三个是同色系的深 / 亮 / 偏色，
     * 分别从色卡的三个角渗出来；不要塞进无关色相，否则主色就不「主」了。
     */
    val swatchGradient: List<Color>,
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
        // 主蓝打底，深蓝 / 亮青 / 蓝紫各占一角。
        swatchGradient = listOf(
            Color(0xFF229AF0),
            Color(0xFF1565C0),
            Color(0xFF4FC3F7),
            Color(0xFF3D6BE0),
        ),
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
        // 玫粉打底，深玫瑰 / 浅粉 / 偏珊瑚各占一角。
        swatchGradient = listOf(
            Color(0xFFF84890),
            Color(0xFFB0124F),
            Color(0xFFFF9EC1),
            Color(0xFFFF4F6E),
        ),
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
        // 翠绿打底，深绿 / 薄荷 / 青绿各占一角。
        swatchGradient = listOf(
            Color(0xFF16BF6E),
            Color(0xFF0B7A45),
            Color(0xFF6EE7A8),
            Color(0xFF2AB6A5),
        ),
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
        // 紫罗兰打底，深紫 / 浅紫 / 蓝紫各占一角。
        swatchGradient = listOf(
            Color(0xFFCD54F1),
            Color(0xFF8E24AA),
            Color(0xFFE9A6F8),
            Color(0xFF7C4DFF),
        ),
    ),

    /** 橙色。 */
    Orange(
        id = "orange",
        label = "橙色",
        lightPrimary = Color(0xFFE8760C),
        lightPrimaryContainer = Color(0xFFFFDFC7),
        lightOnPrimaryContainer = Color(0xFF2B1600),
        darkPrimary = Color(0xFFFFB77C),
        darkPrimaryContainer = Color(0xFF7A3200),
        darkOnPrimaryContainer = Color(0xFFFFDFC7),
        // 橙打底，焦橙 / 浅杏 / 琥珀各占一角。
        swatchGradient = listOf(
            Color(0xFFE8760C),
            Color(0xFFC2410C),
            Color(0xFFFFC892),
            Color(0xFFF5A623),
        ),
    ),
    ;

    /** 色卡的主色（＝[swatchGradient] 的第一个颜色），选中描边等单点用色取它。 */
    val swatch: Color get() = swatchGradient.first()

    companion object {
        /** 按 id 取配色；未知 id 回退 [Default]（蓝色）。 */
        fun fromId(id: String?): ThemePalette =
            entries.firstOrNull { it.id == id } ?: Default
    }
}
