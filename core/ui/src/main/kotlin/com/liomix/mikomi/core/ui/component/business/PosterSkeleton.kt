package com.liomix.mikomi.core.ui.component.business

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.component.base.SkeletonShimmer

/**
 * 海报卡骨架：与 [AnimePosterCard] 同构（2:3 封面块 + 单行标题条）。
 *
 * 首屏骨架与分页骨架共用它，保证「加载中」的样子在哪一段都一致。
 */
@Composable
fun PosterCardSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp),
            shape = RoundedCornerShape(4.dp),
        )
    }
}

/**
 * 分页加载中的骨架行：一行与网格**同宽、同列数、同间距**的海报占位。
 *
 * 放在网格的 full-span 项里即可（宽度天然等于网格内容宽），用于替换「到底了一个转圈」：
 * 用户看到的是内容仍在长出来，而不是一个孤立的进度环。
 *
 * 列数不复用业务里的 `GridCells` 常量，而是用与 `GridCells.Adaptive` 完全相同的公式
 * 从自身宽度反推：`n = (W + spacing) / (minWidth + spacing)`。这样即便以后改了网格的
 * 最小列宽或页面边距，骨架行也不会错位（错的列数会多出一个折行的空格子，很显眼）。
 *
 * 整行共用一个 [SkeletonShimmer] 时间线；这一项本身只在接近视口时才被组合，
 * 所以加载结束、行消失时动画也随之停止。
 *
 * @param minColumnWidth 必须与网格 `GridCells.Adaptive(minSize = ...)` 的值一致
 */
@Composable
fun PosterSkeletonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    minColumnWidth: Dp = 100.dp,
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { spacing.toPx() }.coerceAtLeast(0f)
    val minColumnPx = with(density) { minColumnWidth.toPx() }.coerceAtLeast(1f)

    SkeletonShimmer {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val columns = ((constraints.maxWidth + spacingPx) / (minColumnPx + spacingPx))
                .toInt()
                .coerceAtLeast(1)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                repeat(columns) {
                    PosterCardSkeleton(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
