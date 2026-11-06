package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * M3 之外的语义色。
 *
 * ## 为什么不硬塞进 ColorScheme
 *
 * `ColorScheme` 的槽位是 Material 定义好的 31 个，它们回答的是「轮廓用什么色、容器用什么色」。
 * 「评分星是金色」「第三名用铜色」「骨架块用 8.6% 的墨」这些答案它没有槽位可以放，
 * 于是它们此前散落在各个 feature 里 —— 评分星出现过 `#FFC107` 与 `#FFB300` 两个值，
 * 名次金出现过 `#FFD700` 与 `#F2B705` 两个值，同一块屏幕上就会互相打架。
 *
 * 这里补的是 M3 **缺的那一类**：跨页面复用的语义色。它不是第二套 ColorScheme，
 * 目录上先查 `colorScheme`，查不到「这个意思」时才来这里。
 *
 * ## 为什么可以局部覆写
 *
 * 通过 [LocalMikomiColors] 下发（而不是全局单例），任意子树都能换一套 —— 这是从
 * Telegram 的 `Theme.ResourcesProvider` 学来的能力：暗色播放器、地图浮层这类沉浸场景
 * 需要「在这个子树里，评分星更亮、骨架更黑」，而不用为此新增一套全局主题。
 * 常规页面直接读 [MikomiTheme.colors] 即可。
 */

/**
 * 标签色相。
 *
 * 只描述**颜色**，不描述**含义** —— 「主角」对应哪个色相是业务知识，留在调用方
 * （见 `CreditsTab` 的关键词表）；designsystem 只负责「红色标签在日间长什么样、
 * 夜间长什么样」。分开之后，同一个红色可以被关系标签、状态标签、错误标记共用。
 */
enum class MikomiTagHue {
    /** 红：警示、第一主角。 */
    Red,

    /** 蓝：信息、第二主角。 */
    Blue,

    /** 紫：特殊、客串。 */
    Violet,

    /** 琥珀：提示、原作。 */
    Amber,

    /** 珊瑚：次警示、导演。 */
    Coral,

    /** 青：中性偏冷、系列构成。 */
    Teal,

    /** 绿：成功、角色设计。 */
    Green,

    /** 品红：强调、音乐。 */
    Magenta,

    /** 中性：未命中任何语义时的兜底。 */
    Neutral,
}

/**
 * 一枚标签的配色（浅底 + 深字）。
 *
 * 对比度约束：`content` 在 `container` 上不低于 4.5:1，两套模式各自成立 ——
 * 夜间不是把日间色简单压暗，而是重新选的一组「深底 + 亮字」。
 */
@Immutable
data class MikomiTagColor(
    val container: Color,
    val content: Color,
)

/**
 * 名次配色。
 *
 * @param fill 压在封面 / 深色渐变上的金属色。够亮才醒目。
 * @param outline 描边色。浅色封面（天空、雪地）会把金属色糊掉，靠它勾出字形。
 * @param ink 浅色页面上作为**文字**用的颜色。
 *
 * ## ink 为什么这么暗
 *
 * 金属色在白底上只有 1.2~1.6:1，直接当文字读不出来，所以另备一档压暗的。
 * 而「压暗多少」以前是目测的 —— 金的 ink 取 #F2B705，压在 `background`(#F1F1F3) 上只有
 * **1.61:1**，银 2.27、铜 3.05，三个都低于正文门槛。搜索页的排名数字因此一直是「有形状、
 * 看不清」的状态。
 *
 * 现在每一档都按「压在 #F1F1F3 上 ≥ 4.5:1」反算：金 #856500（4.82）、银 #5F6B76（4.83）、
 * 铜 #8A4E17（5.85）。色相保持不变，只是明度压到底 —— 名次的可读性优先于「金属感」，
 * 因为金属感由 [fill] 在封面上承担，[ink] 只负责在浅底上被读出来。
 */
