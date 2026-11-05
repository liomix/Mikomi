package com.liomix.mikomi.core.ui.component.layout

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * 底部悬浮栏占用的高度（栏体 + 外边距，**不含**系统导航栏 inset）。
 *
 * 页面需要给底栏让位时读它，而不是自己记一个 dp 数：底栏长什么样、有多高是**应用壳**的决定，
 * 页面只需要知道「屏幕底部有一块 n dp 是别人的」。这样换掉底栏形态（悬浮胶囊 / 贴底分栏 /
 * 平板侧栏）时，页面一行都不用改。
 *
 * 默认 [0.dp] = 没有底栏，页面照常铺满 —— 所以二级页面、播放器这些不需要底栏的地方
 * 不必写任何条件判断，直接用就行。
 *
 * 用 `static` 而不是普通的 `compositionLocalOf`：它由壳在根节点给一次，整场运行都不会变；
 * 读它不该带来逐次读取的开销。
 */
val LocalBottomBarSpace = staticCompositionLocalOf { 0.dp }
