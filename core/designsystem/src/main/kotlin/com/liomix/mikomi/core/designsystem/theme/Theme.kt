package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

/** 预设配色下 primary 上的前景色（各预设主色均为中深色调，统一用白字）。 */
private val PaletteOnPrimary = Color(0xFFFFFFFF)

/** 夜间背景：纯黑。 */
private val NightBackground = Color(0xFF000000)

/** 夜间的卡片 / 面板底色。 */
private val NightSurface = Color(0xFF181818)

/**
 * 夜间表面层级：背景纯黑、卡片 #181818，仅最高层级略提亮，
 * 保证卡片在纯黑背景上仍有边界，又不会出现大块浅灰。
 */
private fun ColorScheme.withNightSurfaces(): ColorScheme = copy(
    background = NightBackground,
    surface = NightSurface,
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = NightSurface,
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF212121),
    surfaceContainerHighest = Color(0xFF262626),
    surfaceVariant = Color(0xFF2A2A2A),
)

/**
 * 日间表面层级：浅灰底 + 纯白卡片。
 *
 * M3 默认的 background(#FFFBFE) 与 surfaceContainerLow(#F7F2FA) 几乎是同一个白，
 * 设置页的卡片因此「贴」在背景上看不出边界。这里把背景压到浅灰紫、卡片提到纯白，
 * 与夜间「深底稍亮卡片」的思路保持一致，卡片在两种模式下都有清晰边界。
 */
private fun ColorScheme.withDaySurfaces(): ColorScheme = copy(
    background = Color(0xFFECE6F0),
    surface = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF5EFF9),
    surfaceContainerHigh = Color(0xFFE6E0E9),
    surfaceContainerHighest = Color(0xFFDDD7E1),
    surfaceVariant = Color(0xFFE7E0EC),
)

/**
 * 应用主题。
 *
 * @param colorSchemeId 用户选择的配色预设 id（见 [ThemePalette]）；
 *   默认 `default` 为蓝色，不再跟随系统动态取色，保证默认外观统一。
 */
@Composable
fun MikomiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemeId: String = ThemePalette.Default.id,
    content: @Composable () -> Unit,
) {
    val palette = ThemePalette.fromId(colorSchemeId)
    // 所有配色（含默认蓝色）都用预设主色覆盖基础方案，其余语义色保持不变。
    // 主色在日 / 夜保持同一色值并把夜间版本自动提亮到可读对比度，
    // 只有容器色按模式区分，避免深色界面出现大块亮色。
    val colorScheme = (if (darkTheme) DarkColorScheme else LightColorScheme).copy(
        primary = if (darkTheme) palette.lightPrimary.forNight() else palette.lightPrimary,
        onPrimary = PaletteOnPrimary,
        primaryContainer = if (darkTheme) {
            palette.darkPrimaryContainer
        } else {
            palette.lightPrimaryContainer
        },
        onPrimaryContainer = if (darkTheme) {
            palette.darkOnPrimaryContainer
        } else {
            palette.lightOnPrimaryContainer
        },
    )

    // 表面层级按模式锁定：夜间纯黑底 + #181818 卡片；日间浅灰底 + 纯白卡片。
    val resolvedScheme = if (darkTheme) {
        colorScheme.withNightSurfaces()
    } else {
        colorScheme.withDaySurfaces()
    }

    MaterialTheme(
        colorScheme = resolvedScheme,
        typography = Typography,
    ) {
        // MaterialTheme 本身不设置 LocalContentColor（默认黑色），
        // 只有 Surface 内部才会按背景推导内容色。页面中直接放在 Box/Column 里的
        // Text、Icon 因此会一直是黑色（夜间不可读）。这里统一兜底为 onBackground，
        // Surface 内部仍会用自己的 contentColor 覆盖。
        CompositionLocalProvider(
            LocalContentColor provides resolvedScheme.onBackground,
            content = content,
        )
    }
}

/**
 * 夜间提亮：保持色相，逐步向白色混合直到在纯黑背景上达到可读对比度。
 *
 * 纯黑背景的对比度 = (L + 0.05) / 0.05，[NIGHT_MIN_CONTRAST] 取 3.8
 * （图标与大号文字 3:1、正文接近 4.5:1 的折中）。
 */
private fun Color.forNight(): Color {
    var color = this
    var step = 0
    while (contrastOnBlack(color) < NIGHT_MIN_CONTRAST && step < MAX_LIGHTEN_STEPS) {
        color = lerp(color, Color.White, LIGHTEN_STEP)
        step++
    }
    return color
}

private fun contrastOnBlack(color: Color): Float = (color.luminance() + 0.05f) / 0.05f

/** 夜间主色的最低可读对比度（纯黑背景）。 */
private const val NIGHT_MIN_CONTRAST = 3.8f

/** 单次提亮的白色混合比例与最大步数（避免过度发白）。 */
private const val LIGHTEN_STEP = 0.08f
private const val MAX_LIGHTEN_STEPS = 12
