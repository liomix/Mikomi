package com.liomix.mikomi.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    val colorScheme = when {
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
