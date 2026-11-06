package com.liomix.mikomi.core.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.liomix.mikomi.core.designsystem.spec.MikomiWindowSpec

/** 窗口宽度档。数值与 Material 3 的窗口尺寸类一致（见 [MikomiWindowSpec]）。 */
enum class MikomiWidthClass { Compact, Medium, Expanded }

/** 窗口高度档。高度档只用来区分「矮窗口」（横屏手机、分屏上半屏）。 */
enum class MikomiHeightClass { Compact, Medium, Expanded }

/**
 * 当前窗口的尺寸档。
 *
 * **只暴露档位，不暴露 dp。** 这是刻意的：一旦把宽高 dp 直接下发，页面就会开始写
 * 「宽度 > 480 时……」「高度 / 宽度 > 0.6 时……」这类一次性判断 —— 那些判断既不可枚举，
 * 也没法验收。档位是可数个的，能用完、能审。
 *
 * 需要精确宽度的地方（网格列数）本来就不该问窗口要：`GridCells.Adaptive` 拿的是
 * 布局阶段到手的真实约束，比窗口宽度更准（它已经扣掉了 padding）。
 */
@Immutable
data class MikomiWindowSize(
    val width: MikomiWidthClass = MikomiWidthClass.Compact,
    val height: MikomiHeightClass = MikomiHeightClass.Compact,
) {
    /** 折叠屏合起、手机竖屏、分屏的一半 —— 单列是唯一合理的排布。 */
    val isCompactWidth: Boolean get() = width == MikomiWidthClass.Compact

    /** 平板横屏、折叠屏展开、桌面窗口拉宽 —— 可以开始收起内容列了。 */
    val isExpandedWidth: Boolean get() = width == MikomiWidthClass.Expanded

    /** 横屏手机、分屏上半屏：可用高度不足以放下「头图 + 内容」。 */
    val isCompactHeight: Boolean get() = height == MikomiHeightClass.Compact
}

/**
 * 整个组合树共享的窗口尺寸档。
 *
 * 由应用壳在根部提供一次（`MikomiNavHost`），页面只读、不自己算。
 * 默认值是 compact/compact：这就是「没有窗口信息时按手机竖屏排」——
 * `@Preview` 与单测因此不需要额外搭一层壳。
 */
val LocalMikomiWindowSize = staticCompositionLocalOf { MikomiWindowSize() }

/**
 * 读当前窗口尺寸档。
 *
 * ## 数据源
 *
 * `LocalWindowInfo.containerDpSize` —— 它由 `AndroidComposeView` 在每次窗口尺寸变化时写入，
 * 和 `material3-adaptive` 的 `currentWindowAdaptiveInfo()` 读的是同一份 `WindowMetrics`，
 * 只是不需要那个产物（本项目要能离线构建）。
 *
 * 拿不到时（`DpSize.Unspecified`）回落到 `LocalConfiguration.screenWidthDp`。
 * 回落值是「屏幕」而不是「窗口」，在多窗口/分屏下会偏大 —— 所以它只是兜底，
 * 不允许页面自己去读 `LocalConfiguration` 做断点判断（`windowAudit` 会拦）。
 *
 * ## 回滞
 *
 * 自由窗口拖动时这个值每像素都变。直接判档会让整页在断点上来回翻，
 * 所以档位变化要越过 [MikomiWindowSpec.BreakpointHysteresis] 才生效。
 *
 * 状态写入放在 [SideEffect] 里而不是组合期：组合期写状态会触发「组合 → 失效 → 再组合」，
 * 而这里的目的恰好是**不**在每一次尺寸变化时立刻改变整棵树。
 */
@Composable
fun rememberMikomiWindowSize(): MikomiWindowSize {
    val windowInfo = LocalWindowInfo.current
    val configuration = LocalConfiguration.current

    val container = windowInfo.containerDpSize
    val size = if (container.width.isSpecified && container.width > 0.dp) {
        container
    } else {
        DpSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp)
    }

    val tracker = remember { WindowSizeTracker(size) }
    SideEffect { tracker.update(size) }
    return tracker.size
}

/**
 * 窗口宽度的像素值。
 *
 * **只给「按宽度算量」的绘制参数用**：扫光的行程要按「每 400ms 平移一个列表宽」反推周期，
 * 那个列表宽就是窗口宽。判档请读 [LocalMikomiWindowSize] —— 拿这个值写
 * `if (widthPx > 600.dp.toPx())` 等于绕开档位重新发明一次断点。
 *
 * 分屏/自由窗口下它给的是**窗口**宽度（`containerDpSize`），不是屏幕宽度：
 * 用屏幕宽算出来的扫光速度会在半屏里慢一半。
 */