@Immutable
data class MikomiRankColor(
    val fill: Color,
    val outline: Color,
    val ink: Color,
)

/**
 * 压在图像 / 视频上的那一套色。
 *
 * ## 为什么单列一族
 *
 * 这一族的取值**不随主题变**：封面上是白字，白天夜里都是白字；视频的信箱区是黑的，
 * 换成浅色主题它还是黑的。所以它们既不属于 `ColorScheme`，也不该跟着日/夜解析。
 *
 * 但它们也不该是 `Color.White` —— 一旦页面直接写白色，就必然出现「这里 0.7、那里 0.72、
 * 另一处 0.8」的分裂（本轮清理前，全仓有 0.45 / 0.7 / 0.72 / 0.8 / 0.85 / 0.9 六种白，
 * 全部用来表达同样的「次级文字」）。收成三档之后，某个位置该用第几档是可判定的。
 *
 * 布局约定：`content` 是主文字与图标；`contentSecondary` 是副标题、时间、说明；
 * `contentTertiary` 是占位与「有但不强调」的信息。再往下不存在第四档 ——
 * 比 [contentTertiary] 还淡的字在画面上已经读不出来，那不是层次，是丢失信息。
 */
@Immutable
data class MikomiMediaColors(
    /** 主文字与图标：纯白。压在任何封面上都要读得出来。 */
    val content: Color,
    /** 次级文字：副标题、简介、时间。 */
    val contentSecondary: Color,
    /** 三级文字：占位、单位、非活跃刻度。 */
    val contentTertiary: Color,
    /** 压在图上的描边与分隔线（14% 白）：交代边界，不该被注意到。 */
    val outline: Color,
    /** 浮动控件的容器底色（14% 白）：返回按钮、控制条按钮的底。 */
    val surface: Color,
    /** 强调容器（28% 白）：按下态、已开启的开关。 */
    val surfaceProminent: Color,
    /** 底部渐变遮罩的深端（72% 黑）：让白色控件在亮画面上也站得住。 */
    val scrim: Color,
    /** 顶部渐变遮罩的浅端（62% 黑）。 */
    val scrimSoft: Color,
    /** 视频信箱区与播放器底层：纯黑。黑边不该被看成一块内容。 */
    val void: Color,
    /** 图像尚未到达时的兜底深灰：比黑浅一档，看得出「这里会有一张图」。 */
    val backdrop: Color,
    /** 播放器背景渐变的顶端（近黑偏冷）。 */
    val backdropTop: Color,
    /** 播放器背景渐变的底端。 */
    val backdropBottom: Color,
    /** 浅色浮层底板（85% 白）：音量/亮度 HUD —— 暗画面上的深色底板会消失。 */
    val overlayContainer: Color,
    /** 浅色浮层上的内容色：近黑而非纯黑，压在 85% 白上纯黑偏硬。 */
    val overlayContent: Color,
    /** 进度条未填充段（26% 白）。 */
    val track: Color,
    /** 进度条已缓冲段（42% 白）。 */
    val trackBuffered: Color,
)

/**
 * 九个色相的标签色板。
 *
 * ## 为什么是一个字段而不是九个
 *
 * 上一版的 `MikomiColors` 有 `tagRed` / `tagBlue` / … 九个平铺字段，`rankFirst/Second/Third`
 * 又三个。它们是**同一张色板的九个格子**，平铺之后有三个坏处：
 *
 * 1. 加一个色相（比如「靛」）要改 `MikomiColors` 的构造签名、`copy` 的行为、
 *    以及 `resolveMikomiColors` 里那一长串 `if (dark) … else …`；
 * 2. `copy` 出来做局部覆写时，九个字段里改哪个是「九个选择」而不是「一张表」；
 * 3. 语义色对象一屏看不完，`MikomiColors` 的字段数会随着「再加一个语义色」一直涨。
 *
 * 收成一张表之后，加色相只动 [MikomiTagHue] 与这张表，`MikomiColors` 的签名不动。
 * 用 `Map` 而不是九个字段，是拿一点点查表开销换「结构不随内容膨胀」——
 * 一次主题解析只建一次表，之后每次读取都是一次哈希。
 */
