package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/*
 * 动效 token。
 *
 * ## 为什么动效也值得做成 token
 *
 * 同一个 App 里「淡入」有 120、150、180 三个版本时，用户说不出哪里不对，但会觉得
 * 「有的地方轻快、有的地方黏」—— 因为大脑靠节奏判断是否是同一个系统。
 * 这里把时长收成五档、缓动收成五条曲线，组件按语义取档，而不是各写一个毫秒数。
 *
 * ## 曲线为什么是这三条
 *
 * Compose 的 `CubicBezierEasing` 与 Material 的 `Easing` 是同一套数学，这里不引入
 * Material 的 `FastOutSlowInEasing` 常量，而是把控制点直接写出来 —— 调参时能一眼看到
 * 曲线形状，也方便和 Telegram 的 `CubicBezierInterpolator` 对照（它用的是同一族曲线）。
 */
object MikomiMotion {

    /**
     * 时长阶梯。
     *
     * 六档，覆盖「按下 → 颜色 → 进场面板 → 位移 → 转场」五个量级。每一档都有真实使用者，
     * 不是凑出来的：再加一档就会出现「180 和 200 我该选哪个」的困惑。
     * **不要为了 10ms 的差别新增一档**：两档之间差 20% 以上用户才分得出来。
     */
    object Duration {
        /** 60ms：只有三四帧。按压反馈、按下瞬间的形变。 */
        const val Instant = 60

        /** 120ms：元素进出组合的淡入淡出。 */
        const val Fast = 120

        /** 180ms：颜色状态切换（选中/未选中的底色与文字色）、退场。 */
        const val Short = 180

        /** 240ms：面板、提示条的入场。 */
        const val Medium = 240

        /** 300ms：位移与尺寸变化（Tab 指示器换位、开关滑块）。 */
        const val Long = 300

        /** 420ms：页面级转场（封面飞行、整屏切换）。 */
        const val ExtraLong = 420
    }

    /**
     * 缓动曲线。
     *
     * 三档是通用的（[Curve.Standard] / [Curve.Decelerate] / [Curve.Accelerate]），
     * [Press] 与 [Overshoot] 专供按压手感，别拿去做别的。
     */
    object Curve {
        /** 标准：两头都收，用于「元素在场内换位置」。 */
        val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** 减速：入场专用。起步就快，末尾长长的收住，是「东西飞进来」的手感。 */
        val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

        /** 加速：退场专用。起步慢、越走越快，是「东西被带走」的手感。 */
        val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

        /** 线性的唯一用处：扫光、滚动文字这类「匀速位移」。 */
        val Linear: Easing = CubicBezierEasing(0f, 0f, 1f, 1f)

        /** 压下去的缓动：Telegram 的 `CubicBezierInterpolator.DEFAULT`，即 CSS 的 `ease`。 */
        val Press: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

        /**
         * 松手的缓动：把 Android `OvershootInterpolator` 的公式原样搬过来。
         *
         * 不用 Compose 的弹簧有两个原因：弹簧是**多次衰减振荡**，收尾会有一串几乎看不见的
         * 抖动；而 Overshoot 是**单峰**曲线，冲过 1.0 一次就回来，读起来更干净，
         * 也才和 Telegram 一致。
         */
        val Overshoot: Easing = object : Easing {
            override fun transform(fraction: Float): Float {
                val t = fraction - 1f
                val amount = MikomiMotion.Press.OvershootAmount
                return t * t * ((amount + 1f) * t + amount) + 1f
            }
        }
    }

    /**
     * 按压规格：所有可点元素共用的一套手感。
     *
     * 数值全部照抄 Telegram 的 `ButtonBounce`（`ui/Components/ButtonBounce.java`）：
     * 默认构造 → 按下 60ms、松手 350ms、过冲 5.0；各调用点的 `getScale(0.1f)`
     * （Telegram 里出现最多的一档）说明按下缩到 0.9。
     *
     * 「按压感」这种东西推不出来，只能对着一个公认手感好的实现抄 —— 所以这里不自己取整。
     */
    object Press {
        /** 按下时缩到的比例：缩掉 10%。 */
        const val Scale = 0.90f

        /** 压下去的时长。60ms 只有三四帧，手指刚压到底就已经缩完了。 */
        const val InMillis = 60

        /** 松手回弹的时长。350ms 里只有前 200ms 在动，后面是过冲后的收尾。 */
        const val ReleaseMillis = 350

        /**
         * 回弹过冲量，对应 Android 的 `OvershootInterpolator(5f)`。
         *
         * 5 听起来夸张，但它作用在「0.9 → 1.0」这段 10% 的差上，实际最高只到 1.051 ——
         * 正好是「看得到弹一下、又不会觉得按钮长大了」。这就是 Telegram 按钮那股韧劲的来源。
         */
        const val OvershootAmount = 5f
    }

    /**
     * 禁用态的透明度（M3 规范值）。
     *
     * 按在「置灰」上的两档：底色降得多、文字降得更多 —— 文字先变得不可读，
     * 用户才不会去点。
     */
    object Disabled {
        /** 禁用底色透明度。 */
        const val ContainerAlpha = 0.12f

        /** 禁用文字透明度。 */
        const val ContentAlpha = 0.38f
    }
}
