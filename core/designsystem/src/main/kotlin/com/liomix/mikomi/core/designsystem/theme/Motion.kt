package com.liomix.mikomi.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

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
/**
 * 一个具名时长档位（毫秒）。
 *
 * 与 [MikomiStep] 同构，只是单位不是 dp —— 动效的档位是「多久」，不是「多大」，
 * 硬塞进同一个类就得给数值加单位标记，反而更难读。
 */
@Immutable
data class MikomiTime(val name: String, val millis: Int) {
    /** 目录里显示的数值，如 `200ms`。 */
    val label: String get() = "${millis}ms"
}

object MikomiMotion {

    /**
     * 时长阶梯。
     *
     * ## 数值取自 Telegram 的真实动画时长
     *
     * 扫一遍 TG 的 `ValueAnimator.setDuration` / `DURATION =` 常量，出现频率最高的六个值是
     * **150 / 200 / 250 / 300 / 350**，加上按压的 60。这就是它的节奏基线：
     *
     * - 150（出现最多）—— 淡入、指示器换位这类「一眨眼」的变化；
     * - 200 —— 颜色与状态切换，TG 的默认手感；
     * - 250 —— 面板展开、提示条进出；
     * - 300 —— 明确的位移与尺寸变化；
     * - 350 —— 大块转场，同时也是 `ButtonBounce` 松手回弹的时长。
     *
     * 之前这套是 120 / 180 / 240 / 300 / 420，每一档都比 TG 偏一档：短动作偏急、长动作偏缓，
     * 整机读起来像「加速播放」。对齐之后，「同一次点击的各个反馈」才会落在同一个拍子上 ——
     * 按压 60 缩下去、200 换色、350 弹回来，正好是一条自然的呼吸。
     *
     * **不要为了 10ms 的差别新增一档**：两档之间差 20% 以上用户才分得出来。
     */
    object Duration {
        /** 60ms：只有三四帧。按压反馈、按下瞬间的形变（即 `ButtonBounce` 的按下时长）。 */
        const val Instant = 60

        /** 150ms：元素进出组合的淡入淡出、指示器换位。TG 的最高频档。 */
        const val Fast = 150

        /** 200ms：颜色状态切换（选中/未选中的底色与文字色）、退场。 */
        const val Short = 200

        /** 250ms：面板、提示条的入场。 */
        const val Medium = 250

        /** 300ms：位移与尺寸变化（开关滑块、展开收起）。 */
        const val Long = 300

        /** 350ms：页面级转场（封面飞行、整屏切换），也是松手回弹的时长。 */
        const val ExtraLong = 350

        /**
         * 全部时长档位。
         *
         * 与尺寸的 `steps` 同一个用途：`TokenGallery` 从它渲染，于是「加了档位忘记登记」
         * 不再可能发生。类型不是 `Dp` 而是毫秒，所以单独定义（[MikomiTime]）。
         */
        val steps: List<MikomiTime> = listOf(
            MikomiTime("Instant", Instant),
            MikomiTime("Fast", Fast),
            MikomiTime("Short", Short),
            MikomiTime("Medium", Medium),
            MikomiTime("Long", Long),
            MikomiTime("ExtraLong", ExtraLong),
        )
    }

    /**
     * 缓动曲线。
     *
     * ## 控制点全部取自 Telegram 的 `CubicBezierInterpolator`
     *
     * 它内置六条：`DEFAULT` / `EASE_OUT` / `EASE_OUT_QUINT` / `EASE_IN` / `EASE_BOTH` /
     * `EASE_OUT_BACK`。这里挑走前四条（后两条分别对应「对称进出」与「带回弹」，
     * 前者用处少的可怜、后者与 [Overshoot] 重复），控制点一字不改 ——
     * 曲线形状差 0.05 在 200ms 的动画里就是「有点黏」和「很爽利」的区别，推不出来只能抄。
     *
     * 原来这套用的是 Material 3 的 `emphasized` 族（`(0.2, 0, 0, 1)` 等）：
     * 起手太慢、收尾太长，做位移时会有一种「先犹豫一下再滑过去」的迟滞感。
     * TG 的 DEFAULT 起手就快，是列表类界面更需要的节奏。
     */
    object Curve {
        /** 标准：TG 的 `CubicBezierInterpolator.DEFAULT`，等价 CSS 的 `ease`。 */
        val Standard: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

        /** 减速 / 入场：TG 的 `EASE_OUT`。起步快、末尾收住，是「东西飞进来」的手感。 */
        val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

        /** 加速 / 退场：TG 的 `EASE_IN`。起步慢、越走越快，是「东西被带走」的手感。 */
        val Accelerate: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)

        /**
         * 强调：TG 的 `EASE_OUT_QUINT`。
         *
         * 比 [Decelerate] 更陡 —— 前 30% 就走了 70% 的路程，然后长时间贴着终点收。
         * 用在「必须让人注意到位置变了」的场合（指示器跳转、列表项插入）。
         */
        val Emphasized: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

        /** 线性的唯一用处：扫光、滚动文字这类「匀速位移」。 */
        val Linear: Easing = CubicBezierEasing(0f, 0f, 1f, 1f)

        /**
         * 压下去的缓动：与 [Standard] 是同一条曲线。
         *
         * 单独留一个名字是因为**语义不同**：`Standard` 是「元素在场内换位置」，
         * `Press` 是「手指压下去了」。哪天按压需要换一条更硬或更软的曲线，
         * 改这里就行，不用去翻出所有用到 Standard 的地方判断哪个该改。
         */
        val Press: Easing = Standard

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
     * 按压反馈的透明度。
     *
     * Telegram 的 ripple 不是「主色 12%」，而是**中性墨色 9.8%**：它把颜色按位与成
     * `(color & 0x00ffffff) | 0x19000000`（见 `Theme.getRoundRectSelectorDrawable`），
     * `0x19` = 25，即 25/255 ≈ 9.8%。
     *
     * 这个差别很实际：用主色做的水波会在选中态（底色已经是主色容器）上叠出第三层颜色，
     * 一屏里点几下就像换了个主题；中性墨永远只是「把这块压暗一点点」，压在什么底色上都对。
     * 本项目的 ripple 见 `Indication.kt`。
     */
    object Pressed {
        /** 按压覆盖层的透明度：TG 的 `0x19`。 */
        const val OverlayAlpha = 25f / 255f
    }

    // 禁用态的淡化系数不在这里 —— 搬去了 `MikomiAlpha`（Colors.kt）。
    // 它们一次都不动，是颜色规则，不是动效。
}