@Immutable
data class MikomiTagColors(
    private val byHue: Map<MikomiTagHue, MikomiTagColor>,
) {
    init {
        // 漏一个色相就让它在解析主题时立刻炸，而不是等到某个用户翻到那个标签才
        // `NoSuchElementException`。色板是编译期常量表，漏项属于写错，不属于运行时状况。
        val missing = MikomiTagHue.entries.filterNot { byHue.containsKey(it) }
        require(missing.isEmpty()) { "标签色板缺少色相：$missing" }
    }

    /** 按色相取一套配色。表里一定有全部色相（构造时校验），所以不会抛 `NoSuchElement`。 */
    operator fun get(hue: MikomiTagHue): MikomiTagColor = byHue.getValue(hue)
}

/**
 * 前三名的配色。
 *
 * 只到三名：第 4 名起的样式完全不同（中性灰 + 投影，而不是金属色 + 描边），
 * 调用方本来就要走另一条分支 —— 见 [of] 返回可空而不是给一个默认值。
 */
@Immutable
data class MikomiRanks(
    val first: MikomiRankColor,
    val second: MikomiRankColor,
    val third: MikomiRankColor,
) {
    /** 按名次取配色；三名之后返回 `null`。 */
    fun of(place: Int): MikomiRankColor? = when (place) {
        1 -> first
        2 -> second
        3 -> third
        else -> null
    }
}

/**
 * M3 之外的全部语义色。
 *
 * 由 [MikomiTheme] 按「配色 + 日/夜」解析一次并缓存，通过 [LocalMikomiColors] 下发。
 * 值对象本身不可变，`copy` 出来即可在子树里覆写其中几项。
 *
 * ## 字段是按「族」分组的
 *
 * 上一版把所有语义色平铺成 22 个字段（九个标签、三个名次各占一格），于是每加一个
 * 语义色就把这个类撑宽一行。现在按族归拢：[tags] 是一张九格色板、[ranks] 是前三名、
 * [media] 是压在图像上的一整套前景色。顶层只剩「单值」的语义色。
 *
 * 新增颜色的判断顺序：
 * 1. `MaterialTheme.colorScheme` 里有对应槽位 → 不要往这里加；
 * 2. 属于某个已有的族（又一个标签色相、又一个名次） → 加进那个族，别在顶层开口子；
 * 3. 确实是新的单值语义（像「搜索高亮」） → 才加顶层字段，并在 `TokenGallery` 登记。
 */
@Immutable
data class MikomiColors(
    /**
     * 评分与收藏共用的金色。
     *
     * **只允许压在图像上**（封面、banner、Video 遮罩）：这个金在白底上只有 1.63:1，
     * 在 `background`(#F1F1F3) 上 1.45:1 —— 当文字用是读不出来的。
     * 目前三处调用（详情头部、首页 banner、搜索封面）都压在图上，符合这个约束；
     * 将来若要在浅色卡片上显示评分，别用它，用 `onSurface` 配这个金色图标，
     * 或者先补一档 `ratingInk`（做法同 [MikomiRankColor.ink]）。
     *
     * 这条约束现在**有机器在查**：`TokenGallery` 的对比度页会把「金压白」算出来并标红，
     * 所以它不是靠自觉。
     */
    val rating: Color,
    /** 九个色相的标签色板。 */
    val tags: MikomiTagColors,
    /** 前三名的金属色。 */
    val ranks: MikomiRanks,
    /** 骨架块底色：半透明墨色，与页面底色同源。 */
    val skeletonBase: Color,
    /** 骨架扫光亮带：比底色更亮一档。 */
    val skeletonSweep: Color,
    /** 压在图像 / 视频上的整套前景色。 */
    val media: MikomiMediaColors,
    /** 预览画布：展示「真实世界里长什么样」的东西（应用图标、主题色卡）时的固定浅底。 */
    val previewCanvas: Color,
) {
    /** 按色相取标签配色。 */
    fun tag(hue: MikomiTagHue): MikomiTagColor = tags[hue]

    /** 按名次取名次配色；三名之后返回 `null`（用中性色）。 */
    fun rank(place: Int): MikomiRankColor? = ranks.of(place)
}

