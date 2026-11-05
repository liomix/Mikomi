package com.liomix.mikomi.core.ui.component.base

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiElevation
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiPillShape
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing

/*
 * 按压形变直接取设计系统的 [MikomiMotion.Press]：按下 60ms、松手 350ms、过冲 5.0，
 * 全部照抄 Telegram 的 `ButtonBounce`（`ui/Components/ButtonBounce.java`）。
 * 「按压感」这种东西推不出来，只能对着一个公认手感好的实现抄 —— 所以不自己取整。
 */

/**
 * 按压形变：按下缩小、松手弹回。
 *
 * 这是本 App 所有「点一下就发生什么」的可点元素的统一手感 —— [PressButton]、
 * [PressIconButton] 都挂在它上面，别在调用点各写一份 `animateFloatAsState`，
 * 否则同一屏里的两个按钮会缩得不一样、弹得不一样。
 *
 * 走 [graphicsLayer] 的缩放矩阵，**不参与布局**：按下时邻居不挪、也不触发测量。
 * 缩放值在绘制阶段才读（`graphicsLayer` 的 lambda 里），所以动画的每一帧只更新图层，
 * 不重组任何组件。
 *
 * @param interactionSource 按压态的来源。手势在哪个节点上，就传那个节点的源。
 * @param enabled 禁用时不缩放：按不动的东西不该还有反馈。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val pressing = pressed && enabled
    val scale = animateFloatAsState(
        targetValue = if (pressing) MikomiMotion.Press.Scale else 1f,
        animationSpec = if (pressing) {
            tween(durationMillis = MikomiMotion.Press.InMillis, easing = MikomiMotion.Curve.Press)
        } else {
            tween(
                durationMillis = MikomiMotion.Press.ReleaseMillis,
                easing = MikomiMotion.Curve.Overshoot,
            )
        },
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** 按钮的配色走向。 */
enum class PressButtonTone {
    /** 实心主色：页面里的主操作，一块实心色最醒目。 */
    Primary,

    /** 浅主色：悬浮在内容之上的操作，颜色轻一点，靠阴影托起来。 */
    Tonal,
}

/**
 * 按钮规格：形状、内边距、最小高度、阴影、配色走向。
 *
 * 和 `TagStyle` 同样的思路——外观收在值对象里，调用点只挑预设。两个预设分别对应
 * 两种真实场景：页面内联操作（[Filled]）与悬浮操作（[Elevated]）。
 */
@Immutable
data class PressButtonStyle(
    /** 形状。 */
    val shape: Shape,
    /** 文字四周留白；图标那一侧的间距另算。 */
    val contentPadding: PaddingValues,
    /** 最小高度（同时决定触控高度）。 */
    val minHeight: Dp,
    /** 投影高度：0 表示完全贴平，不画阴影。 */
    val elevation: Dp,
    /** 配色走向。 */
    val tone: PressButtonTone,
    /** 前导图标尺寸。 */
    val iconSize: Dp = MikomiSizes.IconMd,
    /** 图标与文字之间的距离。 */
    val iconGap: Dp = MikomiSpacing.Md,
) {
    companion object {
        /** 页面内主操作：胶囊实心主色，48dp，无阴影。 */
        val Filled: PressButtonStyle = PressButtonStyle(
            shape = MikomiPillShape,
            contentPadding = PaddingValues(horizontal = MikomiSpacing.Xl),
            minHeight = MikomiSizes.ButtonHeight,
            elevation = MikomiElevation.Flat,
            tone = PressButtonTone.Primary,
        )

        /**
         * 悬浮操作：56dp、16dp 圆角、浅主色 + 6dp 阴影。
         *
         * 数值取自 M3 的扩展 FAB（`ExtendedFloatingActionButton`），这样从 FAB 换成
         * [PressButton] 时尺寸和位置都不用动。左右留白 20dp 与 56dp 高度是绑在一起的一对
         * 规格（高度减两倍留白 = 一个字高），所以不拆成 token。
         */
        val Elevated: PressButtonStyle = PressButtonStyle(
            shape = RoundedCornerShape(MikomiRadius.Xl),
            contentPadding = PaddingValues(horizontal = MikomiSpacing.Room),
            minHeight = 56.dp,
            elevation = MikomiElevation.Floating,
            tone = PressButtonTone.Tonal,
        )
    }
}

