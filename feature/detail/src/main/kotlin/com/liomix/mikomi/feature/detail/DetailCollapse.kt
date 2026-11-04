package com.liomix.mikomi.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 头部收缩状态。
 *
 * 与「折叠工具栏」的常规做法不同，这里**头部内容不位移**：封面始终贴在屏幕顶部不动，
 * 只是可见高度从 [fullHeightPx] 一路收到 `minVisible`，收掉的部分由 Tab 栏从下往上盖住。
 * 因此看起来是「Tab 栏在封面上滑动」，而不是「封面被推走」—— banner 一直活着，也永远
 * 不会被收没。
 *
 * 只有一个浮点状态 [offsetPx]，可见高度、Tab 栏位置、海报淡出全由它派生。
 * 所有读取都发生在**布局 / 绘制阶段**（[CollapsingHeader] 与 Tab 栏的 `offset`、
 * 海报的 `graphicsLayer`），滚动过程中不会发生重组 —— 这是这套实现能跟手的前提。
 *
 * @param fullHeightPx 头部完整高度（展开态）
 * @param minVisiblePx 收到底时仍保留的高度：封面 + 标题要继续露出来，不能被 Tab 栏吃干净
 */
@Stable
internal class HeaderCollapse(
    val fullHeightPx: Float,
    minVisiblePx: Float,
) {
    /** 收缩行程：展开 → 收到底走过的距离。 */
    val travelPx: Float = (fullHeightPx - minVisiblePx).coerceAtLeast(0f)

    /** 已收起距离：0 = 完全展开，[travelPx] = 收到底。 */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** 收缩进度 0..1。 */
    val fraction: Float
        get() = if (travelPx <= 0f) 0f else (offsetPx / travelPx).coerceIn(0f, 1f)

    /** 当前可见高度。 */
    val visiblePx: Float
        get() = fullHeightPx - offsetPx

    /**
     * 海报与信息列的淡出系数。
     *
     * 它们贴在标题下面，是第一个被 Tab 栏压到的区域：后半程才淡出，收到底时正好消失。
     * 直接硬裁一张海报会在半张脸的位置切一刀，比淡出难看得多。
     */
    val infoAlpha: Float
        get() = ((1f - fraction) / 0.5f).coerceIn(0f, 1f)

    /** 收到底后头部不再有可收缩的余量，滚动全部交给内容区。 */
    private val roomToCollapse: Float
        get() = (travelPx - offsetPx).coerceAtLeast(0f)

    /** 内容区上滑：先吃掉头部；返回实际消耗的像素（正数）。 */
    fun collapseBy(distance: Float): Float {
        val used = min(distance, roomToCollapse)
        if (used > 0f) offsetPx += used
        return used
    }

    /** 内容已经到顶还在下滑：把头部放回来；返回实际消耗的像素（正数）。 */
    fun expandBy(distance: Float): Float {
        val used = min(distance, offsetPx)
        if (used > 0f) offsetPx -= used
        return used
    }
}

/**
 * 收缩状态的持有者。
 */
@Composable
internal fun rememberHeaderCollapse(fullHeight: Dp, minVisible: Dp): HeaderCollapse {
    val density = LocalDensity.current
    val fullHeightPx = with(density) { fullHeight.toPx() }
    val minVisiblePx = with(density) { minVisible.toPx() }
    return remember(fullHeightPx, minVisiblePx) {
        HeaderCollapse(fullHeightPx = fullHeightPx, minVisiblePx = minVisiblePx)
    }
}

/**
 * 把内容区的垂直滚动分给头部。
 *
 * 上滑时在 `onPreScroll`（子级之前）先吃增量 → 手一动 Tab 栏就往上盖，内容留在原地；
 * 收到底后 `collapseBy` 返回 0，剩余增量原样流向内容，于是「先收头，再滚内容」是连续的，
 * 没有切换点。下滑走 `onPostScroll`：只有内容真的滚到顶、还有多余距离时才把头部放回来，
 * 内容还没到顶时不会误触发。
 *
 * 惯性也走同一条路：fling 的每一步都是一次 `scrollBy`，仍会先经过这里，
 * 所以「甩一下让 Tab 栏盖上去」是自然结果，不需要额外处理。
 *
 * 刻意**不做松手吸附**：头部收在半截是合法状态（封面还在，只是露出了更多内容），
 * 强行补一段动画只会让手感和预期不一致 —— 手指停在哪，头部就停在哪。
 */
@Composable
internal fun rememberHeaderCollapseConnection(collapse: HeaderCollapse): NestedScrollConnection {
    return remember(collapse) { HeaderCollapseConnection(collapse) }
}

private class HeaderCollapseConnection(
    private val collapse: HeaderCollapse,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // available.y < 0：手指上滑（内容是往后翻），这部分先给头部。
        if (available.y >= 0f) return Offset.Zero
        val used = collapse.collapseBy(-available.y)
        return if (used == 0f) Offset.Zero else Offset(0f, -used)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // available.y > 0：内容已经滚到顶，剩下这段下拖距离用来把头部放回来。
        if (available.y <= 0f) return Offset.Zero
        val used = collapse.expandBy(available.y)
        return if (used == 0f) Offset.Zero else Offset(0f, used)
    }
}

/**
 * 头部容器：按**完整高度**测量，只把露不出来的那部分裁掉。
 *
 * 内容固定在 (0, 0)，不随收缩上移 —— 封面钉在屏幕顶部、标题不动，
 * 变化的只有「可见到哪一行」。直接改测量高度会让头部内部（封面、信息列）每帧重新测量、
 * 重新排版，而这里内容尺寸自始至终不变，只是多了一次裁切。
 */
internal fun Modifier.collapsingHeader(collapse: HeaderCollapse): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val full = collapse.fullHeightPx.roundToInt()
        val visible = collapse.visiblePx.roundToInt().coerceIn(0, full)
        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, full))
        layout(constraints.maxWidth, visible) {
            placeable.place(0, 0)
        }
    }
