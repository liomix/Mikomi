package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * 字阶。
 *
 * ## 为什么要 15 个槽位全部写出来
 *
 * 之前这里只定义了 `bodyLarge`，其余 14 个槽位继承 `Typography()` 的默认值 —— 看起来
 * 「少写了 14 行」，实际是**这套字号表根本不存在**：想调标题字号，得先知道它现在是
 * Material 的哪一档、多少 sp；想让全站标题重一档，没有地方可以改。于是页面开始自己写
 * `fontSize = 19.sp`，一个字阶就这样散成了 124 处硬编码。
 *
 * 写全之后，这份表就是唯一的字阶来源：调档改这里，页面只负责选槽位。
 *
 * ## 字距为什么全是 0
 *
 * Material 的 `letterSpacing`（0.1~0.5sp）是为拉丁字母调的：小写字母之间需要一点空隙
 * 才不会粘在一起。中文是等宽方块字，额外字距只会让「番剧详情」四个字看起来是散的，
 * 标题尤其明显。所以这里全部归零 —— 这也是 Material 官方 CJK 指南的做法。
 *
 * ## 字号与行高
 *
 * 字号按 Telegram 的骨架校正：它把 16sp 当默认正文（`SharedConfig.fontSize = 16`），
 * 20sp 当主标题，中间 18 / 15 / 14 / 13 / 12 各有明确分工。本表里 `titleLarge` 因此
 * 从 22sp 收到 **20sp** —— 原来那一档比页面里的一级标题还大，层级是反的。
 * 装不进 15 个槽位的 18 / 15 / 13 三档见 [MikomiTextStyles]。
 *
 * **行高不跟 TG**：TG 的行高是西文的紧凑值（约 1.3 倍），中文方块字上下都有留白，
 * 1.3 倍会让两行贴在一起。这里保留 Material 的 1.4~1.5 倍 —— 这是语言差异，
 * 不是设计系统的差异，照搬会导致整站文字发挤。
 */
val Typography = Typography(
    // ---- 展示级：整屏只有一处，用于空态插画旁的巨大数字 ----
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),

    // ---- 标题级：页面标题、区块标题 ----
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),

    // ---- 小标题级：卡片标题、按钮文字、列表行主文案 ----
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),

    // ---- 正文级：简介、评论这类需要连续读的段落 ----
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),

    // ---- 标签级：按钮、标签、徽标。比正文重一档，因为它们通常是短词 ----
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
)

/**
 * M3 十五个槽位装不下的样式。
 *
 * ## 为什么这几档要单独存在
 *
 * Material 的字阶是**层级式**的（display → headline → title → body → label，每级三档，
 * 跨度很大），适合「一屏里只有两三级文字」的营销页；Telegram 的字阶是**场景式**的
 * （20 标题 / 18 区块 / 16 正文 / 15 次级正文 / 14 副标题 / 13 辅助 / 12 最小），
 * 步进只有 1~2sp，因为它一屏里要放下标题、副标题、时间、状态、预览五行不同的字。
 *
 * 本项目是信息密集的列表界面（番剧列表、播放器选集、设置项），更接近后者。
 * 所以 M3 那 15 个槽位按层级保留（大标题、封面标题用它），**中间密集的那几档补在这里** ——
 * 18 / 15 / 13 三档在 TG 的 `setTextSize` 统计里分别是 79 / 180 / 194 次，
 * 都是真实高频需求，而 M3 的字阶里根本没有它们的位置。
 *
 * ## 什么不该进这里
 *
 * 页面里一次性的字形微调（封面标题的投影、榜单数字的花体）留原地。
 * 判别方法：换掉它，是「这个页面看起来不对了」，还是「这类内容看起来不对了」——
 * 后者才该上提。
 */
object MikomiTextStyles {

    /**
     * 二级页面顶栏标题。
     *
     * 20sp Bold，与 Telegram 的 `ActionBar` 标题同档。不用 `titleLarge`（Normal 字重）：
     * 顶栏标题需要压住底下的内容，光靠字号不够。
     */
    val TopBarTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    )

    /**
     * 区块标题：首页分区标题、「相关作品」「制作人员」这类分节名。
     *
     * 18sp Bold。比 [TopBarTitle] 小一档、比正文大一档 —— 它需要「划分段落」，
     * 但不需要像顶栏那样成为整个屏幕的锚点。
     */
    val SectionTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )

    /**
     * 次级正文：简介、评论这类「成段但不必是最重要信息」的文字。
     *
     * 15sp。16 和 14 之间差 2sp，但在这个位置上就是这 1sp 的事 ——
     * 用 16 会和主标题抢注意力，用 14 又跌进「辅助信息」的档位。
     */
    val BodySecondary: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
    )

    /**
     * 辅助说明：更新时间、来源、计数这类「读得到但不需要被记住」的文字。
     *
     * 13sp。`bodySmall`（12sp）在中文下偏小 —— 汉字笔画在 12sp 已经糊成一团，
     * 13sp 是「最小但还能读」的位置，也是 Telegram 用得最多的小字档。
     */
    val Caption: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )
}
