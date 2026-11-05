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
 * 字号与行高沿用 Material 3 的基线，不在这轮里动：它们是**已经跑在所有页面里**的数值，
 * 改一行就会让几十个页面重新折行。要调字号，改这张表，一次只调一档，再回归验证。
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
        fontSize = 22.sp,
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
 * 只收「跨组件复用、且不是某个槽位改一改」的那几个。页面里一次性的字形微调
 * （封面标题的投影、榜单数字的花体）留原地，别往这里堆 —— 那会把字阶变成杂物间。
 *
 * 这里用普通 object 而不是 CompositionLocal：字阶切换不是运行时的需求
 * （没有「这个子树换一套字号」的场景），做成 `CompositionLocal` 只会让每次读取
 * 多一次订阅。自定义字体接入时，记得连同这里一起改。
 */
object MikomiTextStyles {

    /**
     * 二级页面顶栏标题。
     *
     * 不用 `titleLarge`（22sp）：顶栏只有 54dp 高，两字标题用 22sp 会顶满上下留白，
     * 而且比页面里的一级标题还大，层级是反的。19sp + Bold 是「够醒目但不压人」的那一档。
     */
    val TopBarTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    )
}
