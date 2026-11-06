package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/*
 * ---- 基准方案 ----
 *
 * M3 的 `lightColorScheme()` / `darkColorScheme()` 不是「空白骨架」，它带着一整套紫：
 * `secondaryContainer` = #E8DEF8、`tertiary` = #7D5260、`outline` = #79747E、
 * `inverseSurface` = #313033。之前这里只覆盖了 primary 一族与表面层级，
 * 于是那些紫每隔一阵就从某个 M3 组件里漏出来一次 —— FilterChip 的选中态、
 * FilledTonalButton 的底、夜间的 outline 描边，都曾经在页面上出现过。
 *
 * 现在基准方案只当槽位骨架用，颜色分为三份来源，一个都不留：
 * - primary 一族 ← [ThemePalette]（用户可切换的五套配色）；
 * - secondary / tertiary 一族 ← 中性色（本项目只有一个强调色，就是主色）；
 * - 表面 / 描边 ← 下面的 `withDaySurfaces` / `withNightSurfaces`。
 */
private val BaseLightColorScheme = lightColorScheme()
private val BaseDarkColorScheme = darkColorScheme()

/** 预设配色下 primary 上的前景色：四种预设主色都偏亮，统一用黑字以满足 4.5:1 对比度。 */
private val PaletteOnPrimary = Color(0xFF000000)

/**
 * 与配色无关、只随日 / 夜翻转的中性色。
 *
 * 这些槽位以前是 8 个散落的 `if (darkTheme) X else Y`，读的时候得先在两行里配对才能知道
 * 「夜间这一档是什么」。收成两份实例后，日 / 夜的差异一眼可见，也只有一个地方会写错。
 *
 * 为什么不是「一套色换明度」：M3 的 `onSecondary` / `onTertiary` 在日间要白字、夜间要深字，
 * 两者不是同一条色阶上的两个点，靠插值算不出来。
 */
private class ModeNeutrals(
    /** 第二强调色：中性灰，不进任何主色作用域，只用来区分层级。 */
    val secondary: Color,
    val onSecondary: Color,
    /** 第三强调色：比 secondary 更淡的中性，仅用于「第三层提示」。 */
    val tertiary: Color,
    val onTertiary: Color,
    /** 中性容器：FilterChip 未选中底、分段控件底。 */
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    /** 反色面：Snackbar 一类「浮在页面之上的深色条」用它。 */
    val inverseSurface: Color,
    val inverseOnSurface: Color,
)

/** 中性容器上的前景（两种模式共用同一个近黑 / 近白）。 */
private val OnNeutralDay = Color(0xFF1A1A1C)
private val OnNeutralNight = Color(0xFFEDEDF0)

private val DayNeutrals = ModeNeutrals(
    secondary = Color(0xFF5C5C60),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF76767A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4E4E7),
    onTertiaryContainer = OnNeutralDay,
    inverseSurface = Color(0xFF2E2E32),
    inverseOnSurface = Color(0xFFF5F5F7),
)

private val NightNeutrals = ModeNeutrals(
    secondary = Color(0xFFC8C8CC),
    onSecondary = OnNeutralDay,
    tertiary = Color(0xFF9A9A9F),
    onTertiary = OnNeutralDay,
    tertiaryContainer = Color(0xFF303035),
    onTertiaryContainer = OnNeutralNight,
    inverseSurface = Color(0xFFEDEDF0),
    inverseOnSurface = OnNeutralDay,
)

/** 夜间背景：纯黑。 */
private val NightBackground = Color(0xFF000000)

/** 夜间的卡片 / 面板底色。 */
private val NightSurface = Color(0xFF181818)

/** 夜间正文：中性近白。M3 默认的 #E6E1E5 带紫，见 [withNightSurfaces] 的说明。 */
private val NightOnSurface = Color(0xFFEDEDF0)

