package com.liomix.mikomi.core.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 选中项容器的不透明度（primary 淡化，承托同色图标与文字）。 */
private const val SELECTED_CONTAINER_ALPHA = 0.14f

/** 悬浮导航栏距屏幕左右与底部的外边距（由宿主应用在布局时施加）。 */
val MikomiNavBarMargin = 12.dp

/**
 * 悬浮导航栏栏体高度。
 *
 * = 图标 24 + 图文间距 2 + 文字行高（labelMedium 16sp）16 + 单项上下内边距 12 + 容器上下内边距 8。
 * 页面要给底栏让位时请用 [MikomiNavBarBottomSpace]，不要自己再算一遍。
 */
val MikomiNavBarBodyHeight = 62.dp

/** 悬浮导航栏从屏幕下沿往上占用的高度（栏体 + 底部外边距，**不含**系统导航栏 inset）。 */
val MikomiNavBarBottomSpace = MikomiNavBarMargin + MikomiNavBarBodyHeight

/**
 * 导航栏条目模型。
 *
 * [key] 为任意稳定标识（通常是路由对象），由调用方解释，组件本身不感知导航框架。
 */
data class MikomiNavItem(
    val key: Any,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val label: String,
    val badgeCount: Int? = null,
)

/**
 * 悬浮胶囊式底部导航栏。
 *
 * 复用 [TabBar] 的滑动指示器与动效：选中项是一颗在条目之间滑动的胶囊，
 * 触摸范围、指示器矩形、实测矩形三者一致；图标在选中态切换为实心变体。
 *
 * 自适应：调用方可通过 [modifier] 传入 `widthIn(max = ...)`，大屏下保持居中且不过度拉伸。
 */
@Composable
fun MikomiNavigationBar(
    items: List<MikomiNavItem>,
    selectedKey: Any?,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)

    TabBar(
        selectedIndex = selectedIndex,
        onSelect = { index -> items.getOrNull(index)?.let { onSelect(it.key) } },
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_CONTAINER_ALPHA),
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        hoveredContentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        // 不投影。栏体会浮在巡礼页的浅色地图上，8dp 投影在纯色背景里扩散成一圈
        // 肉眼可见的灰边，两端圆角外侧尤其像「多出来一块矩形的触控范围」；
        // 而在深色页面上这层灰影根本看不见。它只在个别页面起反作用，所以不要。
        // 浮起感由栏体着色与页面留白给出，不靠阴影。
        elevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        itemSpacing = 4.dp,
        itemStyle = TabItemStyle.navigation(),
    ) {
        items.forEachIndexed { index, item ->
            Tab(
                index = index,
                text = item.label,
                icon = item.icon,
                selectedIcon = item.selectedIcon,
                badgeCount = item.badgeCount,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
