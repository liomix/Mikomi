package com.liomix.mikomi.feature.animap

import android.app.Activity

/**
 * 巡礼页预热入口（供宿主导航层调用）。
 *
 * 宿主在三种时机调用，全部是「用户可能马上要看地图」的信号：
 * - 首页停留几秒后（这时预热的时间最充裕，收益也最大）；
 * - 顶级横滑手势过半（意图已经明确，但还没松手）；
 * - 点击底部导航的巡礼项（最晚的一次，只能追上转场那几百毫秒）。
 *
 * 调用是幂等的：已有可用页面、正在预热、或距上次失败不足重试间隔时直接返回，
 * 重复调用不会重建 WebView。
 *
 * **必须在主线程调用**：内部要创建 `WebView`。
 *
 * 实现与生命周期策略见 [AniMapCache]。
 */
object AniMapWarmer {

    /**
     * 提前把巡礼页加载进后台。
     *
     * @param activity 用于获取窗口与构造 WebView，不会长期持有
     * @param url 地图页地址，默认 [ANIMAP_URL]
     */
    fun warmUp(activity: Activity, url: String = ANIMAP_URL) {
        AniMapCache.warmUp(activity, url)
    }
}