/** 夜间次级文字：中性灰，压在 #181818 上 8.6:1。 */
private val NightOnSurfaceVariant = Color(0xFFB4B4B9)

/**
 * 夜间表面层级：背景纯黑、卡片 #181818，仅最高层级略提亮，
 * 保证卡片在纯黑背景上仍有边界，又不会出现大块浅灰。
 */
private fun ColorScheme.withNightSurfaces(): ColorScheme = copy(
    background = NightBackground,
    surface = NightSurface,
    // 正文与次级文字也必须显式写：M3 darkColorScheme 的默认值是 #E6E1E5 / #CAC4D0，
    // 两个都带一点点紫（R > G > B）。压在纯黑上看不出来，但一旦和中性灰的卡片并排，
    // 「白得发暖」就会显脏 —— 这与日间把 onSurface 从 #1C1B1F 改成纯黑是同一个理由。
    onBackground = NightOnSurface,
    onSurface = NightOnSurface,
    onSurfaceVariant = NightOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = NightSurface,
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF212121),
    surfaceContainerHighest = Color(0xFF262626),
    surfaceVariant = Color(0xFF2A2A2A),
    // 描边：M3 默认的 #938F99 / #49454F 都带紫，压在纯黑上会读成「脏灰」。
    outline = Color(0xFF8A8A8F),
    outlineVariant = Color(0xFF3A3A3E),
    // 这两个是 M3 1.2 起的槽位，默认值同样带紫；用到的组件少，但漏了就是漏了。
    surfaceDim = Color(0xFF0D0D0D),
    surfaceBright = Color(0xFF262626),
)

/**
 * 日间表面层级：中性浅灰底 + 纯白卡片。
 *
 * M3 默认的 background(#FFFBFE) 与 surfaceContainerLow(#F7F2FA) 几乎是同一个白，
 * 设置页的卡片因此「贴」在背景上看不出边界。这里把背景压到中性浅灰、卡片提到纯白，
 * 与夜间「深底稍亮卡片」的思路保持一致，卡片在两种模式下都有清晰边界。
 *
 * 容器色、描边色一并去掉了 M3 默认的紫调（原值是 `#F5EFF9` / `#E6E0E9` / `#DDD7E1` /
 * `#CAC4D0`）—— 底色中性化之后，紫调的 Tab 底、输入框描边会明显「串色」。
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
    // 次级文字：M3 默认 #49454F 也带紫调（压在白上是看不出，但和下面对不上的描边同屏就显脏）。
    onSurfaceVariant = Color(0xFF4A4A4F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF7F7F9),
    surfaceContainerHigh = Color(0xFFE8E8EB),
    surfaceContainerHighest = Color(0xFFE1E1E5),
    surfaceVariant = Color(0xFFE9E9EC),
    outline = Color(0xFF76767A),
    outlineVariant = Color(0xFFC7C7CC),
    surfaceDim = Color(0xFFE1E1E5),
    surfaceBright = Color(0xFFFFFFFF),
)

/**
 * 应用主题。
 *
 * 这是设计系统的唯一入口：颜色（[ColorScheme] + [MikomiColors]）、字号（[Typography]）、
 * 形状（[MikomiShapes]）都在这里下发。页面和组件只需要读 `MaterialTheme.*` /
 * [MikomiTheme.colors] / [MikomiTextStyles]，不自己 new 一套数值。
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
    // 配色 id 只在设置页变化，没必要每次重组都走一遍 fromId 的字符串比较。
    val palette = remember(colorSchemeId) { ThemePalette.fromId(colorSchemeId) }
    // 按（模式 + 配色）取缓存：切日夜时这里是一次数组下标，不再重算方案。
    val theme = remember(darkTheme, palette) { resolveTheme(darkTheme, palette) }
    // 按压层：中性墨色 + Telegram 的 9.8%，理由见 `Indication.kt`。
    val rippleColor = remember(theme) {
        theme.colorScheme.onBackground.copy(alpha = MikomiMotion.Pressed.OverlayAlpha)
    }
    val indication = rememberMikomiIndication(rippleColor)

    MaterialTheme(
        colorScheme = theme.colorScheme,
        typography = Typography,
        shapes = MikomiShapes,
    ) {
        // MaterialTheme 本身不设置 LocalContentColor（默认黑色），
        // 只有 Surface 内部才会按背景推导内容色。页面中直接放在 Box/Column 里的
        // Text、Icon 因此会一直是黑色（夜间不可读）。这里统一兜底为 onBackground，
        // Surface 内部仍会用自己的 contentColor 覆盖。
        //
        // LocalIndication 一并在这里下发：这样全仓的 clickable / Surface(onClick)
        // 都用同一套按压反馈，调用点不必逐个配置。
        CompositionLocalProvider(
            LocalContentColor provides theme.colorScheme.onBackground,
            LocalMikomiColors provides theme.colors,
            LocalMikomiRippleColor provides rippleColor,
            LocalIndication provides indication,
            content = content,
        )
    }
}

/**
 * 设计系统的取值入口。
 *
 * 只放「必须有主题才能读到」的东西；间距、圆角、动效都是编译期常量，
 * 直接用 [MikomiSpacing] / [MikomiRadius] / [MikomiMotion] 即可，不必经过这里。
 */