/*
 * ---- 原始值（primitive）----
 *
 * 上面那层的每个字段都从这里取色，改色只改这一段。命名按色相 + 明度用途，
 * 不带任何页面名 —— 一旦叫 `creditsRed`，下一个用它的人就得先猜它凭什么属于 credits。
 */

/** 评分金：Material Amber 500。 */
private val Gold = Color(0xFFFFC107)

/**
 * 标签日间：浅底 + 深字，全部 ≥ 4.5:1。
 *
 * 写成一张表而不是九个 `val`，是因为「九个色相 × 两种模式」本质就是 18 行数据。
 * 平铺之后，加一个色相要在文件里找三个地方（日间、夜间、`resolve` 的分支），
 * 而表里只加一行。
 */
private val TagDay: Map<MikomiTagHue, MikomiTagColor> = mapOf(
    MikomiTagHue.Red to MikomiTagColor(Color(0xFFFFE0E0), Color(0xFFB3261E)),
    MikomiTagHue.Blue to MikomiTagColor(Color(0xFFD6E4FF), Color(0xFF1A56C7)),
    MikomiTagHue.Violet to MikomiTagColor(Color(0xFFE8DFFF), Color(0xFF5A2FA6)),
    MikomiTagHue.Amber to MikomiTagColor(Color(0xFFFFE8C7), Color(0xFF8A4B00)),
    MikomiTagHue.Coral to MikomiTagColor(Color(0xFFFFD9D2), Color(0xFFA52714)),
    MikomiTagHue.Teal to MikomiTagColor(Color(0xFFD0F0E8), Color(0xFF0B6B5A)),
    MikomiTagHue.Green to MikomiTagColor(Color(0xFFDDF2D2), Color(0xFF2E6B1E)),
    MikomiTagHue.Magenta to MikomiTagColor(Color(0xFFF5DDF5), Color(0xFF7A2B7A)),
    MikomiTagHue.Neutral to MikomiTagColor(Color(0xFFE8EAED), Color(0xFF4A5568)),
)

/**
 * 标签夜间：深底 + 亮字。
 *
 * 不是把日间色压暗 —— 浅底 #FFE0E0 压在纯黑页面上是一块发光板，会盖过内容本身。
 * 这里每个色相各自取「同色相的深色容器 + 同色相的亮色文字」，
 * 容器亮度落在 surface(#181818) 附近，只靠色相区分，不靠亮度抢眼。
 */
private val TagNight: Map<MikomiTagHue, MikomiTagColor> = mapOf(
    MikomiTagHue.Red to MikomiTagColor(Color(0xFF4A2320), Color(0xFFFFB4AB)),
    MikomiTagHue.Blue to MikomiTagColor(Color(0xFF1B3A5C), Color(0xFFA8C8FF)),
    MikomiTagHue.Violet to MikomiTagColor(Color(0xFF33255C), Color(0xFFD0BCFF)),
    MikomiTagHue.Amber to MikomiTagColor(Color(0xFF4A3210), Color(0xFFFFD79A)),
    MikomiTagHue.Coral to MikomiTagColor(Color(0xFF4A2820), Color(0xFFFFB59E)),
    MikomiTagHue.Teal to MikomiTagColor(Color(0xFF123C36), Color(0xFF8EE0D0)),
    MikomiTagHue.Green to MikomiTagColor(Color(0xFF243C18), Color(0xFFB6E39A)),
    MikomiTagHue.Magenta to MikomiTagColor(Color(0xFF42204A), Color(0xFFF0B6F5)),
    MikomiTagHue.Neutral to MikomiTagColor(Color(0xFF2C2C31), Color(0xFFC7C7CC)),
)

