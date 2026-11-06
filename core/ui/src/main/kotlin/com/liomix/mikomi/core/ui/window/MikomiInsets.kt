package com.liomix.mikomi.core.ui.window

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

/**
 * 系统安全区的唯一出口。
 *
 * ## 为什么不允许页面直接写 `WindowInsets.statusBars`
 *
 * 因为 `statusBars` 与 `navigationBars` **不含挖孔**。横屏刘海机的挖孔在左右两侧，
 * 那两条 inset 在那里都是 0 —— 于是 `statusBarsPadding()` 加了个零，
 * 顶栏的返回键照旧落在刘海底下，而且只在刘海机上才复现。
 *
 * 语义区选的不是「状态栏多高」，而是「**这块内容绝对不能被人挡住**」：
 * 对那个问题，正确的答案是 `safeDrawing` 一族（含挖孔），不是某一条系统栏。
 * 全仓 26 处调用点各写一遍区号，等于 26 次重新回答同一个问题，
 * 所以这里只留一组**按用途命名**的入口，页面不再碰 `WindowInsets` 本身。
 *
 * ## 五个入口，各自对应一种「谁在遮盖」
 *
 * | 入口 | 含 | 不含 | 用在哪 |
 * | --- | --- | --- | --- |
 * | [Top] | 状态栏、挖孔 | 键盘 | 顶栏下方开始的内容 |
 * | [Bottom] | 导航栏、挖孔 | 键盘 | 与 `imePadding` 搭配的容器 |
 * | [BottomOrIme] | 导航栏、挖孔、键盘 | — | 自己独占底部的容器（滚到底/被键盘顶起都成立） |
 * | [Chrome] | 状态栏、挖孔、左右 | 键盘 | 顶栏（返回键贴左边，横屏会被刘海压住） |
 * | [Full] | 状态栏、导航栏、挖孔 | 键盘 | 四边都要让开的浮层 |
 *
 * ## 键盘只允许有一个主人
 *
 * `safeDrawing` 是**含键盘**的（`systemBars ∪ ime ∪ displayCutout`）。这一点很多地方记错，
 * 于是出现两种反向的坑：
 *
 * - 页面根节点加 `safeDrawingPadding()`：键盘一弹，整页（含顶栏）被推上去，顶栏跑出屏幕；
 * - 容器已经按键盘让过位，里面又写一次 `imePadding()`：让了两份，底部空出一条黑带。
 *
 * 所以键盘相关的只有 [BottomOrIme] 一个入口，且一条修饰链上只能出现一次。
 * `windowAudit` 会盯着这件事（见 `docs/mikomi-rules.md` §6.6）。
 *
 * ## 为什么不用 `WindowInsetsRulers`（Compose 1.9+ 的新 API）
 *
 * 那套 API 的值只能在 layout/placement 阶段读，正是本项目喜欢的形态（逐帧变化的值不进组合）。
 * 但 Compose 1.12 只提供了 rulers 本体，配套的 `Modifier.fitInside` 还没有出现在任何公开产物里 ——
 * 自己拿 placement scope 手搓一层，等于在一个实验 API 上再叠一层自研，
 * 而它要解决的问题（键盘动画期间少跳一点）还没到必须付这个代价的程度。
 */
object MikomiInsets {

    /** 顶部：状态栏 ∪ 挖孔，不含键盘。 */
    val Top: WindowInsets
        @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)

    /** 底部：导航栏 ∪ 挖孔，**不含**键盘 —— 与 `imePadding` 搭配时用这个。 */
    val Bottom: WindowInsets
        @Composable get() = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Bottom)

    /**
     * 底部的唯一所有者：导航栏 ∪ 挖孔 ∪ 键盘。
     *
     * 键盘弹起时取键盘高度（此时导航栏被键盘盖住，让两份就是多出一条空带），
     * 收起时回落到导航栏 —— 恰好是「滚到底停在手势条之上」想要的那条线。
     * 一条链上用了它，就不要再写 [Bottom] 或 `imePadding`。
     */
    val BottomOrIme: WindowInsets
        @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)

    /** 顶栏：状态栏 ∪ 挖孔 ∪ 左右。返回键贴左边，横屏刘海机需要左右那一份。 */
    val Chrome: WindowInsets
        @Composable get() = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    /** 只要左右（挖孔，横屏时在两侧）。给「已经自己处理了上下」的画面用。 */
    val Horizontal: WindowInsets
        @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

    /**
     * 底部 ∪ 左右，不含顶部。
     *
     * 播放器的底部控制条用它：横屏全屏时两侧要让开挖孔，但**不能**把状态栏那一份也加进来，
     * 否则控制条会被顶到画面中间。
     */
    val BottomSides: WindowInsets
        @Composable get() = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

    /** 四边：状态栏 ∪ 导航栏 ∪ 挖孔，不含键盘。给「浮在整屏之上」的浮层用。 */
    val Full: WindowInsets
        @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
}

/**
 * 安全区的 dp 值。
 *
 * 给 `contentPadding` / 手工 `padding` 用 —— 那两处收的是 [Dp]，不是 [WindowInsets]。
 * 一旦需要写死成 dp，`windowInsetsPadding` 的「自动消费」就没了，所以同一个页面上
 * 只应该有一条路：要么整体走 [WindowInsets]，要么整体读这里的值。
 */
@Immutable
data class MikomiInsetsDp(
    val start: Dp,
    val top: Dp,
    val end: Dp,
    val bottom: Dp,
)

/** 读一次当前安全区的 dp 值。窗口尺寸/形态变化时自动重建。 */
@Composable
fun rememberMikomiInsetsDp(): MikomiInsetsDp {
    val direction = LocalLayoutDirection.current
    val padding = MikomiInsets.Full.asPaddingValues()
    return MikomiInsetsDp(
        start = padding.calculateStartPadding(direction),
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding(),
    )
}

/** 只要底部安全区（导航栏 ∪ 挖孔，不含键盘）的 dp 值。 */
@Composable
fun rememberMikomiBottomInset(): Dp = MikomiInsets.Bottom.asPaddingValues().calculateBottomPadding()

/** 只要顶部安全区（状态栏 ∪ 挖孔）的 dp 值。 */
@Composable
fun rememberMikomiTopInset(): Dp = MikomiInsets.Top.asPaddingValues().calculateTopPadding()
