package com.liomix.mikomi.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
 * 应用主题。
 *
 * @param colorSchemeId 用户选择的配色预设 id（见 [ThemePalette]）；
 *   默认值 `default` 表示跟随系统动态取色（Android 12+）或内置紫色方案。
 */
@Composable
fun MikomiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    colorSchemeId: String = ThemePalette.Default.id,
    content: @Composable () -> Unit,
) {
    val palette = ThemePalette.fromId(colorSchemeId)
    val baseScheme = when {
        // 用户显式选了非默认配色 → 用预设主色覆盖基础方案（保持其余语义色不变）。
        palette != ThemePalette.Default -> {
            val base = if (darkTheme) DarkColorScheme else LightColorScheme
            if (darkTheme) {
                base.copy(
                    primary = palette.darkPrimary,
                    primaryContainer = palette.darkPrimaryContainer,
                    onPrimaryContainer = palette.darkOnPrimaryContainer,
                )
            } else {
                base.copy(
                    primary = palette.lightPrimary,
                    primaryContainer = palette.lightPrimaryContainer,
                    onPrimaryContainer = palette.lightOnPrimaryContainer,
                )
            }
        }

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 夜间统一锁定表面色：背景纯黑、卡片 #181818（覆盖动态取色带来的灰底）。
    val colorScheme = if (darkTheme) baseScheme.withNightSurfaces() else baseScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
