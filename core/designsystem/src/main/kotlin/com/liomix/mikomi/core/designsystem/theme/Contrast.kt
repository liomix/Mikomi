package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/*
 * 对比度：把「可读性」从注释里的一条约束，变成能算出答案的东西。
 *
 * ## 为什么需要它
 *
 * `Colors.kt` 的注释里原本写着两句话：「标签 content 在 container 上不低于 4.5:1」
 * 和「评分金只允许压在图像上」。这两句都是**结论**，不是**机制** ——
 * 写下来之后没有任何东西在执行它们。名次 ink 就是活生生的例子：
 * 注释说「金属色当文字读不出来所以另备一档压暗的」，而那一档本身压在页面底色上只有
 * 1.61:1，比它想避免的问题还严重，整整一年没人发现。
 *
 * 所以这里提供三样东西：
 *
 * 1. [contrastRatio] —— 两个颜色之间的 WCAG 比值，纯函数；
 * 2. [MikomiContrast] —— 门槛常量，别在调用点写 4.5/3.0 这种魔法数；
 * 3. [auditContrast] —— **一套主题里必须成立的全部配对**，返回实测值。
 *
 * ## 谁在跑它
 *
 * - `TokenGallery` 的对比度一节：切换日/夜、切换五套配色，21 行数字跟着变，不达标标红。
 * - `TokenGallery` 的 `@Preview`：不达标直接让预览失败（`check`），
 *   这样「改了色值导致不可读」在 IDE 里就炸，而不是等用户抱怨。
 * - `./gradlew contrastAudit`：不经过 Compose，直接解析源码里的色值再算一遍。
 *
 * 第三条是刻意的重复：Gradle 那边验的是**源码里写的值**（防手动改色改错），
 * Kotlin 这边验的是**解析后的主题**（防解析逻辑改错，比如夜间提亮循环的阈值）。
 * 输入不同，不构成两份真相。
 */

/**
 * 对比度门槛（WCAG 2.1 AA）。
 *
 * 只收三档，且都带明确的使用条件 —— 多一档就会出现「这里该用 3 还是 4.5」的犹豫。
 */
object MikomiContrast {
    /** 正文与小于 18sp 的文字。低于它的文字读起来吃力，不是「设计风格」。 */
    const val Body: Float = 4.5f

    /** 大号文字（≥18sp，或 ≥14sp 且加粗）。 */
    const val Large: Float = 3.0f

    /** 非文本：图标、描边、进度条、图表这类「有形状就够了」的元素。 */
    const val NonText: Float = 3.0f
}

/**
 * 两个颜色的对比度（WCAG relative luminance 比值，范围 1.0 ~ 21.0）。
 *
 * 半透明色必须先合成到它的实际底色上（[compositeOver]），否则算出来的值没有意义 ——
 * `luminance()` 只看 RGB，会把 50% 白当成纯白来算。这一点在媒体族（白 76%、黑 72%）
 * 上尤其容易错，所以 [auditContrast] 里每一对都显式合成。
 */
