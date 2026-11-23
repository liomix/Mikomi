package com.liomix.mikomi.core.ui.business

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Motion
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Stroke
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.mikomiClickable

/**
 * 图标与进度环共用的视觉尺寸：顶栏图标的标准档。
 *
 * 图标与环必须共用同一个值 —— 一个 24、一个 20 的话，切换那一帧整个按钮会先缩再弹。
 */
private val GlyphSize = Sizes.IconMd

/** 进度环线宽：在 24dp 直径上，2dp 与图标笔画重量接近。 */
private val IndicatorStroke = Stroke.Thick

/**
 * 顶栏刷新按钮：刷新中换成同尺寸的进度环，同时禁用点击。
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
 * - **图标和环都是 [GlyphSize]**：两者共用同一个尺寸，切换时包围盒不变，
 *   顶栏里的标题和相邻动作都不会跟着挪半像素。
 * - **切换走 [Crossfade]**：图标和环是两种东西，硬切会「闪」一下；淡入淡出读起来是
 *   「图标让位给进度」，与全站动效同一条短时长（[Motion.Duration.Short]）。
 * - **刷新中禁用**：连点会叠加多次网络请求，而进度环本身已经说明「正在处理」。
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
            // 48dp 是无障碍最小触控目标；视觉仍是 24dp。
            // 不用 M3 的 `IconButton`：它会套一层 minimumInteractiveComponentSize，
            // 而且自带水波。
            .size(Sizes.TouchTarget)
            .clip(CircleShape)
            .mikomiClickable(enabled = !isRefreshing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = isRefreshing,
            animationSpec = tween(durationMillis = Motion.Duration.Short),
            label = "refreshGlyph",
        ) { refreshing ->
            Box(
                modifier = Modifier.size(GlyphSize),
                contentAlignment = Alignment.Center,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(GlyphSize),
                        strokeWidth = IndicatorStroke,
                    )
                } else {
                    Icon(
                        icon = Icons.Refresh,
                        contentDescription = contentDescription,
                        size = GlyphSize,
                    )
                }
            }
        }
    }
}
