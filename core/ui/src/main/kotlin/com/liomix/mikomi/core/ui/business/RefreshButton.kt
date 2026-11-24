package com.liomix.mikomi.core.ui.business

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.airbnb.lottie.compose.LottieConstants
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.ui.base.mikomiClickable

/** 图标尺寸：顶栏图标的标准档。 */
private val GlyphSize = Sizes.IconMd

/**
 * 顶栏刷新按钮：刷新中图标转起来，同时禁用点击。
 *
 * ## 为什么要有这个组件
 *
 * 「顶栏右上角一个刷新」在四个页面出现过，各写一遍的下场是四份略有差异的实现：
 * 有的禁用了连点、有的没有；有的是 20dp 环、有的 18dp；有的走了 M3 `IconButton`
 * 于是带水波，而全站已经决定没有任何按压层（`mikomiClickable` 固定 `indication = null`）。
 * 统一到这里之后，换图标、换尺寸、改禁用行为都只改一个地方。
 *
 * ## 三个细节
 *
 * - **位置由外层 48dp 盒决定**：图标 24/环 20 都居中在这块盒子里，所以切换时按钮尺寸、
 *   相邻动作、标题的位置都不会动 —— 尺寸差只体现在图形本身。
 * - **刷新中是同一枚图标在转，不是换成进度环**：`refresh.json` 本来就是一支循环箭头，
 *   转起来就是「正在刷新」，换图形反而多一次视觉跳变。
 * - **刷新完回到首帧**：动画停在半路会让箭头歪着，所以用 `key(isRefreshing)` 重建 ——
 *   重建的只是这一个 24dp 的播放器，动画资源有缓存，代价可以忽略。
 * - **刷新中禁用**：连点会叠加多次网络请求，而转动的图标本身已经说明「正在处理」。
 *
 * @param contentDescription 无障碍名。各页文案不同（「刷新排行榜」/「刷新时间表」），
 *   由调用方给，不在这里写死通用的「刷新」—— 读屏用户听到的是具体在刷新什么。
 */
@Composable
fun RefreshButton(
    isRefreshing: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            // 48dp 是无障碍最小触控目标；视觉仍是 24dp / 20dp。
            // 不用 M3 的 `IconButton`：它会套一层 minimumInteractiveComponentSize，
            // 而且自带水波。
            .size(Sizes.TouchTarget)
            .clip(CircleShape)
            .mikomiClickable(enabled = !isRefreshing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 同一个动画两种状态：播放中 = 刷新中，暂停 = 静止的箭头。
        // `key` 让暂停那一刻重建，进度归零 —— 否则箭头会歪在某一帧上。
        key(isRefreshing) {
            LottieIcon(
                rawRes = LottieIcons.Refresh,
                size = GlyphSize,
                playing = isRefreshing,
                iterations = LottieConstants.IterateForever,
                contentDescription = contentDescription,
            )
        }
    }
}
