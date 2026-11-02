package com.liomix.mikomi.feature.profile.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 个人页配色。
 *
 * 日间对齐 Telegram 的浅灰底 + 纯白卡片；夜间跟随主题转纯黑 + 深灰卡片，
 * 不写死日间色值，避免夜间模式下列表整片发白。
 */
internal data class ProfilePalette(
    val background: Color,
    val card: Color,
    val title: Color,
    val value: Color,
    val secondary: Color,
    val tertiary: Color,
    val divider: Color,
    val skeleton: Color,
    val accent: Color,
    val accentContainer: Color,
)

@Composable
internal fun rememberProfilePalette(): ProfilePalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return remember(dark, scheme.primary) {
        if (dark) {
            ProfilePalette(
                background = Color(0xFF000000),
                card = Color(0xFF1A1A1C),
                title = Color(0xFFF5F5F5),
                value = Color(0xFFEDEDED),
                secondary = Color(0xFF8E9296),
                tertiary = Color(0xFF9AA0A6),
                divider = Color(0xFF232326),
                skeleton = Color(0xFF232326),
                accent = Color(0xFF6FB6E8),
                accentContainer = Color(0xFF16303F),
            )
        } else {
            ProfilePalette(
                background = Color(0xFFF1F1F3),
                card = Color(0xFFFFFFFF),
                title = Color(0xFF222222),
                value = Color(0xFF1A1D21),
                secondary = Color(0xFF79807E),
                tertiary = Color(0xFF82868A),
                divider = Color(0xFFF7F7F8),
                skeleton = Color(0xFFF2F2F4),
                accent = Color(0xFF3A95D5),
                accentContainer = Color(0xFFDFECF6),
            )
        }
    }
}
