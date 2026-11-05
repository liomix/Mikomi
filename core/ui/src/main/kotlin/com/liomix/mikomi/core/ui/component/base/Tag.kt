package com.liomix.mikomi.core.ui.component.base

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiPillShape
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiStroke

/** 元信息徽标的小圆角：它是「贴住文字的一块底」，不是胶囊。 */
private val MetaShape: Shape = RoundedCornerShape(MikomiRadius.Sm)

/** 选中态 / 未选中态切换时的颜色过渡时长。 */
private const val ColorAnimMillis = MikomiMotion.Duration.Short

/** 选中底色透明度：浅色染主色，避免实心块视觉重量过重（看起来像主按钮）。 */
private const val ContainerAlphaSelected = 0.14f

/** 选中描边透明度。 */
private const val BorderAlphaSelected = 0.55f

/** 未选中描边透明度：只勾出边界，不抢文字。 */
private const val BorderAlphaUnselected = 0.7f

/** 可选中标签的高度，同时就是它的触控高度（见文件末尾的破例说明）。 */
private val SelectableHeight = MikomiSizes.ChipHeight

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
 * 标签的三套默认规格。
 *
 * 它只提供**默认值**：颜色、形状、内边距、字号、最小高度都能被调用方单独覆盖
 * （「这枚标签长什么样」由页面决定，组件不预设死）。不传 [Tag] 的 `style` 时，
 * 按是否可点自动选：可点 → [Selectable]，只读 → [Content]。
 */
enum class TagStyle {
    /** 可选中的选项：胶囊 + 40dp 触控高度，选中时主色淡底 + 主色描边 + 半粗文字。 */
    Selectable,

    /** 陈列型只读标签：描边胶囊，字号大一号。题材、关键词这类内容。 */
    Content,

    /** 元信息徽标：填色小圆角，字号小三档。版本号、状态这类次要信息。 */
    Meta,
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
 * // 版本号徽标：换规格 + 单独调色/圆角/字号
 * Tag(
 *     text = "v1.2.3",
 *     style = TagStyle.Meta,
 *     colors = TagColors(container = …, content = …),
 *     shape = RoundedCornerShape(MikomiRadius.Xs),
 *     textStyle = MaterialTheme.typography.labelSmall,
 *     contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
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
 * @param style 默认规格，只在调用方没有覆盖对应参数时生效。
 * @param colors 配色；默认按 [style] 与 [selected] 从主题推导。
 * @param shape 形状，默认胶囊（`Meta` 是 6dp 圆角）。
 * @param contentPadding 文字四周留白。
 * @param textStyle 文字样式（字号、行高、字重）。
 * @param minHeight 最小高度；可选中规格默认 40dp，只读规格不设下限。
 * @param maxLines 最多几行；默认不限（标签一般是短词，长文案由调用方决定是省略还是换行）。
 * @param overflow 超出 [maxLines] 时怎么收尾。
 */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    style: TagStyle = if (onClick != null) TagStyle.Selectable else TagStyle.Content,
    colors: TagColors = style.defaultColors(selected = selected),
    shape: Shape = style.defaultShape,
    contentPadding: PaddingValues = style.defaultContentPadding,
    textStyle: TextStyle = style.defaultTextStyle(),
    minHeight: Dp = style.defaultMinHeight,
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
            .heightIn(min = minHeight)
            .then(
                if (click == null) {
                    Modifier
                } else {
                    Modifier
                        .clip(shape)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = click,
                        )
                },
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = borderColor?.let { BorderStroke(MikomiStroke.Thin, animatedBorderColor) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = textStyle,
                // 选中才提字重；其余情况沿调用方传进来的 textStyle（有些徽标要 Medium）。
                fontWeight = if (selected) FontWeight.SemiBold else textStyle.fontWeight,
                maxLines = maxLines,
                overflow = overflow,
            )
        }
    }
}

/** 三套规格的形状。 */
private val TagStyle.defaultShape: Shape
    get() = when (this) {
        TagStyle.Selectable, TagStyle.Content -> MikomiPillShape
        TagStyle.Meta -> MetaShape
    }

/**
 * 两套实测内边距，留在这里而不上提成 token。
 *
 * 它们回答的是「这枚标签自己有多胖」，不是「布局要留多少呼吸」—— 上提成共用 token
 * 之后，调 Tag 的手感会连带改到别的控件。判别方法同 `MikomiSpacing` 的说明：
 * 这个数字变了，是整页的节奏变了，还是只有这一个控件变了。
 */
private val SelectableHorizontalPadding = 18.dp
private val MetaHorizontalPadding = 5.dp
private val MetaVerticalPadding = 1.dp

/** 三套规格的内边距。可选中的那套左右留白更大，因为它是控件而不是文字块。 */
private val TagStyle.defaultContentPadding: PaddingValues
    get() = when (this) {
        TagStyle.Selectable -> PaddingValues(horizontal = SelectableHorizontalPadding)
        TagStyle.Content -> PaddingValues(
            horizontal = MikomiSpacing.Lg,
            vertical = MikomiSpacing.Sm,
        )
        TagStyle.Meta -> PaddingValues(
            horizontal = MetaHorizontalPadding,
            vertical = MetaVerticalPadding,
        )
    }

/** 三套规格的最小高度：只有控件需要撑到 40dp，只读标签跟着文字走。 */
private val TagStyle.defaultMinHeight: Dp
    get() = when (this) {
        TagStyle.Selectable -> SelectableHeight
        TagStyle.Content, TagStyle.Meta -> 0.dp
    }

/** 三套规格的字号：徽标要压住存在感，所以比文字本体还小一档。 */
@Composable
private fun TagStyle.defaultTextStyle(): TextStyle = when (this) {
    TagStyle.Selectable -> MaterialTheme.typography.labelLarge
    TagStyle.Content -> MaterialTheme.typography.labelMedium
    TagStyle.Meta -> MaterialTheme.typography.labelSmall
}

/**
 * 三套规格的默认配色。
 *
 * - [TagStyle.Selectable]：未选中透明 + 中性描边；选中主色淡底 + 主色描边 + 主色文字；
 * - [TagStyle.Content]：页面底色 + 中性描边，只勾边界；
 * - [TagStyle.Meta]：浅一档的容器色，不描边——它靠底色和主体区分，不再加线。
 */
@Composable
private fun TagStyle.defaultColors(selected: Boolean): TagColors {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        TagStyle.Selectable -> if (selected) {
            TagColors(
                container = colors.primary.copy(alpha = ContainerAlphaSelected),
                content = colors.primary,
                border = colors.primary.copy(alpha = BorderAlphaSelected),
            )
        } else {
            TagColors(
                container = Color.Transparent,
                content = colors.onSurfaceVariant,
                border = colors.outlineVariant.copy(alpha = BorderAlphaUnselected),
            )
        }

        TagStyle.Content -> TagColors(
            container = colors.surface,
            content = colors.onSurface,
            border = colors.outlineVariant,
        )

        TagStyle.Meta -> TagColors(
            container = colors.surfaceContainerHigh,
            content = colors.onSurfaceVariant,
        )
    }
}