/**
 * 通用按钮：带按压形变的文字按钮（可选前导图标）。
 *
 * ## 按压手感
 *
 * 按下时整块缩到 90%（60ms 内完成），松手 350ms 弹回、冲过 100% 一次再收住 ——
 * 看起来是「被压扁又弹回来」，而不是「变色然后复原」。手感由 [pressScale] 提供，
 * 要调就改那一处，别在这个函数里另起一套，否则同屏两个按钮会缩得不一样。
 *
 * 尺寸与阴影来自 [style]；文字固定 `titleSmall` + 半粗 —— 这是本 App 按钮的字形，
 * 需要别的字号时应该先确认那是不是另一种按钮，而不是加参数。
 *
 * 只有图标没有文字时用 [PressIconButton]，两者共用同一套手感。
 *
 * @param text 按钮文案（由调用方 `stringResource` 解析后传入）。
 * @param onClick 点击回调。
 * @param enabled 置灰并吞掉点击；禁用时不做按压动画。
 * @param leadingIcon 前导图标；文案已经说明了操作，所以图标对无障碍隐藏。
 * @param style 规格，默认 [PressButtonStyle.Filled]。
 */
@Composable
fun PressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    style: PressButtonStyle = PressButtonStyle.Filled,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val scheme = MaterialTheme.colorScheme
    val containerColor = when {
        !enabled -> scheme.onSurface.copy(alpha = MikomiMotion.Disabled.ContainerAlpha)
        style.tone == PressButtonTone.Primary -> scheme.primary
        else -> scheme.primaryContainer
    }
    val contentColor = when {
        !enabled -> scheme.onSurface.copy(alpha = MikomiMotion.Disabled.ContentAlpha)
        style.tone == PressButtonTone.Primary -> scheme.onPrimary
        else -> scheme.onPrimaryContainer
    }

    Surface(
        onClick = onClick,
        modifier = modifier.pressScale(interactionSource, enabled),
        enabled = enabled,
        shape = style.shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = style.elevation,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = style.minHeight)
                .padding(style.contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(style.iconSize),
                )
                Spacer(modifier = Modifier.width(style.iconGap))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 圆形图标按钮：只有一枚图标，形状与 [PressButton] 不同、手感完全一致。
 *
 * 它是 [PressButton] 的「图标版」而不是另一种控件：同样的按下缩放与回弹，
 * 同样的禁用透明度，区别只在形状（圆形）和内容（图标）。所以两者都挂在
 * [pressScale] 上 —— 一屏里一个是胶囊、一个是圆，按下去的节奏必须是一样的。
 *
 * 颜色不预设成某个语义（主色/中性），因为图标按钮的底色往往表达**状态**而不是
 * 层级：收藏按钮点前是中性底、点后是主色底。这层判断只有页面知道，所以颜色作为
 * 参数传入，默认给一组中性值。
 *
 * @param icon 图标。
 * @param contentDescription 无障碍描述。图标按钮没有文字，这个**不能为空**，
 *   否则读屏只会念「按钮」。
 * @param onClick 点击回调。
 * @param enabled 置灰并吞掉点击；禁用时不做按压动画。
 * @param containerColor 底色。
 * @param contentColor 图标颜色。
 * @param size 直径，同时也是触控尺寸（默认 48dp，正好是无障碍最小触控目标）。
 * @param iconSize 图标尺寸。
 */
@Composable
fun PressIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = MikomiSizes.TouchTarget,
    iconSize: Dp = MikomiSizes.IconMd,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = modifier.pressScale(interactionSource, enabled),
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) {
            containerColor
        } else {
            scheme.onSurface.copy(alpha = MikomiMotion.Disabled.ContainerAlpha)
        },
        contentColor = if (enabled) {
            contentColor
        } else {
            scheme.onSurface.copy(alpha = MikomiMotion.Disabled.ContentAlpha)
        },
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
