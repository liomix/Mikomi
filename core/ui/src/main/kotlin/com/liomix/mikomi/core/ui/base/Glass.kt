package com.liomix.mikomi.core.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp as colorLerp
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.designsystem.Stroke

/**
 * 玻璃：一层半透明的底面，外加一条可选的发丝边。
 *
 * ## 它不是模糊
 *
 * 「玻璃」在这里只有两件事：**透**（底下的色块隐约透出来）与**边**（一条比填充更亮或更暗
 * 的细线交代边界）。**没有模糊** —— 真正的背景模糊要先把底下的内容录进一个
 * `graphicsLayer` 再糊出来（`BlurredTopBar` 就是这么做的），代价是那块内容必须由组件自己
 * 拥有。做成一枚 `Modifier` 就注定拿不到「我背后是什么」，所以这里不假装有。
 *
 * 也正因为不模糊，它便宜：一次半透明填充加一次描边，没有离屏、没有 `RenderEffect`。
 * 绝大多数「浮在页面上」的场合要的就是这个 —— 糊一块纯色和原样画它是同一张图。
 *
 * ## 三种厚度
 *
 * - [GlassStyle.raised]：浮在页面上的控件（胶囊、浮起的小卡）。最实的一层。
 * - [GlassStyle.inset]：压在另一层玻璃上的小件（胶囊右端的箭头底座）。比它下面那层更透，
 *   才读成「凹槽」而不是第二颗按钮。
 * - [GlassStyle.panel]：大面积容器（底部弹窗、抽屉）。只有底色、**没有边** ——
 *   它的边界由位置、圆角和遮罩交代，再描一圈会变成一张卡片。
 *
 * 三者的差别只是浓度，取的都是 `surfaceContainer*` 同一族槽位：日间容器色比页面底色深、
 * 夜间比它浅，所以「谁在上一层」这件事在两种模式下自动翻面，不需要写死两个灰。
 */
@Immutable
data class GlassStyle(
    /** 填充色，通常带 alpha。也用来喂 `ModalBottomSheet(containerColor = ...)` 这类只收颜色的槽位。 */
    val fill: Color,
    /** 发丝边；`null` 表示不描边（面板层用它）。 */
    val edge: Color?,
) {
    companion object {
        /** 控件层：浮在页面上的胶囊、小卡。 */
        @Composable
        fun raised(): GlassStyle = GlassStyle(
            fill = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = RaisedAlpha),
            edge = MaterialTheme.colorScheme.outlineVariant.copy(alpha = EdgeAlpha),
        )

        /** 凹槽层：压在另一层玻璃上的小件。 */
        @Composable
        fun inset(): GlassStyle = GlassStyle(
            fill = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = InsetAlpha),
            edge = MaterialTheme.colorScheme.outlineVariant.copy(alpha = EdgeAlpha),
        )

        /** 面板层：底部弹窗、抽屉这类大面积容器。 */
        @Composable
        fun panel(): GlassStyle = GlassStyle(
            fill = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = PanelAlpha),
            edge = null,
        )

        /**
         * 压在**图片**上：半透明白底 + 白描边，取 `media` 层而不是页面层。
         *
         * 它与 [raised] 不是同一族的浓度差：底是白 62%（14% 压在亮封面上读不出「有一个
         * 控件」，28% 在雪景、天空上仍会发虚），边是白 14% —— 玻璃感全靠这一圈交代。
         * 与 [overPage] 是**同一条线的两端**，中间过程用 [lerpTo] 按滚动进度插值。
         */
        @Composable
        fun overImage(): GlassStyle = GlassStyle(
            fill = AppTheme.colors.media.surfaceStrong,
            edge = AppTheme.colors.media.outline,
        )

        /**
         * 压在**页面底色**上：容器色底 + 中性描边。
         *
         * 这一端是**不透明**的，和 [raised] 有意不同：顶栏压到头图上之后，它要彻底盖住
         * 底下那张图 —— 一条读了半屏的头图从搜索框里透出来，就没有「栏」可言了。
         */
        @Composable
        fun overPage(): GlassStyle = GlassStyle(
            fill = MaterialTheme.colorScheme.surfaceContainerHigh,
            edge = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * 从这一端过渡到 [target]，进度 [fraction]（0 = 完全是自己，1 = 完全是 [target]）。
 *
 * 给「同一个控件的两种处境」用：首页顶栏浮在头图上时是 [GlassStyle.overImage]，
 * 上滑贴到页面底色时是 [GlassStyle.overPage]，中间按滚动进度线性过渡 ——
 * 两条材质是同一条线的两端，不是到某一帧硬切。
 *
 * 边可以只有一端有：缺的那端按「透明」参与插值（`Modifier.border` 用透明色画不出东西，
 * 与 `null` 等价）；两端都没有边时结果也没有 —— 保留 `null` 这个语义，别让面板层
 * 无端长出一条看不见但会参与比较的边。
 */
fun GlassStyle.lerpTo(target: GlassStyle, fraction: Float): GlassStyle {
    val t = fraction.coerceIn(0f, 1f)
    val fromEdge = edge
    val toEdge = target.edge
    return GlassStyle(
        fill = colorLerp(fill, target.fill, t),
        edge = when {
            fromEdge == null && toEdge == null -> null
            else -> colorLerp(fromEdge ?: Color.Transparent, toEdge ?: Color.Transparent, t)
        },
    )
}

/**
 * 把一块玻璃铺到 [shape] 上：裁剪 → 填充 → 描边。
 *
 * 顺序是固定的，也是它存在的理由之一：**裁剪必须在填充之前**，否则圆角外的方形区域也会
 * 被涂上颜色；而调用方接在后面的点按（`Modifier.mikomiClickable`）自然就落在裁剪之内 ——
 * 四个角点不到。把这三步拆给每个调用方手写，迟早会有人把 `clip` 写在 `clickable` 后面。
 *
 * 会裁剪子内容（图片、列表都会被收进圆角）。玻璃是一整块，溢出圆角的一角属于 bug，不是特性。
 */
fun Modifier.glass(
    style: GlassStyle,
    shape: Shape,
): Modifier = this
    .clip(shape)
    .background(color = style.fill, shape = shape)
    .then(
        if (style.edge != null) {
            Modifier.border(width = Stroke.Thin, color = style.edge, shape = shape)
        } else {
            Modifier
        },
    )

/** 控件层的浓度：够实，文字压得住。 */
private const val RaisedAlpha = 0.85f

/** 凹槽层的浓度：比 [RaisedAlpha] 透一档，才读成「嵌进去」。 */
private const val InsetAlpha = 0.55f

/**
 * 面板层的浓度：够透，能看见底下被遮罩压暗的页面；够实，一屏几十个格子压上去不花。
 *
 * 0.88 是「看得出有东西在后面」和「文字不跟着背景一起花」之间的那条线。再透下去，
 * 深色封面的形状会从网格缝里浮出来，读起来像没加载完。
 */
private const val PanelAlpha = 0.88f

/** 发丝边的浓度。压得太实会变成一条「卡片描边」，玻璃感就没了。 */
private const val EdgeAlpha = 0.6f
