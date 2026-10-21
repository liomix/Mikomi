package com.liomix.mikomi.feature.animap

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * WebView 页面回退处理：沉浸中返回手势唤出底部导航栏，可回退时回到上一网页，
 * 否则交由导航栈处理。
 *
 * 预测性返回（NavigationBackHandler）待 navigationevent 在项目内统一接入后迁移，
 * 参见规范 3.5 的导航返回约定。
 *
 * @param enabled 是否启用返回事件拦截（可回退或沉浸中需唤出导航栏）
 * @param onBack 返回事件回调（唤出导航栏/WebView 回退由调用方决定）
 */
@Composable
internal fun AniMapBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
