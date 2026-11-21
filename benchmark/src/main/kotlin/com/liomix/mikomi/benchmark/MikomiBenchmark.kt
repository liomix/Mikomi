package com.liomix.mikomi.benchmark

/**
 * 性能测试共用的常量与手势。
 *
 * 手势不按坐标写死在每个测试里：屏幕尺寸因设备而异，写死等于让测试只在写它的那台机器上成立。
 * 这里统一按「屏幕宽度的中点 + 高度百分比」换算，换设备不用改代码。
 */
internal object MikomiBenchmark {

    /** 被测应用。macrobenchmark 的所有 API 都以包名寻址。 */
    const val PACKAGE = "com.liomix.mikomi"

    /**
     * 首页首屏之外再滑动的次数。
     *
     * 3 次大约覆盖两屏半的列表：足够把卡片、封面图、布局计算的路径写进 profile，
     * 又不会让生成过程拖到几十秒（Baseline Profile 的采集时间与滑动次数近似线性）。
     */
    const val LIST_SWIPES = 3
}
