package com.liomix.mikomi.feature.detail

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** 吸附锚点数量：初始（展开）/ 顶端（收到最矮）。 */
private const val ANCHOR_COUNT = 2

/** 甩过这个速度就「翻一格」，而不是只看离哪个锚点近（px/s）。 */
private const val ANCHOR_VELOCITY_THRESHOLD = 380f

/** 吸附动画：硬一点、几乎不过冲 —— 只有两档时，吸附要「啪」地到位，粘滞感就是失败。 */
private val AnchorSpring = spring<Float>(
    dampingRatio = 0.95f,
    stiffness = 900f,
)

/** 橡皮筋回弹：允许一次可见的回弹过冲，这就是「拉过头被弹回来」的来源。 */
private val StretchSpring = spring<Float>(
    dampingRatio = 0.6f,
    stiffness = Spring.StiffnessMedium,
)

/**
 * 头部收缩状态。
 *
 * 与「折叠工具栏」的常规做法不同，这里**头部内容不位移**：封面始终贴在屏幕顶部不动，
 * 只是可见高度从 [fullHeightPx] 一路收到 `minVisible`，收掉的部分由 Tab 栏从下往上盖住。
 * 因此看起来是「Tab 栏在封面上滑动」，而不是「封面被推走」—— banner 一直活着，也永远
 * 不会被收没。
 *
 * 有两个浮点状态：
 * - [offsetPx]：收缩进度，松手后吸附到 2 个锚点之一（全展开 / 收到最矮）；
 * - [stretchPx]：整页的橡皮筋位移，只在「内容已经到边界」时出现，松手弹回 0。
 *
 * 所有读取都发生在**布局 / 绘制阶段**（[CollapsingHeader]、Tab 栏的 `offset`、
 * 整页的 `offset`、海报的 `graphicsLayer`），滚动过程中不会发生重组。
 *
 * @param fullHeightPx 头部完整高度（展开态）
 * @param minVisiblePx 收到底时仍保留的高度：封面 + 标题要继续露出来，不能被 Tab 栏吃干净
 * @param stretchLimitPx 橡皮筋最大位移：到这个距离就几乎拉不动了
 */
@Stable
internal class HeaderCollapse(
    val fullHeightPx: Float,
    minVisiblePx: Float,
    private val stretchLimitPx: Float,
) {
    /** 收缩行程：展开 → 收到底走过的距离。 */
    val travelPx: Float = (fullHeightPx - minVisiblePx).coerceAtLeast(0f)

    /** 锚点间距（三个锚点等距）。 */
    private val anchorStep: Float = travelPx / (ANCHOR_COUNT - 1)

    /** 已收起距离：0 = 完全展开，[travelPx] = 收到底。 */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /**
     * 整页位移：正 = 被往下拽（内容已到顶），负 = 被往上拽（内容已到底）。
     *
     * 它不是滚动，只是「拉过头」的临时形变，松手一定回到 0。
     */
    var stretchPx by mutableFloatStateOf(0f)
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

    /**
     * 拉出橡皮筋：越接近 [stretchLimitPx] 越拉不动，手指往回走时则是 1:1（不阻尼）。
     *
     * @param delta 带符号的手指位移，正 = 往下
     * @return 实际产生的位移（同号），调用方拿它当「已消费」上报
     */
    fun stretchBy(delta: Float): Float {
        if (delta == 0f) return 0f
        // 同向 = 继续拉（要阻尼）；反向 = 往回放（不阻尼，否则回弹会发黏）。
        val sameDirection = delta * stretchPx >= 0f
        val factor = if (sameDirection) {
            (1f - abs(stretchPx) / stretchLimitPx).coerceIn(0f, 1f)
        } else {
            1f
        }
        val next = (stretchPx + delta * factor).coerceIn(-stretchLimitPx, stretchLimitPx)
        val used = next - stretchPx
        stretchPx = next
        return used
    }

    /** 把橡皮筋位移往回放（1:1）：返回实际吃掉的位移（与 delta 同号）。 */
    fun pullStretchBy(delta: Float): Float {
        if (stretchPx == 0f || delta == 0f) return 0f
        val next = if (stretchPx > 0f) {
            (stretchPx + delta).coerceAtLeast(0f)
        } else {
            (stretchPx + delta).coerceAtMost(0f)
        }
        val used = next - stretchPx
        stretchPx = next
        return used
    }

    /**
     * 松手后吸附到哪个锚点。
     *
     * 慢速松手：就近吸附，两档之间没有半截状态。
     * 快速甩动（|velocity| 过阈值）且这一下确实推动了头部：直接翻过去 ——
     * 只按距离算的话，推到一半再补一点会被弹回出发档，手感像「推不动」。
     *
     * @param velocity 松手速度（px/s，负 = 上滑）。手势没动过头部时传 0，
     *   避免「在内容里 fling」把头部一起带跑。
     */
    fun nearestAnchor(velocity: Float = 0f): Float {
        if (anchorStep <= 0f) return 0f
        val raw = offsetPx / anchorStep
        val index = when {
            // 0.02 是迟滞：正好停在锚点上时甩一下也要走一格。
            velocity <= -ANCHOR_VELOCITY_THRESHOLD -> ceil(raw + 0.02f)
            velocity >= ANCHOR_VELOCITY_THRESHOLD -> floor(raw - 0.02f)
            else -> raw.roundToInt().toFloat()
        }.coerceIn(0f, (ANCHOR_COUNT - 1).toFloat())
        return index * anchorStep
    }

    suspend fun animateOffsetTo(target: Float) {
        if (target == offsetPx) return
        animate(offsetPx, target, animationSpec = AnchorSpring) { value, _ ->
            offsetPx = value.coerceIn(0f, travelPx)
        }
    }

    suspend fun animateStretchTo(target: Float) {
        if (target == stretchPx) return
        animate(stretchPx, target, animationSpec = StretchSpring) { value, _ ->
            stretchPx = value.coerceIn(-stretchLimitPx, stretchLimitPx)
        }
    }
}