object MikomiTheme {

    /**
     * M3 之外的语义色（评分金、标签色板、名次色、骨架墨色）。
     *
     * 目录上先查 `MaterialTheme.colorScheme`，那里没有对应的「意思」时才来这里。
     * 需要在某个子树里换一套（沉浸页、播放器浮层）时，直接覆写 [LocalMikomiColors]：
     *
     * ```kotlin
     * val immersive = MikomiTheme.colors.copy(rating = Color(0xFFFFE082))
     * CompositionLocalProvider(LocalMikomiColors provides immersive) { PlayerHud() }
     * ```
     */
    val colors: MikomiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMikomiColors.current
}

/**
 * 一套解析完成的主题：M3 配色 + 扩展色。
 *
 * 两者必须一起解析、一起缓存 —— 扩展色里的骨架墨色取自 [ColorScheme.onSurface]，
 * 分开算就会出现「配色换了、骨架还是上一套的墨」这种半新半旧的状态。
 */
private class ResolvedTheme(
    val colorScheme: ColorScheme,
    val colors: MikomiColors,
)

/**
 * 已解析方案的缓存，槽位 = `palette.ordinal * 2 + (深色 ? 1 : 0)`。
 *
 * 一个方案要付三笔账：`darkColorScheme()` / `lightColorScheme()` 的首次类初始化、夜间主色为
 * 凑对比度做的多次 `lerp` + `luminance` 循环、以及三次 `ColorScheme.copy`。
 * 单看都是微秒级，但它们全落在「点下日夜开关」那一帧上，冷启动后第一次切换就会被感知成顿挫。
 * 缓存之后同一套方案只算一次，App 启动时再用 [warmUpColorSchemes] 把账提前付掉。
 *
 * 用定长数组而不是 `Map<String, …>`：方案数是编译期已知的（5 色 × 2 模式），
 * 键只是一对下标，没必要为它造一个 key 类再算字符串哈希 —— 那笔开销正好也在切换那一帧上。
 *
 * 线程安全：写入的是不可变对象（字段全是 `val`），并发重复构建同一个槽位最多多算一次、
 * 结果等价；读者要么看到 null 自己去建，要么看到一套完整的方案，不会看到半成品。
 */
private val ResolvedThemes = arrayOfNulls<ResolvedTheme>(ThemePalette.entries.size * 2)

/**
 * 取（必要时构建）一套可直接交给 `MaterialTheme` 的完整主题。
 *
 * 纯函数，不依赖 Compose 运行时，可以从任意线程调用 —— 预热正是跑在后台线程上的。
 */
