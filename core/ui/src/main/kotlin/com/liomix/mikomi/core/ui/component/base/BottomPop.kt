package com.liomix.mikomi.core.ui.component.base

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.spec.MikomiSheetSpec
import com.liomix.mikomi.core.designsystem.theme.MikomiElevation
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 底部弹窗：从屏幕底部升起，可上下拖动，松手吸附到调用方定义的锚点。
 *
 * 尺寸与手感参数（圆角、抓手 36×4、关闭阈值 0.55、橡皮筋 0.55）在 `MikomiSheetSpec` 里 ——
 * 它们属于「这个组件的固有规格」，不该由页面各调一次。
 *
 * 锚点用**比例**定义（面板高度 / 可用高度），而不是 dp：
 * 同一个 `0.5f` 在平板上是半屏、在手机上也是半屏，换设备不用重算。
 * 传进来的列表会被过滤、排序、去重，所以顺序写反了也不会出事。
 *
 * 拖拽手感是三条规则叠出来的：
 * 1. **锚点区间内 1:1 跟手**——手指走多少，面板就走多少；
 * 2. **越过区间立刻变「沉」**——超出部分按橡皮筋公式压缩，越拖越拉不动，
 *    但仍然跟手，松手必然弹回，不会真的拖出天际；
 * 3. **松手按速度优先**——甩一下直接跳到相邻锚点，慢慢松手则吸附最近的锚点；
 *    往下甩到底、或者拖到最低锚点的一半以下，就关闭。
 *
 * 展开状态是受控的：[expanded] 由调用方持有，关闭请求通过 [onDismissRequest] 回传。
 * 组件内部只有一个「当前露出高度」：拖拽写它、动画写它、绘制读它，
 * 所以拖拽打断动画、动画打断拖拽都不会出现两套位置打架。
 *
 * 用法：
 * ```
 * BottomPop(
 *     expanded = sheetOpen,
 *     onDismissRequest = { sheetOpen = false },
 *     anchors = listOf(0.4f, 0.9f),
 * ) {
 *     Text("内容")
 * }
 * ```
 *
 * @param expanded 是否展开。展开与关闭都会播放动画。
 * @param onDismissRequest 请求关闭：点遮罩、按返回键、向下甩出时调用。
 *   调用方应当把 [expanded] 置为 false；即使忘了，组件也会自己滑下去并卸载。
 * @param modifier 作用在浮层根布局上，一般不需要传。
 * @param anchors 吸附锚点，取值为面板高度占可用高度的比例（0~1），升序、去重由组件保证。
 *   打开时停在最高的那个锚点。
 * @param content 面板内容。面板高度等于最高锚点，内容超出请自己滚动。
 *   内容里的可滚动列表会优先吃掉竖直手势，滚到边界不会带动面板。
 */
@Composable
fun BottomPop(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchors: List<Float> = MikomiSheetSpec.Anchors,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fractions = remember(anchors) { anchors.sanitized() }

    // 面板当前露出的高度（px）。0 = 完全在屏幕外；最高锚点 = 完全展开。
    var visible by remember { mutableFloatStateOf(0f) }
    // 拖拽中的未阻尼目标值。只在拖拽期间有效，松手即失效。
    var dragRaw by remember { mutableFloatStateOf(0f) }
    // 浮层是否还需要组合：退场动画跑完之前不能拆掉 Popup，否则面板会瞬间消失。
    var mounted by remember { mutableStateOf(false) }
    // 当前驱动 visible 的动画。用普通 MutableState 而不加 by：只在回调与协程里读写，
    // 不参与组合，改动不会引起重组。
    val motionJob: MutableState<Job?> = remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(expanded) {
        if (expanded) mounted = true
    }

    /** 把 visible 动画到 [target]，返回这次动画的 Job。新动画会先取消旧动画。 */
    fun animateVisibleTo(target: Float): Job {
        motionJob.value?.cancel()
        val job = scope.launch {
            animate(
                initialValue = visible,
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ -> visible = value }
        }
        motionJob.value = job
        return job
    }

    /**
     * 关闭：先通知调用方，再自己滑下去卸载。
     *
     * 遮罩点击、返回键、向下甩出三条路径都走这里，行为才一致；
     * 调用方即使漏改 [expanded]，浮层也不会停在屏幕外继续占着窗口。
     */
    fun dismiss() {
        onDismissRequest()
        val job = animateVisibleTo(0f)
        scope.launch {
            job.join()
            mounted = false
        }
    }

    // 给长生命周期的手势检测器一个稳定入口：pointerInput 的 key 不能是每次重组都会变的局部函数，
    // 否则正在进行的拖拽会被打断。
    val dismissAction = remember { mutableStateOf({}) }
    dismissAction.value = ::dismiss

    if (!mounted) return

    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = { dismissAction.value() },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val availablePx = constraints.maxHeight.toFloat()
            val anchorPx = remember(fractions, availablePx) {
                fractions.map { it * availablePx }
            }
            val minAnchorPx = anchorPx.first()
            val maxAnchorPx = anchorPx.last()
            val dismissPx = minAnchorPx * MikomiSheetSpec.DismissFraction
            val velocityThresholdPx = with(density) { MikomiSheetSpec.SnapVelocity.toPx() }
            val panelHeight = with(density) { maxAnchorPx.toDp() }
            val velocityTracker = remember { VelocityTracker() }

            LaunchedEffect(expanded, anchorPx) {
                if (expanded) {
                    animateVisibleTo(maxAnchorPx)
                } else {
                    animateVisibleTo(0f).join()
                    mounted = false
                }
            }

            // 遮罩：透明度跟随露出高度，拖拽时同步变淡；动画期间只在绘制阶段读 state。
            // 绘制与点击关闭都由 SheetScrim 负责 —— 与侧栏共用同一份，深度不会跑偏。
            SheetScrim(
                progress = { visible / maxAnchorPx },
                onDismiss = { dismissAction.value() },
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(panelHeight)
                    .graphicsLayer {
                        // 面板始终按最高锚点的高度布局，靠平移露出不同比例：
                        // 只有绘制开销，拖拽过程不会重排内容、列表也不会跳位。
                        translationY = maxAnchorPx - visible
                    }
                    .pointerInput(anchorPx, availablePx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                motionJob.value?.cancel()
                                dragRaw = visible
                            },
                            onDragEnd = {
                                val velocity = velocityTracker.calculateVelocity().y
                                velocityTracker.resetTracking()
                                val target = resolveSnapTarget(
                                    visible = visible,
                                    velocity = velocity,
                                    anchors = anchorPx,
                                    velocityThreshold = velocityThresholdPx,
                                    dismissPx = dismissPx,
                                )
                                if (target == null) dismissAction.value() else animateVisibleTo(target)
                            },
                            onDragCancel = {
                                velocityTracker.resetTracking()
                                // 手势被系统打断时别把面板丢在半路，交给最近锚点收尾。
                                animateVisibleTo(anchorPx.minByOrNull { abs(it - visible) } ?: maxAnchorPx)
                            },
                        ) { change, dragAmount ->
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            // 手指向上是负值，露出高度要变大。
                            dragRaw -= dragAmount
                            visible = dragRaw.rubberBanded(
                                min = minAnchorPx,
                                max = maxAnchorPx,
                                dimension = availablePx,
                                floor = -maxAnchorPx,
                            )
                        }
                    },
                shape = RoundedCornerShape(
                    topStart = MikomiSheetSpec.CornerSize,
                    topEnd = MikomiSheetSpec.CornerSize,
                ),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = MikomiElevation.Raised,
                shadowElevation = MikomiElevation.Sheet,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SheetHandle()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        content = content,
                    )
                }
            }
        }
    }
}