/** 名次：金属填充、描边、页面文字色三档。 */
private val RankFirst = MikomiRankColor(
    fill = Color(0xFFFFD700),
    outline = Color(0xFF4A3700),
    // 压在 #F1F1F3 上 4.82:1，压在纯白卡片上 5.44:1。
    ink = Color(0xFF856500),
)
private val RankSecond = MikomiRankColor(
    fill = Color(0xFFC9D3DE),
    outline = Color(0xFF39414B),
    // 4.83:1 / 5.45:1
    ink = Color(0xFF5F6B76),
)
private val RankThird = MikomiRankColor(
    fill = Color(0xFFD98719),
    outline = Color(0xFF4B2A00),
    // 5.85:1 / 6.60:1
    ink = Color(0xFF8A4E17),
)

/** 前三名的色板。不随日 / 夜变 —— 金属色压在封面上，两种模式下都成立。 */
private val RankColors = MikomiRanks(RankFirst, RankSecond, RankThird)

/** 骨架底色透明度：浅色 8.6% 黑（Telegram `setAlpha(listSelector, 22)`）。 */
private const val SkeletonDayBaseAlpha = 22f / 255f

/** 骨架亮带透明度：浅色 3.5% 黑。 */
private const val SkeletonDaySweepAlpha = 9f / 255f

/** 骨架底色透明度：夜间 9.4% 白。 */
private const val SkeletonNightBaseAlpha = 24f / 255f

/** 骨架亮带透明度：夜间 12% 白。 */
private const val SkeletonNightSweepAlpha = 31f / 255f

/**
 * 压在图像 / 视频上的颜色族。
 *
 * **这个族是常量，不是主题色**：它描述的是「白色压在封面上」这件事，与日/夜无关。
 * 因此它同时以两种方式暴露：
 *
 * - `MikomiTheme.colors.media` —— 常规读法，并且可以在子树里用 `copy` 覆写
 *   （例如把某个播放主题整体压暖）；
 * - `MikomiMedia` —— 非组合位置的读法。遮罩渐变、文字阴影这类值写在顶层 `val` 里，
 *   组合期一次都不重新分配，读不到 `CompositionLocal`。
 */
val MikomiMedia: MikomiMediaColors = MikomiMediaColors(
    content = Color.White,
    contentSecondary = Color.White.copy(alpha = 0.76f),
    contentTertiary = Color.White.copy(alpha = 0.45f),
    outline = Color.White.copy(alpha = 0.14f),
    surface = Color.White.copy(alpha = 0.14f),
    surfaceProminent = Color.White.copy(alpha = 0.28f),
    scrim = Color.Black.copy(alpha = 0.72f),
    scrimSoft = Color.Black.copy(alpha = 0.62f),
    void = Color.Black,
    backdrop = Color(0xFF26262A),
    backdropTop = Color(0xFF14161B),
    backdropBottom = Color(0xFF05060A),
    overlayContainer = Color.White.copy(alpha = 0.85f),
    overlayContent = Color.Black.copy(alpha = 0.88f),
    track = Color.White.copy(alpha = 0.26f),
    trackBuffered = Color.White.copy(alpha = 0.42f),
)

/** 应用图标预览底色：与真实启动图标的白底一致，所以不能跟主题走。 */
private val PreviewCanvas = Color(0xFFFEFEFE)

