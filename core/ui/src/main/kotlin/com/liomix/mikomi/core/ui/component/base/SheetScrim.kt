package com.liomix.mikomi.core.ui.component.base

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import com.liomix.mikomi.core.designsystem.spec.MikomiScrimSpec

/*
 * 浮层共用的遮罩层。
 *
 * ## 为什么要有这一个文件
 *
 * 底部面板与侧栏此前各写了一份同样的东西：一个铺满的 `Box`，`drawBehind` 里按进度
 * 画半透明黑，再挂一个 `detectTapGestures` 把点击转成关闭请求。两份代码逐字相同，
 * 只有「进度从哪来」不同 —— 这不是巧合，是同一个组件被复制了两次。
 *
 * 复制带来的问题不是行数，而是**三者必须同时正确**：遮罩深度、点击关闭、以及
 * 「进度变化只重绘不重组」这条性能约束。任何一处被单独改动，两个浮层的手感就分叉，
 * 而且分叉得很隐蔽（一个是 0.28、一个是 0.32，肉眼几乎看不出来）。
 *
 * ## 进度为什么是 lambda
 *
 * 浮层的展开进度是**逐帧变化**的值（拖拽、吸附动画）。若作为 `Float` 参数传入，
 * 调用方每次重组都会重建这一层，整段动画就是几十次重组；传 lambda 则是把读取
 * 推迟到 [drawBehind] 内部 —— 只有绘制阶段订阅这个状态，动画全程只重绘、不重组。
 *
 * 这与 `Telegram` 的 `BottomSheet` 把遮罩当成一个「跟着 progress 画的层」是同一个思路，
 * 区别只是那边手写 `invalidate()`，这里靠 Compose 的快照订阅。
 *
 * ## 为什么不 public
 *
 * 它是浮层的**骨架**，不是可独立使用的组件：调用方只可能通过 [BottomPop] 或
 * [SideBarPop] 间接用到它。放成 public 会让人以为「可以拿它自己拼一个浮层」，
 * 而拼出来的东西不会处理返回键、不会处理按压穿透、也不会在退场播完后销毁自己。
 */

/**
 * 浮层遮罩：铺满父容器，按 [progress] 画出半透明黑，点击即请求关闭。
 *
 * @param progress 展开进度，0 = 完全收起（不画），1 = 最深。**只在绘制阶段读取**。
 * @param onDismiss 点击遮罩时的关闭请求。浮层应把它与返回键、下滑手势接到同一个出口。
 * @param modifier 调用方修饰符，作用在铺满的容器上。
 */
@Composable
internal fun SheetScrim(
    progress: () -> Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimColor = MaterialTheme.colorScheme.scrim
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val value = progress().coerceIn(0f, 1f)
                // 0 时不必画：浮层刚挂载的头一帧就是 0，省掉一次全屏矩形填充。
                if (value > 0f) {
                    drawRect(scrimColor.copy(alpha = MikomiScrimSpec.Alpha * value))
                }
            }
            .pointerInput(onDismiss) {
                detectTapGestures { onDismiss() }
            },
    )
}