/**
 * 收缩状态的持有者。
 */
@Composable
internal fun rememberHeaderCollapse(
    fullHeight: Dp,
    minVisible: Dp,
    stretchLimit: Dp,
): HeaderCollapse {
    val density = LocalDensity.current
    val fullHeightPx = with(density) { fullHeight.toPx() }
    val minVisiblePx = with(density) { minVisible.toPx() }
    val stretchLimitPx = with(density) { stretchLimit.toPx() }
    return remember(fullHeightPx, minVisiblePx, stretchLimitPx) {
        HeaderCollapse(
            fullHeightPx = fullHeightPx,
            minVisiblePx = minVisiblePx,
            stretchLimitPx = stretchLimitPx,
        )
    }
}

/**
 * 把内容区的垂直滚动分给头部与橡皮筋。
 *
 * 上滑在 `onPreScroll`（子级之前）里先吃：Tab 栏立刻顺着封面往上盖，内容留在原地；
 * 收到底后 `collapseBy` 返回 0，剩余增量原样流向内容 —— 「先收头，再滚内容」是连续的。
 * 下拉走 `onPostScroll`：内容真的回到顶部、还有多余距离时才放回头部；
 * 头部都放完了还有距离，才开始拉橡皮筋。
 *
 * 两端的橡皮筋都发生在 `onPostScroll`（说明子级已经消费不动了）：
 * 内容到顶时继续下拉 → 整页被往下拽；内容到底时继续上滑 → 整页被往上拽。
 *
 * 松手后 `onPostFling` 统一收尾：橡皮筋弹回 0、头部吸附到最近的锚点。
 * 这两件事放在独立协程里并行做，fling 本身立刻结束，不拖着页面一起等。
 */
@Composable
internal fun rememberHeaderCollapseConnection(collapse: HeaderCollapse): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    return remember(collapse, scope) { HeaderCollapseConnection(collapse, scope) }
}

private class HeaderCollapseConnection(
    private val collapse: HeaderCollapse,
    private val scope: CoroutineScope,
) : NestedScrollConnection {

    /** 松手后的收尾动画。用户重新按下并拖动时立刻取消，否则两边会同时写同一个状态。 */
    private var settleJob: Job? = null

    /** 本次手势松手时的速度（`onPreFling` 拿得到完整值，`onPostFling` 的 available 通常是 0）。 */
    private var flingVelocity = 0f

    /** 本次手势里头部有没有真的动过 —— 决定甩动要不要「翻一格」。 */
    private var movedHeader = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        if (dy == 0f) return Offset.Zero
        settleJob?.cancel()

        var consumed = 0f
        var rest = dy

        // 先把橡皮筋收回去（手指方向与过冲相反时）。这一段必须是 1:1，
        // 否则「拉出来一点、推回去却推不动」会非常别扭。
        if (collapse.stretchPx != 0f && dy * collapse.stretchPx < 0f) {
            val used = collapse.pullStretchBy(dy)
            rest -= used
            consumed += used
            // 还没放完：这一帧就到这里，别再往下分给头部/内容。
            if (collapse.stretchPx != 0f || rest == 0f) return Offset(0f, consumed)
        }

        // 上滑：先收缩头部（下拉方向不在这里处理，要让内容先滚到顶）。
        if (rest < 0f) {
            val used = collapse.collapseBy(-rest)
            if (used > 0f) {
                consumed -= used
                movedHeader = true
            }
        }
        return if (consumed == 0f) Offset.Zero else Offset(0f, consumed)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val dy = available.y
        if (dy == 0f) return Offset.Zero
        settleJob?.cancel()

        if (dy > 0f) {
            // 内容已经到顶：先把头部放回来。
            val used = collapse.expandBy(dy)
            if (used > 0f) movedHeader = true
            val rest = dy - used
            if (rest <= 0f) return if (used > 0f) Offset(0f, used) else Offset.Zero
            // 头部也放完了 —— 再拉就是橡皮筋（整页往下走一点）。
            val stretched = collapse.stretchBy(rest)
            // 拉到极限时 stretchBy 返回 0：这里主动吃掉剩下的位移，
            // 否则底层列表会再叠一层系统自带的回弹，屏幕上就是两套动画抢一个手势。
            return Offset(0f, used + if (stretched == 0f) rest else stretched)
        }

        // 内容到底还在上滑：整页被往上拽。
        val stretched = collapse.stretchBy(dy)
        return Offset(0f, if (stretched == 0f) dy else stretched)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        flingVelocity = available.y
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // 速度与「动过头部」都只服务于这一次手势，取完立刻清掉，
        // 否则下一次慢速松手会沿用上一次的甩动方向。
        val velocity = if (movedHeader) flingVelocity else 0f
        flingVelocity = 0f
        movedHeader = false

        settleJob?.cancel()
        settleJob = scope.launch {
            // 两件事并行：橡皮筋必须先回到 0，头部吸附的是收缩位置，两者互不干扰。
            launch { collapse.animateStretchTo(0f) }
            launch { collapse.animateOffsetTo(collapse.nearestAnchor(velocity)) }
        }
        return Velocity.Zero
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
