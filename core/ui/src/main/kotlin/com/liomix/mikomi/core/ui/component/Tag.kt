package com.liomix.mikomi.core.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 标签高度，同时就是它的触控高度（满足 48dp 最小触控目标）。 */
private val TagHeight = 48.dp

/** 标签文字左右内边距。 */
private val TagHorizontalPadding = 18.dp

/** 外层胶囊容器的内边距：选中标签与外层之间留出的白色间隙。 */
private val TagRowPadding = 4.dp

/** 按压反馈的动画时长。 */
private const val PressAnimMillis = 100

/** 选中底色透明度：浅色染主色，保持轻量，不抢文字。 */
private const val ContainerAlphaSelected = 0.14f

/** 未选中时的轻微缩放，让选中项在视觉上稍微「抬」起来。 */
private const val UnselectedScale = 0.97f

/** 按下时的缩放，提供即时触感。 */
private const val PressedScale = 0.96f

/**
 * 标签选项（一组里单选一个）。
 *
 * 视觉：选中为主色淡底 + 主色半粗文字；未选中为透明底 + 次级文字色。
 *
 * 动效：
 * - 选中态用 spring 驱动底色、文字色与字重同步过渡；
 * - 选中项轻微放大、按下时轻微收缩，切换与触摸都有连续反馈；
 * - 所有动画共用同一进度值，避免颜色、字重各自为政产生割裂感。
 *
 * 通常与 [TagRow] 搭配使用：
 * ```
 * TagRow {
 *     Tag(text = "动态", selected = selected == 0, onClick = { selected = 0 })
 *     Tag(text = "已归档的动态", selected = selected == 1, onClick = { selected = 1 })
 * }
 * ```
 *
 * @param text 标签文案（由调用方 `stringResource` 解析后传入，组件层不依赖业务资源）。
 * @param selected 是否处于选中态。
 * @param onClick 点击回调（语义按 Tab 上报）。
 */
@Composable
fun Tag(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val selectionProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "tagSelection",
    )
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = PressAnimMillis),
        label = "tagPress",
    )

    val containerColor = lerp(
        start = colors.primary.copy(alpha = 0f),
        stop = colors.primary.copy(alpha = ContainerAlphaSelected),
        fraction = selectionProgress,
    )
    val contentColor = lerp(
        start = colors.onSurfaceVariant,
        stop = colors.primary,
        fraction = selectionProgress,
    )
    val normalWeight = FontWeight.Normal.weight
    val selectedWeight = FontWeight.SemiBold.weight
    val textWeight = FontWeight(
        (normalWeight + (selectedWeight - normalWeight) * selectionProgress).roundToInt(),
    )

    Surface(
        modifier = modifier
            .height(TagHeight)
            .graphicsLayer {
                val selectedScale = UnselectedScale + (1f - UnselectedScale) * selectionProgress
                val scale = selectedScale * (1f - (1f - PressedScale) * pressProgress)
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = TagHorizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = textWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 标签容器：白色（跟随 surface）胶囊底，内部横向排列一组 [Tag]。
 *
 * 外层容器只负责背景、圆角与内边距，具体选项由调用方通过 Slot 传入，
 * 便于同一组标签承载任意数量、任意文案。
 *
 * @param content 标签选项，通常为若干 [Tag]。
 */
@Composable
fun TagRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(TagRowPadding).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}