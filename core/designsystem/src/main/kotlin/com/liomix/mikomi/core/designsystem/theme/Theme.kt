package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.util.concurrent.ConcurrentHashMap

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

/** 预设配色下 primary 上的前景色：四种预设主色都偏亮，统一用黑字以满足 4.5:1 对比度。 */
private val PaletteOnPrimary = Color(0xFF000000)

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
 * 日间表面层级：中性浅灰底 + 纯白卡片。
 *
 * M3 默认的 background(#FFFBFE) 与 surfaceContainerLow(#F7F2FA) 几乎是同一个白，
 * 设置页的卡片因此「贴」在背景上看不出边界。这里把背景压到中性浅灰、卡片提到纯白，
 * 与夜间「深底稍亮卡片」的思路保持一致，卡片在两种模式下都有清晰边界。
 *
 * 容器色、描边色一并去掉了 M3 默认的紫调（原值是 `#F5EFF9` / `#E6E0E9` / `#DDD7E1` /
 * `#CAC4D0`）——底色中性化之后，紫调的 Tab 底、输入框描边会明显「串色」。
 * 亮度阶梯保持与 M3 相同的相对关系：background 低于 surfaceContainer，
 * High / Highest 比表面更暗，用于未选中 chip、进度轨道这类强调块。
 */
private fun ColorScheme.withDaySurfaces(): ColorScheme = copy(
    background = Color(0xFFF1F1F3),
    surface = Color(0xFFFFFFFF),
    // 默认文字 / 图标用纯黑：M3 的 onBackground(#1C1B1F)、onSurface(#1C1B1F) 都带一点紫调，
    // 压在去掉紫调的中性灰底上会显脏。没显式指定 color 的 Text / Icon 走的正是这两个值
    // （根层由 MikomiTheme 用 onBackground 兜底 LocalContentColor，Surface 内部用 onSurface）。
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF7F7F9),
    surfaceContainerHigh = Color(0xFFE8E8EB),
    surfaceContainerHighest = Color(0xFFE1E1E5),
    surfaceVariant = Color(0xFFE9E9EC),
    outline = Color(0xFF76767A),
    outlineVariant = Color(0xFFC7C7CC),
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
    // 按（模式 + 配色）取缓存：切日夜时这里是一次 Map 命中，不再重算方案。
    val colorScheme = remember(darkTheme, palette) { resolveColorScheme(darkTheme, palette) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
    ) {
        // MaterialTheme 本身不设置 LocalContentColor（默认黑色），
        // 只有 Surface 内部才会按背景推导内容色。页面中直接放在 Box/Column 里的
        // Text、Icon 因此会一直是黑色（夜间不可读）。这里统一兜底为 onBackground，
        // Surface 内部仍会用自己的 contentColor 覆盖。
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onBackground,
            content = content,
        )
    }
}

/**
 * 已解析方案的缓存，键是「模式 + 配色 id」。
 *
 * 一个方案要付三笔账：`darkColorScheme()` / `lightColorScheme()` 的首次类初始化、
 * 夜间主色为凑对比度做的多次 `lerp` + `luminance` 循环、以及三次 `ColorScheme.copy`。
 * 单看都是微秒级，但它们全落在「点下日夜开关」那一帧上，冷启动后第一次切换就会被感知成顿挫。
 * 缓存之后同一套方案只算一次，App 启动时再用 [warmUpColorSchemes] 把账提前付掉。
 */
private val ResolvedSchemes = ConcurrentHashMap<SchemeKey, ColorScheme>()

private data class SchemeKey(val darkTheme: Boolean, val colorSchemeId: String)

/**
 * 取（必要时构建）一套可直接交给 `MaterialTheme` 的完整配色。
 *
 * 纯函数，不依赖 Compose 运行时，可以从任意线程调用 —— 预热正是跑在后台线程上的。
 * 结果不可变，并发重复构建同一个 key 只会多算一次，不会写坏缓存。
 */
private fun resolveColorScheme(darkTheme: Boolean, palette: ThemePalette): ColorScheme =
    ResolvedSchemes.getOrPut(SchemeKey(darkTheme, palette.id)) {
        buildColorScheme(darkTheme, palette)
    }

/**
 * 预热所有配色的日 / 夜两套方案。
 *
 * 由 App 在启动后的后台线程调用。用户的第一次日夜切换因此只走一次 Map 命中，
 * 不会因为类初始化、对比度提亮循环或大对象分配而卡一帧。
 */
fun warmUpColorSchemes() {
    for (palette in ThemePalette.entries) {
        resolveColorScheme(darkTheme = false, palette = palette)
        resolveColorScheme(darkTheme = true, palette = palette)
    }
}

/** 真正的方案构建：主色按模式改写，表面层级按模式锁定。 */
private fun buildColorScheme(darkTheme: Boolean, palette: ThemePalette): ColorScheme {
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
    return if (darkTheme) {
        colorScheme.withNightSurfaces()
    } else {
        colorScheme.withDaySurfaces()
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
