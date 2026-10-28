package com.liomix.mikomi.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

/**
 * 单行文本，超宽时自动横向无限滚动（marquee），未超宽时静态显示。
 *
 * 对齐 Dart 版成功实现（`SingleChildScrollView` + `ScrollController`）：
 * 用 Compose 的 [horizontalScroll] + [ScrollState] 作为**真正的横向滚动容器**，
 * 由滚动容器精确报告可滚动像素（`maxValue`），不再手工计算「文本宽 - 容器宽」
 * 再平移文字——彻底避开字体填充 / 测量口径 / 锚点偏移等黑盒，首字天然从容器
 * 左缘 x=0 开始完整显示。
 *
 * - 内层 [Text] 不设宽度约束，让 [horizontalScroll] 测得完整单行宽并产生溢出；
 * - 溢出判定：`scrollState.maxValue > 0` 即存在溢出；
 * - 动画：`scrollState.animateScrollTo(maxValue)`（suspend）直接平滑滚到末尾，
 *   再由 `scrollTo(0)` 瞬间回到起点，与 Dart 版 `animateTo`/`jumpTo` 一一对应；
 * - 循环：静止 [START_PAUSE_MILLIS] → 滚到末尾（[SCROLL_MILLIS]）→
 *   末尾停留 [END_PAUSE_MILLIS] → 回到起点；
 * - 未溢出时零动画开销（懒列表大量 item 不空转）；
 * - [userScrollEnabled] 为 false 时关闭手动拖动，仅保留自动滚动，
 *   避免在 HorizontalPager 等横向容器里抢占外层滑动手势。
 */
@Composable
fun ScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    userScrollEnabled: Boolean = true,
) {
    val scrollState = rememberScrollState()

    // 记录容器宽（布局后才有意义），用于溢出判定。
    var containerWidth by remember { mutableIntStateOf(0) }
    val maxScroll = scrollState.maxValue
    val needsScroll = containerWidth > 0 && maxScroll > 0

    LaunchedEffect(needsScroll, maxScroll) {
        if (!needsScroll) {
            scrollState.scrollTo(0)
            return@LaunchedEffect
        }
        while (true) {
            // 每轮从起点开始；中途离屏会取消本协程，重新进入先归零。
            scrollState.scrollTo(0)
            delay(START_PAUSE_MILLIS)
            scrollState.animateScrollTo(
                value = maxScroll,
                animationSpec = tween(durationMillis = SCROLL_MILLIS, easing = LinearEasing),
            )
            delay(END_PAUSE_MILLIS)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width },
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.horizontalScroll(scrollState, enabled = userScrollEnabled),
        )
    }
}

/** 每轮滚动前的静止时长（首字完整可见阶段）。 */
private const val START_PAUSE_MILLIS = 2_000L

/** 自起始位滚到末尾位的时长。 */
private const val SCROLL_MILLIS = 3_000

/** 滚到末尾后的短暂停留（此时首字在盒外，故取短）。 */
private const val END_PAUSE_MILLIS = 500L
