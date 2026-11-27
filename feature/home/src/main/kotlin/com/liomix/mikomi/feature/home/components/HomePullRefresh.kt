package com.liomix.mikomi.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Elevation
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.Stroke

/**
 * 首页下拉刷新：容器、指示器、内容列的拉伸 —— 下拉这件事的三种表现放在一处。
 *
 * ## 为什么不用 M3 的默认箭头
 *
 * 默认指示器是一个带箭头的圆形进度（`CircularArrowProgressIndicator`），它表达的是
 * 「再拉一点就到阈值」这个**操作提示**。首页的下拉不是一次需要教学的操作：用户想的是
 * 「把内容拽下来看看有没有新的」。一个指着下方的箭头读起来像按钮。
 *
 * 这里换成一个**纯进度环**：拉到一半是弧，刷新时事弧在转。没有方向、没有文案，
 * 只表达「在拉」和「在刷」两件事。
 *
 * ## 拉伸
 *
 * 见 [pullStretch]：整列内容（头图 / 快捷栏 / 海报）从顶部被拽长，而不是整页往下平移。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    state: PullToRefreshState,
    topInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullProgress(
                fraction = { state.distanceFraction },
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // 让开状态栏：刘海机上贴着屏幕顶端的那条弧会被吃掉一半。
                    .padding(top = topInset + Spacing.Sm),
            )
        },
        content = content,
    )
}

/**
 * 下拉指示器：一个圆底 + 一圈进度环。
 *
 * - 未刷新：弧长跟随下拉距离（`progress`），读值放在 lambda 里 —— 每帧变的是绘制，
 *   不是组合；
 * - 刷新中：切成不确定态，弧自己转。
 *
 * [fraction] 与 [isRefreshing] 分开传，是因为它们的**变化频率**不同：
 * 距离每帧变，刷新状态一次刷新只变两次。合成一个参数会让刷新状态也被卷进绘制读取。
 */
@Composable
private fun PullProgress(
    fraction: () -> Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.graphicsLayer {
            // 拉到 10% 才开始显现：手指刚碰到时冒出一整块圆，像误触。
            // 刷新中必须保持全显 —— 此时 distanceFraction 已经归零了。
            val appear = if (isRefreshing) 1f else ((fraction() - 0.1f) / 0.9f).coerceIn(0f, 1f)
            alpha = appear
            val scale = 0.72f + 0.28f * appear
            scaleX = scale
            scaleY = scale
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = Elevation.Raised,
    ) {
        Box(
            modifier = Modifier.size(IndicatorSize),
            contentAlignment = Alignment.Center,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(RingSize),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    strokeWidth = Stroke.Thick,
                )
            } else {
                CircularProgressIndicator(
                    progress = { fraction().coerceIn(0f, 1f) },
                    modifier = Modifier.size(RingSize),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    strokeWidth = Stroke.Thick,
                )
            }
        }
    }
}

/**
 * 下拉时把内容列从顶部**拉长**。
 *
 * 锚点固定在顶部（`TransformOrigin(0.5f, 0f)`）：状态栏与顶栏不动，头图、快捷栏、
 * 海报一起被拽长 —— 这才是「从上面拽住一块布」的手感，而不是整页往下平移。
 *
 * 系数只有 [PULL_STRETCH]（4%）：再多，2:3 的海报就开始读得出纵向变形。
 * 上限不取 `distanceFraction` 的原值 —— 它有回弹与过冲，超过 1 之后继续拉长会让
 * 头图把整个屏幕挤掉。
 *
 * 读值必须放在 `graphicsLayer` 里：下拉距离每帧都在变，组合期读会让整个网格
 * （含十来个 item）跟着重组，那正是「下拉时掉帧」的来源。
 */
internal fun Modifier.pullStretch(fraction: () -> Float): Modifier = graphicsLayer {
    val pulled = fraction().coerceIn(0f, 1f)
    scaleY = 1f + pulled * PULL_STRETCH
    transformOrigin = TransformOrigin(0.5f, 0f)
}

/** 拉到底（阈值）时的纵向拉伸比例。 */
private const val PULL_STRETCH = 0.04f

/** 圆底直径：托得住 22dp 的环，又不至于像个按钮。 */
private val IndicatorSize = 44.dp

/** 环直径：留出 11dp 的圆底留白，与进度环的粗描边（[Stroke.Thick]）比例才顺眼。 */
private val RingSize = 22.dp
