package com.liomix.mikomi.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 默认扫光时长（毫秒）。 */
private const val ShimmerDurationMillis = 1_200

/**
 * 骨架屏基础块：带 shimmer 扫光动画的占位块。
 *
 * 只需要一块矩形时直接用；更复杂的骨架结构（头像 + 多行文字、卡片内嵌块等）
 * 用 [shimmer] 修饰符贴到任意布局上即可，两者共用同一套扫光实现。
 *
 * @param baseColor 占位底色，默认跟随主题 surfaceVariant。
 * @param highlightColor 扫光高光色，默认跟随主题 surface。
 * @param durationMillis 扫光单轮时长，同类骨架建议传相同值以保持步调一致。
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.surface,
    durationMillis: Int = ShimmerDurationMillis,
) {
    Box(
        modifier = modifier.shimmer(
            shape = shape,
            baseColor = baseColor,
            highlightColor = highlightColor,
            durationMillis = durationMillis,
        ),
    )
}

/**
 * shimmer 扫光修饰符：可贴在任意布局上，用它扩展自定义骨架屏。
 *
 * 扫光进度在 [drawBehind] 内读取：状态变化只触发 Draw 阶段重绘，
 * 不经过组合阶段重组，长列表多块骨架同时播放也不会带来额外重组开销。
 */
@Composable
fun Modifier.shimmer(
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.surface,
    durationMillis: Int = ShimmerDurationMillis,
): Modifier {
    val progress = rememberShimmerProgress(durationMillis)
    return clip(shape).drawBehind {
        drawRect(baseColor)
        val sweepWidth = size.width * 2f
        val startX = progress.value * sweepWidth - size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlightColor, Color.Transparent),
                start = Offset(startX, 0f),
                end = Offset(startX + size.width, size.height),
            ),
        )
    }
}

/** 无限循环的扫光进度；读取方在 Draw 阶段取值，保持阶段延迟。 */
@Composable
private fun rememberShimmerProgress(durationMillis: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonProgress",
    )
}
