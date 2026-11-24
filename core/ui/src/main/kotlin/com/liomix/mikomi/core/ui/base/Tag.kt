package com.liomix.mikomi.core.ui.base

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.designsystem.Motion
import com.liomix.mikomi.core.designsystem.PillShape
import com.liomix.mikomi.core.designsystem.TagSpec
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.Stroke

/** 元信息徽标的小圆角：它是「贴住文字的一块底」，不是胶囊。 */
private val MetaShape: Shape = RoundedCornerShape(Radius.Sm)

/** 主色淡底的浓度：与 TabBar 指示器同一档（0.14），两处「主色被淡化当底」看起来才是一套。 */
private const val NeutralContainerAlpha = 0.14f

/** 选中态 / 未选中态切换时的颜色过渡时长。 */
private const val ColorAnimMillis = Motion.Duration.Short

/** 可选中标签的高度，同时就是它的触控高度（见文件末尾的破例说明）。 */
private val SelectableHeight = Sizes.ChipHeight

/**
 * 标签配色。
 *
 * @param container 底色；透明表示只画描边
 * @param content 文字色
 * @param border 描边色；null 表示不描边
 */
@Immutable
data class TagColors(
    val container: Color,
    val content: Color,
    val border: Color? = null,
)

/**
 * 标签的配色走向：决定颜色是否随 [Tag] 的 `selected` 变化。
 *
 * 只有两档，因为「陈列标签」与「元信息徽标」的配色本来就完全一样
 * （浅容器底 + 次级文字色），分成两个 tone 只会多一个空分支。
 */
enum class TagTone {
    /** 可选中：颜色在「透明底 + 中性描边」与「主色淡底 + 主色描边」之间切换。 */
    Selectable,

    /** 只读：固定的浅容器底 + 次级文字色，不描边、不随状态变。 */
    Neutral,
}

/**
 * 标签规格：形状、内边距、最小高度、字号、配色走向。
 *
 * 三套预设见 companion。与 [PressButtonStyle]、[TabItemStyle] 同一套写法 ——
 * 调用方要微调一项时用 `copy`，而不用再往 [Tag] 的签名上挂第 14 个参数。
 */
@Immutable
data class TagStyle(
    val shape: Shape,
    val contentPadding: PaddingValues,
    val minHeight: Dp,
    val textStyle: TextStyle,
    val tone: TagTone,
) {
    companion object {
        /** 可选中的选项：胶囊 + 40dp 触控高度，选中时主色淡底 + 主色描边 + 半粗文字。 */
        @Composable
        fun selectable(): TagStyle = TagStyle(
            shape = PillShape,
            contentPadding = PaddingValues(horizontal = TagSpec.SelectableHorizontalPadding),
            minHeight = SelectableHeight,
            textStyle = MaterialTheme.typography.labelLarge,
            tone = TagTone.Selectable,
        )

        /** 陈列型只读标签：填色胶囊（24dp，与图标默认档同高），不带描边。题材、计数这类内容。 */
        @Composable
        fun content(): TagStyle = TagStyle(
            shape = PillShape,
            contentPadding = PaddingValues(horizontal = TagSpec.ChipHorizontalPadding),
            minHeight = TagSpec.ChipHeight,
            textStyle = MaterialTheme.typography.labelMedium,
            tone = TagTone.Neutral,
        )

        /** 元信息徽标：填色小圆角，字号小三档。版本号、状态这类次要信息。 */
        @Composable
        fun meta(): TagStyle = TagStyle(
            shape = MetaShape,
            contentPadding = PaddingValues(
                horizontal = TagSpec.MetaHorizontalPadding,
                vertical = TagSpec.MetaVerticalPadding,
            ),
            minHeight = 0.dp,
            textStyle = MaterialTheme.typography.labelSmall,
            tone = TagTone.Neutral,
        )
    }
}

