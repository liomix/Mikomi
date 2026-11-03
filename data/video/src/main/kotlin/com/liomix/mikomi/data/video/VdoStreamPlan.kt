package com.liomix.mikomi.data.video

/**
 * 出流的最终形态。播放层只认这个类型，不再去问源「要不要网页」。
 *
 * 分成两种不是偏好，是现实：能拿到直链就自己播（进度、倍速、填充全归我们管），
 * 拿不到就只能把页面交给 WebView，让站点播放器自己播。区别在于**谁掌握控制权**，
 * 所以必须在类型上就分开，而不是再塞一个布尔。
 */
sealed interface VdoStreamPlan {

    /** 拿到了可直接播放的地址。 */
    data class Native(val stream: VdoStream) : VdoStreamPlan

    /**
     * 只能渲染播放页。
     *
     * @property pageUrl 要加载的播放页（这一集的地址，不是源首页）。
     * @property userAgent 必须用桌面 UA：这类站点见 Android UA 会 302 到「下载 App」页。
     * @property reason 为什么落到这一步，用于日志与诊断。
     */
    data class Web(
        val pageUrl: String,
        val userAgent: String,
        val reason: String,
    ) : VdoStreamPlan
}

/**
 * 一次出流计划的结果。
 *
 * @property trace 试过哪些策略、各自结论、耗时。失败时它会进 `PlayerFailure.detail`，
 *   这是「画面出不来」唯一能靠得住的信息——没有它只能猜。
 */
data class VdoPlanResult(
    val plan: VdoStreamPlan,
    val trace: String,
)
