package com.liomix.mikomi.core.ui.business

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Stroke
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.mikomiClickable

/** 图标尺寸：顶栏图标的标准档。 */
private val GlyphSize = Sizes.IconMd

/**
 * 进度环直径：比图标小一号。
 *
 * 环是「正在转的东西」，24dp 满格时它的外缘和图标同宽，看起来会是一颗比图标更重的圆盘；
 * 20dp 才和图标笔画的分量相当。小一圈不会让按钮缩小 —— 两者都居中在同一个 48dp 触控盒里。
 */
private val IndicatorSize = 20.dp

/** 进度环线宽：与「必须一眼分辨的指示」同档。 */
private val IndicatorStroke = Stroke.Thick

/**
 * 顶栏刷新按钮：刷新中换成进度环，同时禁用点击。
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
 * - **硬切，不做交叉淡入**：这是一次状态替换（点了之后立刻在刷），过渡动画反而让人以为
 *   「按钮在动」；缓存命中的刷新只有几十毫秒，淡入淡出还没播完就切回去了。
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
            // 48dp 是无障碍最小触控目标；视觉仍是 24dp / 20dp。
            // 不用 M3 的 `IconButton`：它会套一层 minimumInteractiveComponentSize，
            // 而且自带水波。
            .size(Sizes.TouchTarget)
            .clip(CircleShape)
            .mikomiClickable(enabled = !isRefreshing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(IndicatorSize),
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
