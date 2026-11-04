package com.liomix.mikomi.navigation

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import kotlin.math.max

/**
 * 页面入场的「手绘帧」，逐条对齐 Telegram `ActionBarLayout.startLayoutAnimation()`。
 *
 * Telegram 那套不用 `ValueAnimator`：它自己往主线程丢 Runnable，每帧读一次系统时间算
 * `dt`，把 `dt / 150` 累加到进度上，再自己决定还要不要下一帧。
 *
 * ```java
 * if (dt > 40 && first) dt = 0;      // 首帧常常在建视图，整帧丢掉
 * else if (dt > 18) dt = 18;         // 任何一帧最多推进 18ms
 * animationProgress += dt / duration;
 * containerView.setAlpha(interpolated);
 * containerView.setTranslationX(dp(48) * (1.0f - interpolated));
 * ```
 *
 * Compose 的 `tween` 是按**墙钟**插值的：某一帧卡了 100ms，它的进度就直接跳 100ms，
 * 画面上表现为「顿一下然后窜一截」。这里复刻的是 Telegram 的做法——卡顿的帧只推进
 * 18ms，整段动画被拉长，但每一帧走过的距离均匀。慢而匀，好过快而跳。
 *
 * 位移与透明度用同一个进度、同一条曲线，所以读起来是「一整块内容推进来」，
 * 而不是「一边变亮一边往里飘」。
 */

/** 标称时长，对应 Telegram 的 `float duration = 150.0f`。 */
internal const val PAGE_MOTION_MILLIS = 150

/** 新页入场的水平行程，对应 Telegram 的 `dp(48)`。 */
internal val PAGE_MOTION_TRAVEL = 48.dp

/**
 * 单帧最多推进的时长（Telegram: `else if (dt > 18) dt = 18`）。
 *
 * 掉帧时宁可把这段动画拉长，也不让画面一格跨出一截。这是整套手感的来源，
 * 也是它和 `tween` 唯一的实质差别。
 */
private const val MAX_FRAME_STEP_MILLIS = 18f

/** 首帧晚超过 40ms 就整帧丢掉（Telegram: `if (dt > 40 && first) dt = 0`）。 */
private const val FIRST_FRAME_DROP_MILLIS = 40f

/**
 * 内容淡入的倍率：进度乘上去后截到 1，让内容在整段位移走完之前就变实心。
 *
 * 取 2.5 意味着内容在约 40% 的时长（60ms 上下）内淡完，仍有两三帧的柔和过渡，
 * 但不会出现「先看到一块空底色、内容再慢慢长出来」的空页观感。
 */
private const val CONTENT_FADE_BOOST = 2.5f

/**
 * 转场窗口：旧页留在组合里的时长。
 *
 * 手绘帧不占用任何 Compose 动画，而框架只按「还有没有动画在跑」决定何时回收旧页，
 * 所以旧页的存活时间由这个窗口决定（`MikomiNavHost` 里给它挂了一个不动它透明度的退场）。
 *
 * 它现在只负责一件事：新页入场时左侧会留出 48dp 让位缝，缝里要显示上一页而不是
 * 根容器的纯底色，所以旧页必须活到新页落位为止。
 *
 * 取 2 倍标称值：30fps 的设备上每帧被钳到 18ms，整段跑到约 300ms 正好塞得下——
 * 这正是 Telegram 在慢机器上的行为，动画变长但依旧均匀。再慢就是真的卡了，
 * 由 [runPageMotionFrames] 的窗口兜底保证它一定会落位。
 */
internal const val PAGE_MOTION_WINDOW_MILLIS = 300

/** Android `DecelerateInterpolator(1.0f)` 的等价曲线：起步最快、尾部平缓。 */
internal val PageMotionEasing = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }

/** 本次转场是「进入」还是「返回 / 替换」。 */
internal enum class PageMotionDirection { Enter, Back }

/**
 * 当前这次转场的方向，由 `MikomiNavHost` 按返回栈长度给出。
 *
 * 只在页面**首次**组合那一刻有意义：装饰器会把它记住，后续重组不再跟变。
 */
internal val LocalPageMotionDirection = compositionLocalOf { PageMotionDirection.Enter }

