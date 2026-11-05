package com.liomix.mikomi.core.ui.component.base

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiElevation
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 侧栏弹窗贴的是哪一侧。
 *
 * 用 `Start` / `End` 而不是左右：布局方向变了（RTL）之后，锚点要跟着屏幕镜像，
 * 而「起始侧」在任何语言里都成立。中文环境等价于左 / 右。
 */
enum class SideBarSide {
    /** 起始侧（中文界面是左边）。 */
    Start,

    /** 结束侧（中文界面是右边）。 */
    End,
}

/** [SideBarPop] 的默认尺寸与节奏。 */
object SideBarPopDefaults {
    /** 面板宽度。 */
    val Width = 320.dp

    /**
     * 面板宽度占可用宽度的上限。
     *
     * 窄屏上 320dp 会几乎铺满，侧栏要求的「露出一点主界面」就没了；
     * 用比例兜底，面板任何屏幕尺寸下都不会变成整页。
     */
    const val MaxWidthFraction = 0.86f

    /** 面板靠屏幕内侧的两个圆角。贴边那侧不圆，否则会露出底下的页面。 */
    val CornerSize = MikomiRadius.Xxl

    /** 入场时长。 */
    const val EnterMillis = MikomiMotion.Duration.Medium

    /** 退场时长。比入场短，关闭动作要显得更利落。 */
    const val ExitMillis = MikomiMotion.Duration.Short

    /** 遮罩最深不透明度。再深就看不清底下的页面了。 */
    const val ScrimAlpha = 0.32f
}

/**
 * 通用侧栏弹窗：从屏幕一侧滑入的面板 + 可点击关闭的遮罩。
 *
 * 组件拥有的是**骨架**：全屏遮罩、贴边面板、进出场动画、返回键关闭、退场播完才销毁。
 * 调用方拥有的是**内容与方向**：面板里放什么、贴哪一侧、多宽。面板不内置标题栏——
 * 侧栏的头部形态（大标题 / 搜索框 / 头像）差异太大，塞进来只会变成一堆开关参数。
 *
 * 状态是受控的：[expanded] 由调用方持有，关闭请求通过 [onDismissRequest] 回传。
 * 组件内部只在需要播放退场动画时缓存一份「可见」状态，不会自己改 [expanded]。
 *
 * 关闭方式有三种：点遮罩、系统返回键、把 [expanded] 置为 false。
 * **不做拖拽关闭**：侧栏内容经常自己带横滑（Tab、轮播、Pager），
 * 面板级拖拽会把那些手势吞掉，收益不抵冲突。
 *
 * 用法：
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     MainContent()
 *     SideBarPop(
 *         expanded = drawerOpen,
 *         onDismissRequest = { drawerOpen = false },
 *     ) {
 *         // 这里已经是 ColumnScope，撑满剩余高度用 Modifier.weight(1f)
 *         LazyColumn(Modifier.weight(1f)) { ... }
 *     }
 * }
 * ```
 *
 * @param expanded 是否展开。展开 / 收起都会播放动画。
 * @param onDismissRequest 请求关闭：点遮罩或按返回键时调用。
 * @param modifier 作用在浮层根布局上；尺寸、定位、间距由组件自己保证，一般不需要传。
 * @param side 面板贴哪一侧。
 * @param width 面板宽度，超过屏宽 [SideBarPopDefaults.MaxWidthFraction] 时按比例收窄。
 * @param content 面板内容。可滚动列表请自己给 `Modifier.weight(1f)`，组件不替你滚。
 */
@Composable
fun SideBarPop(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: SideBarSide = SideBarSide.End,
    width: Dp = SideBarPopDefaults.Width,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 用 MutableTransitionState 而不是 AnimatedVisibility：遮罩和面板要共享同一条进度，
    // 而且退场期间浮层必须保持组合，否则 expanded 变 false 的一瞬间面板就消失了。
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = expanded

    val transition = rememberTransition(transitionState, label = "SideBarPop")
    val progress by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) {
                    SideBarPopDefaults.EnterMillis
                } else {
                    SideBarPopDefaults.ExitMillis
                },
                easing = FastOutSlowInEasing,
            )
        },
        label = "progress",
    ) { visible -> if (visible) 1f else 0f }

    // 收起后动画还在跑：继续组合；动画跑完且两端都是 false 才真正移除。
    if (!transitionState.currentState && !transitionState.targetState && transitionState.isIdle) {
        return
    }

    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            // focusable 才能收到返回键，也才能让遮罩挡住下面页面的点击。
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val scrimColor = MaterialTheme.colorScheme.scrim

            // 遮罩：透明度在绘制阶段读 progress，动画全程不重组，只重绘这一层。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(scrimColor.copy(alpha = SideBarPopDefaults.ScrimAlpha * progress))
                    }
                    .pointerInput(onDismissRequest) {
                        detectTapGestures { onDismissRequest() }
                    },
            )

            // 面板宽度既受调用方的 width 约束，也受屏幕比例约束，窄屏不会铺满整页。
            val panelWidth = minOf(width, maxWidth * SideBarPopDefaults.MaxWidthFraction)
            val travelSign = if (side == SideBarSide.Start) -1f else 1f

            Surface(
                modifier = Modifier
                    .align(
                        if (side == SideBarSide.Start) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        },
                    )
                    .fillMaxHeight()
                    .width(panelWidth)
                    .graphicsLayer {
                        // 只改平移不改尺寸：不触发布局测量，整段动画只有绘制开销。
                        translationX = travelSign * size.width * (1f - progress)
                    },
                shape = side.panelShape(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = MikomiElevation.Raised,
                shadowElevation = MikomiElevation.Overlay,
            ) {
                Column(modifier = Modifier.fillMaxSize(), content = content)
            }
        }
    }
}

/** 贴边那侧不圆角：圆角露出来会看到底下的页面，像面板浮在半空。 */
private fun SideBarSide.panelShape(): Shape = when (this) {
    SideBarSide.Start -> RoundedCornerShape(
        topEnd = SideBarPopDefaults.CornerSize,
        bottomEnd = SideBarPopDefaults.CornerSize,
    )

    SideBarSide.End -> RoundedCornerShape(
        topStart = SideBarPopDefaults.CornerSize,
        bottomStart = SideBarPopDefaults.CornerSize,
    )
}
