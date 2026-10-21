package com.liomix.mikomi.feature.animap

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * WebView 页面回退处理：可回退时消费返回事件回到上一网页，否则交由导航栈处理。
 *
 * 预测性返回（NavigationBackHandler）待 navigationevent 在项目内统一接入后迁移，
 * 参见规范 3.5 的导航返回约定。
 */
@Composable
internal fun AniMapBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