/**
 * 跑一段 [PAGE_MOTION_MILLIS] 的手绘帧，每帧回调一次缓动后的进度。
 *
 * `withFrameNanos` 是 Compose 的帧时钟，语义上等价于 Telegram 那个自驱动的
 * `runOnUIThread` 递归：每次回调就是「又该画下一帧了」。
 */
internal suspend fun runPageMotionFrames(onProgress: (Float) -> Unit) {
    val startedAt = withFrameNanos { it }
    var lastFrame = startedAt
    var firstFrame = true
    var progress = 0f
    while (progress < 1f) {
        var now = 0L
        withFrameNanos { now = it }
        val wallStep = ((now - lastFrame) / 1_000_000f).coerceAtLeast(0f)
        lastFrame = now
        val dt = when {
            // 首帧在建视图，这一帧的距离不该算进动画里。
            firstFrame && wallStep > FIRST_FRAME_DROP_MILLIS -> 0f
            wallStep > MAX_FRAME_STEP_MILLIS -> MAX_FRAME_STEP_MILLIS
            else -> wallStep
        }
        firstFrame = false
        val windowLeft = PAGE_MOTION_WINDOW_MILLIS - (now - startedAt) / 1_000_000f
        val step = when {
            // 窗口到点：这一帧收尾。宁可最后紧一点，也不能让新页还在滑动时
            // 被撤掉旧页——那时左侧让位缝里会露出根容器的纯底色。
            windowLeft <= dt -> 1f - progress
            // 平时按标称速度走；只有掉帧严重到按标称速度跑不完窗口时，才改按
            // 「剩余距离 / 剩余时间」定速。均匀推进的优先级高于严格 150ms。
            else -> max(dt / PAGE_MOTION_MILLIS, (1f - progress) * dt / windowLeft)
        }
        progress = (progress + step).coerceAtMost(1f)
        onProgress(PageMotionEasing.transform(progress))
    }
}

/**
 * 新页入场：从「右移 [travelPx] 像素」按手绘帧走回原位，内容同时淡入。
 *
 * 与 Telegram 的一处**有意偏离**：Telegram 把 `alpha` 打在整页（含背景）上，于是新页
 * 半透明的那几帧里，用户看到的是「上一页 + 新页」叠在一起（底色越浅越明显）。
 * 这里把底色和不透明性摘出来：底色从第一帧就是实心的，只有内容淡入。
 *
 * 这样在数学上就不可能透出上一页（新页自带不透明底色，铺满全屏），而「内容淡入」
 * 保留了 Telegram 那种柔和的起身感。代价是左侧 48dp 的让位缝里看到的是上一页，
 * 和 Telegram 一致。
 */
@Composable
private fun PageMotionSurface(travelPx: Int, content: @Composable () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { runPageMotionFrames { progress = it } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = travelPx * (1f - progress) }
            // 底色必须画在 layer 内部（写在 graphicsLayer 之后），才会跟着一起位移。
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 内容比位移先淡完：等位移走完才变实心的话，中间会先看到一块空底色。
                .graphicsLayer { alpha = (progress * CONTENT_FADE_BOOST).coerceAtMost(1f) },
        ) {
            content()
        }
    }
}

/**
 * 给每个页面挂上入场动画。
 *
 * 用 [NavEntryDecorator] 而不是逐个 destination 手包：转场是「每个页面都要有」的东西，
 * 挂在入口上就不必在每个 `entry<...>` 里重复一遍，也不会漏。
 *
 * 返回时目标页必须**直接可见**（Telegram 的返回只动被关闭的那一页），而目标页在返回
 * 的那一刻恰好也是「首次组合」，所以方向必须冻结：不冻结就会反过来播一次入场。
 */
internal fun telegramPageMotionDecorator(): NavEntryDecorator<NavKey> =
    NavEntryDecorator { entry ->
        // 先读再冻结：`remember` 的 lambda 不是 @Composable 上下文，读不了 CompositionLocal。
        val requested = LocalPageMotionDirection.current
        val direction = remember { requested }
        if (direction == PageMotionDirection.Back) {
            entry.Content()
        } else {
            val travelPx = with(LocalDensity.current) { PAGE_MOTION_TRAVEL.roundToPx() }
            PageMotionSurface(travelPx) { entry.Content() }
        }
    }