fun Color.contrastRatio(other: Color): Float {
    val a = luminance()
    val b = other.luminance()
    val hi = max(a, b)
    val lo = min(a, b)
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * 一条对比度断言。
 *
 * @param name 人话描述，报告里直接显示 —— 别写 `tagRed.content`，要写「红色标签的文字」。
 * @param foreground 前景色（已经合成过透明度）。
 * @param background 背景色（已经合成过透明度）。
 * @param min 门槛，取 [MikomiContrast] 里的档位。
 */
@Immutable
data class ContrastCheck(
    val name: String,
    val foreground: Color,
    val background: Color,
    val min: Float,
) {
    /** 实测比值。 */
    val ratio: Float get() = foreground.contrastRatio(background)

    /** 是否达标。 */
    val passes: Boolean get() = ratio >= min
}

/**
 * 一套主题里必须成立的全部配对。
 *
 * 判据是「这处文字/图标有没有可能落在那个底色上」。只列**实际存在的组合**，
 * 不穷举矩阵 —— `onSurface` 压 `primaryContainer` 这种组合页面里不会出现，
 * 列进来只会让人以为设计系统在管一些它管不到的事。
 *
 * 媒体族（压在图像上的白字）不在其中：它们的背景是任意封面，对比度由遮罩与文字阴影
 * 保证，不是靠色值本身。唯一例外是 HUD 的浅色浮层，那一对确有色值关系，所以列在最后。
 */
fun auditContrast(colors: MikomiColors, scheme: ColorScheme): List<ContrastCheck> {
    val checks = mutableListOf<ContrastCheck>()

    // ---- 正文：页面与卡片的两种底色 ----
    checks += ContrastCheck(
        name = "正文压页面底色",
        foreground = scheme.onBackground,
        background = scheme.background,
        min = MikomiContrast.Body,
    )
    checks += ContrastCheck(
        name = "正文压卡片",
        foreground = scheme.onSurface,
        background = scheme.surface,
        min = MikomiContrast.Body,
    )
    checks += ContrastCheck(
        name = "次级文字压卡片",
        foreground = scheme.onSurfaceVariant,
        background = scheme.surface,
        min = MikomiContrast.Body,
    )

    // ---- 描边：只需要「看得出有边界」 ----
    checks += ContrastCheck(
        name = "描边压页面底色",
        foreground = scheme.outline,
        background = scheme.background,
        min = MikomiContrast.NonText,
    )
    checks += ContrastCheck(
        name = "描边压卡片",
        foreground = scheme.outline,
        background = scheme.surface,
        min = MikomiContrast.NonText,
    )

    // ---- 主色一族：按钮、选中态、Snackbar ----
    checks += ContrastCheck(
        name = "主色按钮上的文字",
        foreground = scheme.onPrimary,
        background = scheme.primary,
        min = MikomiContrast.Body,
    )
    checks += ContrastCheck(
        name = "主色容器上的文字",
        foreground = scheme.onPrimaryContainer,
        background = scheme.primaryContainer,
        min = MikomiContrast.Body,
    )
    // 刻意**不**审计「主色图标压在卡片上 ≥ 3:1」：主色是用户选的四套预设，
    // 浅色系（绿 #16BF6E 压白 2.41、橙 #E8760C 2.99）本来就到不了 3:1 ——
    // 这不是配置错误，是「品牌色优先于图标对比度」这个取舍。真把它列进来，
    // 唯一的出路是改预设主色，而那属于另一件事（改品牌）。
    checks += ContrastCheck(
        name = "反色条上的文字",
        foreground = scheme.inverseOnSurface,
        background = scheme.inverseSurface,
        min = MikomiContrast.Body,
    )

    // ---- 标签：九个色相 × 当前模式 ----
    for (hue in MikomiTagHue.entries) {
        val tag = colors.tag(hue)
        checks += ContrastCheck(
            name = "标签「$hue」的文字",
            foreground = tag.content,
            background = tag.container,
            min = MikomiContrast.Body,
        )
    }

    // ---- 名次文字：只在页面上当文字用，所以对两种页面底色各验一次 ----
    for (place in 1..3) {
        val rank = colors.rank(place) ?: continue
        checks += ContrastCheck(
            name = "第 $place 名的文字压页面底色",
            foreground = rank.ink,
            background = scheme.background,
            min = MikomiContrast.Body,
        )
        checks += ContrastCheck(
            name = "第 $place 名的文字压卡片",
            foreground = rank.ink,
            background = scheme.surface,
            min = MikomiContrast.Body,
        )
    }

    // ---- 媒体族：只有 HUD 那一对有色值关系（浅底 + 深字） ----
    val media = colors.media
    val hudBackground = media.overlayContainer.compositeOver(media.backdrop)
    checks += ContrastCheck(
        name = "HUD 文字压浅色浮层",
        foreground = media.overlayContent.compositeOver(hudBackground),
        background = hudBackground,
        min = MikomiContrast.Body,
    )

    return checks
}
