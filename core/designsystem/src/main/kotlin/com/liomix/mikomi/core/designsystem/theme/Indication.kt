package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 按压反馈。
 *
 * ## 为什么不用 M3 默认的 ripple
 *
 * Compose 的默认 ripple 取 `LocalContentColor`（在 Surface 里通常是 `primary` 或 `onSurface`），
 * 透明度 12%。问题出在「取主色」这条：选中态的背景往往已经是主色容器，主色水波压上去是
 * **第三层同色**，连点几下之后那块区域的色相会明显偏移；夜间主色被 `forNight()` 提亮过，
 * 这个偏移更明显。
 *
 * Telegram 的做法是：按压层永远是**中性墨色**，只按位取 alpha
 * （`(color & 0x00ffffff) | 0x19000000`，见 `Theme.getRoundRectSelectorDrawable`），
 * 所以压在蓝底、黑底、白底上都只是「这一块暗了一点」，不会改变色相。
 * 本项目照此把按压层固定成 `onSurface` + 9.8%。
 *
 * ## 生效方式
 *
 * 由 [MikomiTheme] 解析一次、注入 `LocalIndication`，全仓的 `clickable` /
 * `Surface(onClick)` 自动生效 —— 调用点不用关心，也就不会出现「这个按钮忘了配按压反馈」。
 * 例外是那些显式传了 `indication = null` 的地方（封面飞行、播放器手势区），
 * 它们本来就不该有水波。
 */

/**
 * 按压层颜色（已含 alpha）。
 *
 * 常规情况下不需要读它 —— `LocalIndication` 已经带好了。只有在一个「墨色看不见」的
 * 沉浸子树里（例如播放器浮层的浅色按钮）才需要覆写：
 *
 * ```kotlin
 * CompositionLocalProvider(
 *     LocalMikomiRippleColor provides Color.White.copy(alpha = MikomiMotion.Pressed.OverlayAlpha),
 * ) { PlayerHud() }
 * ```
 */
val LocalMikomiRippleColor = staticCompositionLocalOf {
    // 兜底：没有主题时按日间黑墨算。
    Color.Black.copy(alpha = MikomiMotion.Pressed.OverlayAlpha)
}

/**
 * 构建一套 ripple。
 *
 * 只在 [MikomiTheme] 里调用。业务代码要按压反馈就写 `Modifier.clickable`，
 * 它读的正是 `LocalIndication`。
 *
 * @param color 按压层颜色（已含 alpha）。
 */
@Composable
internal fun rememberMikomiIndication(color: Color): IndicationNodeFactory =
    remember(color) { ripple(color = color) }