private fun resolveTheme(darkTheme: Boolean, palette: ThemePalette): ResolvedTheme {
    val slot = palette.ordinal * 2 + if (darkTheme) 1 else 0
    ResolvedThemes[slot]?.let { return it }
    val colorScheme = buildColorScheme(darkTheme, palette)
    val resolved = ResolvedTheme(
        colorScheme = colorScheme,
        colors = resolveMikomiColors(colorScheme, dark = darkTheme),
    )
    ResolvedThemes[slot] = resolved
    return resolved
}

/**
 * 预热所有配色的日 / 夜两套方案。
 *
 * 由 App 在启动后的后台线程调用。用户的第一次日夜切换因此只走一次 Map 命中，
 * 不会因为类初始化、对比度提亮循环或大对象分配而卡一帧。
 */
fun warmUpColorSchemes() {
    for (palette in ThemePalette.entries) {
        resolveTheme(darkTheme = false, palette = palette)
        resolveTheme(darkTheme = true, palette = palette)
    }
}

/** 真正的方案构建：主色按模式改写，中性色与表面层级按模式锁定。 */
private fun buildColorScheme(darkTheme: Boolean, palette: ThemePalette): ColorScheme {
    // 所有配色（含默认蓝色）都用预设主色覆盖基础方案，其余语义色保持不变。
    // 主色在日 / 夜保持同一色值，夜间版本自动提亮到可读对比度；
    // 容器色与中性色按模式区分，避免深色界面出现大块亮色。
    val neutrals = if (darkTheme) NightNeutrals else DayNeutrals
    // 容器在两种模式下都取「当前模式的主色容器」：选中态因此始终和主色同族。
    val primaryContainer = if (darkTheme) palette.darkPrimaryContainer else palette.lightPrimaryContainer
    val onPrimaryContainer =
        if (darkTheme) palette.darkOnPrimaryContainer else palette.lightOnPrimaryContainer

    val colorScheme = (if (darkTheme) BaseDarkColorScheme else BaseLightColorScheme).copy(
        primary = if (darkTheme) palette.lightPrimary.forNight() else palette.lightPrimary,
        onPrimary = PaletteOnPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        // 次要色与容器：容器一律取主色容器，让「选中态」在整套主题里长得一致；
        // 纯色（secondary / tertiary）取中性，避免出现第二个强调色。
        secondary = neutrals.secondary,
        onSecondary = neutrals.onSecondary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = neutrals.tertiary,
        onTertiary = neutrals.onTertiary,
        tertiaryContainer = neutrals.tertiaryContainer,
        onTertiaryContainer = neutrals.onTertiaryContainer,
        // 反色面：Snackbar 一类「浮在页面之上的深色条」用它，别让 M3 的 #313033 紫调漏出来。
        inverseSurface = neutrals.inverseSurface,
        inverseOnSurface = neutrals.inverseOnSurface,
        inversePrimary = palette.lightPrimary,
        // 色调高度（tonal elevation）的着色源：不写就取 M3 默认的紫。
        surfaceTint = palette.lightPrimary,
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
 *
 * 比值直接用 [contrastRatio] 算，不再本地复写一份「对黑」的公式 ——
 * 两处各写一遍的话，改了门槛或改了合成方式就会有一处悄悄过期。
 */
private fun Color.forNight(): Color {
    var color = this
    var step = 0
    while (color.contrastRatio(Color.Black) < NIGHT_MIN_CONTRAST && step < MAX_LIGHTEN_STEPS) {
        color = lerp(color, Color.White, LIGHTEN_STEP)
        step++
    }
    return color
}

/** 夜间主色的最低可读对比度（纯黑背景）。 */
private const val NIGHT_MIN_CONTRAST = 3.8f

/** 单次提亮的白色混合比例与最大步数（避免过度发白）。 */
private const val LIGHTEN_STEP = 0.08f
private const val MAX_LIGHTEN_STEPS = 12