/**
 * 绘制阴影用的恒定黑。
 *
 * 阴影不随主题变 —— 夜间的阴影也还是黑的（夜里看到的阴影是「没有光」，不是「白色的暗」）。
 * 单独做成顶层常量而不是挂在 [MikomiColors] 上，是因为它常被用在**非组合**的位置：
 * `TextStyle(shadow = Shadow(...))`、`Modifier.shadow(ambientColor = ...)` 这类
 * 对象初始化在组合之外，读不到 `CompositionLocal`。
 */
val MikomiShadowColor: Color = Color.Black

/**
 * 禁用态的淡化系数。
 *
 * 值取自 M3（容器 0.12 / 内容 0.38）。这两个数不是随手取的：0.12 让容器「几乎消失
 * 但仍占位」，0.38 让内容保住可辨识度，再低就分不清「禁用」和「没画出来」。
 *
 * ## 为什么在颜色层，不在动效层
 *
 * 它此前挂在 `MikomiMotion.Disabled` 下 —— 「禁用态」最早是从按压动效那一族里分出来的。
 * 但这两个值**一次都不动**：它们是「拿基色淡化」的颜色规则，与时长、曲线无关。
 * 放在 `Motion` 里，找禁用态的人不会去那里翻，改动画的人又会以为它们跟着动效走。
 *
 * ## 用它，别用整体 alpha
 *
 * 组件在自己的 `enabled` 分支里用这两个值。**不要**用 `Modifier.alpha()` 整体压暗：
 * 整体 alpha 会连带压掉投影与描边，看起来像「半透明贴纸」；M3 的语义是
 * 「容器转中性灰、内容变淡」，两者是分开的两级。
 */
object MikomiAlpha {
    /** 禁用态的容器淡化：叠在 `onSurface` 上当作底色。 */
    const val DisabledContainer: Float = 0.12f

    /** 禁用态的内容淡化：文字与图标。 */
    const val DisabledContent: Float = 0.38f
}

/**
 * 解析一套扩展色。
 *
 * 纯函数、不依赖组合运行时，可以从任意线程调用（`warmUpColorSchemes` 就在后台线程上跑）。
 * 骨架色需要 [ColorScheme] 是因为它取 `onSurface` 的色相：夜里是白墨、白天是黑墨，
 * 而「夜不夜」不能只看传入的布尔量 —— 主题里可能配了深色的日间方案。
 */
internal fun resolveMikomiColors(scheme: ColorScheme, dark: Boolean): MikomiColors {
    val ink = scheme.onSurface
    return MikomiColors(
        rating = Gold,
        tags = MikomiTagColors(if (dark) TagNight else TagDay),
        ranks = RankColors,
        skeletonBase = ink.copy(
            alpha = if (dark) SkeletonNightBaseAlpha else SkeletonDayBaseAlpha,
        ),
        skeletonSweep = ink.copy(
            alpha = if (dark) SkeletonNightSweepAlpha else SkeletonDaySweepAlpha,
        ),
        media = MikomiMedia,
        previewCanvas = PreviewCanvas,
    )
}

/**
 * 无主题时的兜底。
 *
 * 组件应该总是跑在 [MikomiTheme] 之下；但预览、单测、或者某个页面忘了包主题时，
 * 读色不该直接抛异常（Telegram 的 `getColorOrDefault` 就是这个思路）。
 * 兜底用浅色的默认方案，视觉上等同于 M3 默认主题。
 */
private val FallbackColors: MikomiColors by lazy {
    resolveMikomiColors(lightColorScheme(), dark = false)
}

/**
 * 当前子树生效的扩展色。
 *
 * 常规读取请用 [MikomiTheme.colors]；只有需要**局部覆写**时才直接用它：
 *
 * ```kotlin
 * // 播放器浮层：在这个子树里评分星更亮、骨架更黑
 * val immersive = MikomiTheme.colors.copy(rating = Color(0xFFFFE082))
 * CompositionLocalProvider(LocalMikomiColors provides immersive) { PlayerHud() }
 * ```
 */
val LocalMikomiColors = staticCompositionLocalOf { FallbackColors }
