package com.liomix.mikomi.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.mikomiClickable
import kotlin.math.roundToInt

/** 首页快捷入口动作。 */
enum class HomeQuickAction {
    SCHEDULE,
    RANK,
    CATEGORY,
}

/** 首页快捷入口的图标：28dp 是「三栏并排」这个布局的物理尺寸，不属于图标档位。 */
private val ActionIconSize = 28.dp

/**
 * 圆角的上限：展开高度的一半。
 *
 * 收到最扁时圆角正好等于半高，形状就是胶囊；再大也没有差别（不会被画出来）。
 */
private val ActionTabsMaxCorner = 33.dp

private val HOME_ACTIONS = listOf(
    ActionItem(
        HomeQuickAction.SCHEDULE,
        Icons.Calendar,
        R.string.home_action_schedule,
    ),
    ActionItem(
        HomeQuickAction.RANK,
        Icons.Trophy,
        R.string.home_action_rank,
    ),
    ActionItem(
        HomeQuickAction.CATEGORY,
        Icons.Category,
        R.string.home_action_category,
    ),
)

/**
 * 首页快捷入口：时间表 / 排行榜 / 分类，三列均分。
 *
 * 每个入口触控目标 ≥48dp；目的地由 [onActionClick] 交给上层路由处理（当前为占位）。
 *
 * 高度只由三处内边距决定：图标 28dp 与文字、间距都不动，收的是栏与按钮自己的留白
 * （12 + 8 → 4 + 4）。整栏从 90dp 降到 66dp —— 头图加高的那部分正是从这里让出来的，
 * 省下的高度换画面，而不是换内容。
 *
 * ## 收放：照搬 Telegram `ProfileActionsView` 的口径
 *
 * 那里滚动压缩不是「压扁」也不是「裁掉」，而是：
 *
 * ```java
 * float fraction = clamp01(height / targetHeight);        // 剩余高度比例
 * float alphaFraction1 = clamp01((fraction - 0.2f) / 0.8f); // 底色
 * float alphaFraction2 = clamp01((fraction - 0.4f) / 0.6f); // 图标与文字
 * canvas.scale(fraction, fraction, cx, cy);               // 以按钮中心等比缩小
 * ```
 *
 * 三条要点：**等比缩小**（宽高一起收，不是只压高度）、**两段淡出**（内容先于底色消失）、
 * **中心为锚点**。所以收到底时不会有「一行字被挤成一根线」的瞬间。
 *
 * @param collapse 0 = 完全展开，1 = 完全收起。高度与画面都在组件内部一起处理：
 *   框只上报缩小后的高度并让圆角变圆，内容等比缩小。
 */
@Composable
fun HomeActionTabs(
    onActionClick: (HomeQuickAction) -> Unit,
    modifier: Modifier = Modifier,
    collapse: Float = 0f,
) {
    val remaining = (1f - collapse).coerceIn(0f, 1f)
    // 与 ProfileActionsView 一致：底色留到 0.2，内容 0.4 就开始走。
    val containerAlpha = ((remaining - 0.2f) / 0.8f).coerceIn(0f, 1f)
    val contentAlpha = ((remaining - 0.4f) / 0.6f).coerceIn(0f, 1f)
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = containerAlpha)

    // 这里不能用 `Surface`：`Surface` 的背景尺寸 = 它自己的测量高度，一旦高度被外部压小，
    // 它会连同里面的 Row 一起被重新约束 —— 内容先被挤扁，再缩小，缩放比例就失真了。
    // 所以背景自己画（`drawBehind`，尺寸读的是**布局之后**的高度），内容单独按原高排布。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // 圆角跟着高度一起长：展开时是常规卡片角，越扁越圆，收到最扁时正好半圆。
                // 上限是半高 —— 再大画出来也是胶囊，没有区别。
                val half = size.height / 2f
                val minCorner = Radius.Lg.toPx()
                val cornerPx = (minCorner + (half - minCorner) * (1f - remaining))
                    .coerceIn(0f, half)
                drawRoundRect(
                    color = containerColor,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )
            }
            // 只有高度上报缩小值；内容仍按自然高度测量与排布（不重排、不被挤扁）。
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
                )
                val height = (placeable.height * remaining).roundToInt().coerceAtLeast(0)
                layout(placeable.width, height) { placeable.place(0, 0) }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.Xs)
                // 内容等比缩小，宽度也跟着收 —— 与框相反：框是「变矮变圆」，内容是「变小」。
                // 锚点取顶部居中：缩小时顶边不动、底部往上收，刚好落在框内。
                .graphicsLayer {
                    scaleX = remaining
                    scaleY = remaining
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    alpha = contentAlpha
                },
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            HOME_ACTIONS.forEach { item ->
                ActionButton(
                    icon = item.icon,
                    label = stringResource(item.labelRes),
                    onClick = { onActionClick(item.action) },
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.Md))
            .mikomiClickable(onClick = onClick)
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = ActionIconSize,
        )
        Spacer(modifier = Modifier.height(Spacing.Tight))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class ActionItem(
    val action: HomeQuickAction,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
)