/**
 * 顶部抓手：拖拽能力的视觉提示。
 *
 * 组件自带而不是让调用方画：拖拽是骨架能力，如果提示要靠内容自己补，
 * 每个页面都得记得加一次，还各不相同。
 */
@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MikomiSheetSpec.HandlePadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = MikomiSheetSpec.HandleWidth,
                    height = MikomiSheetSpec.HandleHeight,
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

/**
 * 松手后该停在哪。
 *
 * @return 目标锚点高度（px）；`null` 表示关闭。
 *
 * 先看位置再看速度：拖得太低就是「想关掉」，无论手上还有没有速度。
 * 有速度时只吸相邻锚点——甩一下跨两档会让人失去对面板的控制感。
 */
private fun resolveSnapTarget(
    visible: Float,
    velocity: Float,
    anchors: List<Float>,
    velocityThreshold: Float,
    dismissPx: Float,
): Float? {
    if (visible <= dismissPx) return null

    if (abs(velocity) >= velocityThreshold) {
        return if (velocity > 0f) {
            // 向下甩：吸到当前位置以下的最近锚点；没有更低的就关闭。
            anchors.lastOrNull { it < visible }
        } else {
            // 向上甩：吸到当前位置以上的最近锚点；已经是最高的就停在最高。
            anchors.firstOrNull { it > visible } ?: anchors.last()
        }
    }

    return anchors.minByOrNull { abs(it - visible) }
}

/**
 * 橡皮筋阻尼：区间内原样跟手，越界部分按 `d · (1 - 1 / (x·c/d + 1))` 压缩。
 *
 * 用「绝对量映射」而不是「每个增量乘系数」：增量式阻尼会随拖拽次数变化，
 * 往回拖时还会对不上原位。绝对值映射可逆——拖多远、回多少都由 raw 决定。
 *
 * @param dimension 施加阻尼的尺度，这里取整块可用高度。
 * @param floor 向下拖的下限：面板已经完全出屏，再拖也没有视觉意义。
 */
private fun Float.rubberBanded(
    min: Float,
    max: Float,
    dimension: Float,
    floor: Float,
): Float = when {
    this < min -> (min - rubberBandDistance(min - this, dimension)).coerceAtLeast(floor)
    this > max -> max + rubberBandDistance(this - max, dimension)
    else -> this
}

/** 越界距离 → 实际可移动距离。距离越长收益越小，永远拉不满一屏。 */
private fun rubberBandDistance(distance: Float, dimension: Float): Float {
    if (distance <= 0f || dimension <= 0f) return 0f
    return dimension * (1f - 1f / (distance * MikomiSheetSpec.RubberBandCoefficient / dimension + 1f))
}

/** 过滤非法值、夹到有效区间、升序去重；调用方给空列表时退回默认锚点。 */
private fun List<Float>.sanitized(): List<Float> = asSequence()
    .filter { it.isFinite() && it > 0f }
    .map { it.coerceIn(MikomiSheetSpec.MinAnchorFraction, 1f) }
    .distinct()
    .sorted()
    .toList()
    .ifEmpty { MikomiSheetSpec.Anchors }