/**
 * 标签：一枚带底色的短文字。
 *
 * **静态展示与可选中是同一个组件**：传不传 [onClick] 决定它是不是控件。
 * 没有 `onClick` 时它只是一个 [Surface]，不挂任何手势。
 *
 * 三套默认规格见 [TagStyle]；但**最终样子由参数决定**，页面想调哪一个就传哪一个：
 *
 * ```kotlin
 * // 默认：可选中（40dp 胶囊，主色选中态）
 * Tag("1.5×", selected = speed == 1.5f, onClick = { … })
 *
 * // 默认：只读陈列标签（描边胶囊）
 * Tag("奇幻")
 *
 * // 版本号徽标：换预设，再用 copy 微调一项
 * Tag(
 *     text = "v1.2.3",
 *     style = TagStyle.meta().copy(shape = RoundedCornerShape(Radius.Xs)),
 *     colors = TagColors(container = …, content = …),
 * )
 * ```
 *
 * 造型默认是胶囊 —— 那是**形状**，不是它的名字：换个形状（圆角矩形、下划线）它也还是标签。
 *
 * **可选中形态的触控范围 = 视觉尺寸（40dp）**：按规范本应外包 48dp 触控区
 * （rule §5.3），此处由产品侧明确破例——标签成组横排时 48dp 的点击区会互相挤压，
 * 且 48dp 的容器让标签本身显得笨重。
 * 因此：可选中形态只用于这类成组、可反复切换的轻量选项，不要用它承载关键的独立操作。
 *
 * @param text 文案（由调用方 `stringResource` 解析后传入，组件层不依赖业务资源）。
 * @param selected 是否处于选中态；选中时文字半粗（`Selectable` 还会换配色）。
 * @param onClick 点击回调（语义按单选按钮上报）；传 null 即只读展示，不挂手势。
 * @param style 规格预设（[TagStyle.selectable] / [TagStyle.content] / [TagStyle.meta]）；
 *   要微调某一项用 `copy`，不要再往这个签名上挂参数。
 * @param colors 配色；默认按 [TagStyle.tone] 与 [selected] 从主题推导。
 * @param leadingIcon 前置图标（评分星标、播放量这种）；图标是内容不是样式，
 *   所以走签名而不是 [TagStyle]。
 * @param iconTint 图标色；null 表示跟随当前内容色。
 * @param maxLines 最多几行；默认不限（标签一般是短词，长文案由调用方决定是省略还是换行）。
 * @param overflow 超出 [maxLines] 时怎么收尾。
 */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    style: TagStyle = if (onClick != null) TagStyle.selectable() else TagStyle.content(),
    colors: TagColors = style.tone.colorsFor(selected = selected),
    leadingIcon: ImageVector? = null,
    iconTint: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val containerColor by animateColorAsState(
        targetValue = colors.container,
        animationSpec = tween(durationMillis = ColorAnimMillis),
        label = "tagContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = colors.content,
        animationSpec = tween(durationMillis = ColorAnimMillis),
        label = "tagContent",
    )
    val borderColor = colors.border
    val animatedBorderColor by animateColorAsState(
        targetValue = borderColor ?: Color.Transparent,
        animationSpec = tween(durationMillis = ColorAnimMillis),
        label = "tagBorder",
    )

    // 不用 Surface 的可点击重载：它会套一层 minimumInteractiveComponentSize（48dp），
    // 与「触控范围等于视觉尺寸」的破例要求冲突；这里自己组合 selectable + 裁剪。
    val click = onClick
    Surface(
        modifier = modifier
            .heightIn(min = style.minHeight)
            .then(
                if (click == null) {
                    Modifier
                } else {
                    Modifier
                        .clip(style.shape)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = click,
                        )
                },
            ),
        shape = style.shape,
        color = containerColor,
        contentColor = contentColor,
        border = borderColor?.let { BorderStroke(Stroke.Thin, animatedBorderColor) },
    ) {
        // 这里**不能**写 `fillMaxHeight()`：`Surface` 是 `Box(propagateMinConstraints = true)`，
        // 最小高度（可选中规格 40dp）本来就会传给内容，`fillMaxHeight` 只会多干一件事 ——
        // 当父级把高度限制住时（例如嵌在 `Row` 里、`Row` 又撑满了一张卡片的高度），
        // 标签会跟着把最大高度也吃满：收藏区的「共 3 部」曾被拉成 948px 高，
        // 同一列里的网格因此被挤到 0 高度，整块区域看起来是空的。
        Row(
            modifier = Modifier.padding(style.contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs),
        ) {
            if (leadingIcon != null) {
                // 夹在字行里的图标只有这一档：16dp，与 labelSmall 的笔画视觉等重。
                Icon(
                    icon = leadingIcon,
                    contentDescription = null,
                    tint = iconTint ?: LocalContentColor.current,
                    size = Sizes.IconXs,
                )
            }
            Text(
                text = text,
                style = style.textStyle,
                // 选中才提字重；其余情况沿调用方传进来的 textStyle（有些徽标要 Medium）。
                fontWeight = if (selected) FontWeight.SemiBold else style.textStyle.fontWeight,
                maxLines = maxLines,
                overflow = overflow,
            )
        }
    }
}

/**
 * 三套规格的默认配色。
 *
 * - [TagTone.Selectable]：未选中透明底 + 中性描边；选中主色淡底 + 主色描边 + 主色文字；
 * - [TagTone.Neutral]：浅一档的容器底 + 次级文字色，不描边 —— 陈列标签与元信息徽标共用这一档。
 */
@Composable
private fun TagTone.colorsFor(selected: Boolean): TagColors {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        TagTone.Selectable -> if (selected) {
            TagColors(
                container = colors.primary.copy(alpha = TagSpec.ContainerAlphaSelected),
                content = colors.primary,
                border = colors.primary.copy(alpha = TagSpec.BorderAlphaSelected),
            )
        } else {
            TagColors(
                container = Color.Transparent,
                content = colors.onSurfaceVariant,
                border = colors.outlineVariant.copy(alpha = TagSpec.BorderAlphaUnselected),
            )
        }

        // 主色淡底 + 主色字：标签整体随主题换色。
        //
        // 底色不用 `primaryContainer`、字不用 `onPrimaryContainer` —— M3 给的
        // `onPrimaryContainer` 是「容器上的深色字」，多数预设下接近黑，看上去
        // 和主题没关系；这里要的是**主色本身**当字，所以底取主色的低透明度。
        TagTone.Neutral -> TagColors(
            container = colors.primary.copy(alpha = NeutralContainerAlpha),
            content = colors.primary,
        )
    }
}
