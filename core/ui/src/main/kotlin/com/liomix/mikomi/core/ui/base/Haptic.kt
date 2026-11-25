package com.liomix.mikomi.core.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 震动的语义档位。
 *
 * 分档的依据是**动作的分量**，不是「震多大力」—— 力度由系统决定，我们能选的只有
 * 「用哪一种系统反馈」。所以这里标的是场景：
 *
 * - [Tick] 轻触：滑动吸附、分段控件落位这类「跟手的细小确认」；
 * - [Toggle] 切换：开关翻转、选中项变化，比 Tick 重一点；
 * - [LongPress] 长按：唤出菜单、进入编辑态 —— 用户按住了一秒，需要知道「按住了」；
 * - [Reject] 失败：越界、操作不可用。**只有真的失败才用**，否则它会退化成噪音。
 *
 * 全站只从 [rememberHaptic] 拿反馈，不要在页面里直接写 `HapticFeedbackType`：
 * 那样每次加一个交互都要重新决定用哪一档，同一件事在不同页面也会震得不一样。
 */
enum class HapticTone {
    Tick,
    Toggle,
    LongPress,
    Reject,
}

/** 档位到平台反馈的映射。集中在一处，平台换了只改这张表。 */
private val HapticTone.platform: HapticFeedbackType
    get() = when (this) {
        HapticTone.Tick -> HapticFeedbackType.SegmentTick
        HapticTone.Toggle -> HapticFeedbackType.ToggleOn
        HapticTone.LongPress -> HapticFeedbackType.LongPress
        HapticTone.Reject -> HapticFeedbackType.Reject
    }

/**
 * 取一个「按档位震动」的回调。
 *
 * 用法：
 * ```
 * val haptic = rememberHaptic()
 * ...
 * onLongClick = { haptic(HapticTone.LongPress); ... }
 * ```
 *
 * 返回的是函数而不是直接调用：调用点通常在 lambda 里（点击回调不是 @Composable），
 * 把 `LocalHapticFeedback` 的读取留在组合期，lambda 里只做一次调用。
 */
@Composable
fun rememberHaptic(): (HapticTone) -> Unit {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) {
        { tone -> feedback.performHapticFeedback(tone.platform) }
    }
}