@Composable
fun rememberMikomiWindowWidthPx(): Float {
    val windowInfo = LocalWindowInfo.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val container = windowInfo.containerDpSize.width
    val width = if (container.isSpecified && container > 0.dp) {
        container
    } else {
        configuration.screenWidthDp.dp
    }
    return with(density) { width.toPx() }
}

/**
 * 档位的记忆体：只记住「现在在哪一档」，新的尺寸要越过回滞带才改。
 *
 * 单独一个类而不是 `remember` 三个 `mutableStateOf`：判定逻辑要读「上一档」，
 * 写在组合函数里就变成「组合期读状态 + 组合期写状态」的一团。
 */
private class WindowSizeTracker(initial: DpSize) {
    var size: MikomiWindowSize by mutableStateOf(
        MikomiWindowSize(
            width = classifyWidth(initial.width, previous = null),
            height = classifyHeight(initial.height, previous = null),
        ),
    )
        private set

    fun update(next: DpSize) {
        val candidate = MikomiWindowSize(
            width = classifyWidth(next.width, previous = size.width),
            height = classifyHeight(next.height, previous = size.height),
        )
        if (candidate != size) size = candidate
    }
}

/**
 * 宽度判档：进入某一档按原断点，离开某一档要多走一个回滞带。
 *
 * 两个方向都只加在「离开」这一侧，所以 600 断点的实际行为是：
 * compact → medium 要 ≥ 624，medium → compact 要 < 576。
 * 手指停在 600 上下抖动时，档位不动；真的把窗口拖走，档位也不会拖泥带水。
 */
private fun classifyWidth(width: Dp, previous: MikomiWidthClass?): MikomiWidthClass {
    val hysteresis = MikomiWindowSpec.BreakpointHysteresis
    return when (previous) {
        null -> when {
            width < MikomiWindowSpec.WidthMediumMin -> MikomiWidthClass.Compact
            width < MikomiWindowSpec.WidthExpandedMin -> MikomiWidthClass.Medium
            else -> MikomiWidthClass.Expanded
        }

        MikomiWidthClass.Compact -> when {
            width >= MikomiWindowSpec.WidthExpandedMin + hysteresis -> MikomiWidthClass.Expanded
            width >= MikomiWindowSpec.WidthMediumMin + hysteresis -> MikomiWidthClass.Medium
            else -> MikomiWidthClass.Compact
        }

        MikomiWidthClass.Medium -> when {
            width < MikomiWindowSpec.WidthMediumMin - hysteresis -> MikomiWidthClass.Compact
            width >= MikomiWindowSpec.WidthExpandedMin + hysteresis -> MikomiWidthClass.Expanded
            else -> MikomiWidthClass.Medium
        }

        MikomiWidthClass.Expanded -> when {
            width < MikomiWindowSpec.WidthMediumMin - hysteresis -> MikomiWidthClass.Compact
            width < MikomiWindowSpec.WidthExpandedMin - hysteresis -> MikomiWidthClass.Medium
            else -> MikomiWidthClass.Expanded
        }
    }
}

/** 高度判档。规则同 [classifyWidth]，只是断点不同。 */
private fun classifyHeight(height: Dp, previous: MikomiHeightClass?): MikomiHeightClass {
    val hysteresis = MikomiWindowSpec.BreakpointHysteresis
    return when (previous) {
        null -> when {
            height < MikomiWindowSpec.HeightMediumMin -> MikomiHeightClass.Compact
            height < MikomiWindowSpec.HeightExpandedMin -> MikomiHeightClass.Medium
            else -> MikomiHeightClass.Expanded
        }

        MikomiHeightClass.Compact -> when {
            height >= MikomiWindowSpec.HeightExpandedMin + hysteresis -> MikomiHeightClass.Expanded
            height >= MikomiWindowSpec.HeightMediumMin + hysteresis -> MikomiHeightClass.Medium
            else -> MikomiHeightClass.Compact
        }

        MikomiHeightClass.Medium -> when {
            height < MikomiWindowSpec.HeightMediumMin - hysteresis -> MikomiHeightClass.Compact
            height >= MikomiWindowSpec.HeightExpandedMin + hysteresis -> MikomiHeightClass.Expanded
            else -> MikomiHeightClass.Medium
        }

        MikomiHeightClass.Expanded -> when {
            height < MikomiWindowSpec.HeightMediumMin - hysteresis -> MikomiHeightClass.Compact
            height < MikomiWindowSpec.HeightExpandedMin - hysteresis -> MikomiHeightClass.Medium
            else -> MikomiHeightClass.Expanded
        }
    }
}
