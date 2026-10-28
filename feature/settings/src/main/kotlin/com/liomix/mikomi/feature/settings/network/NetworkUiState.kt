package com.liomix.mikomi.feature.settings.network

import com.liomix.mikomi.core.model.BangumiApiMode

/**
 * 镜像地址的连通性测试结果。
 *
 * 判定标准是「真的能用」，而不是「域名能解析」：探测的正是首页依赖的接口，
 * 必须拿到 2xx 才算通过。只判断「有响应」会把 `404 resource not found` 误报成可用——
 * 公开镜像往往只代理了部分路径，这种假阳性比连不上更坑。
 */
sealed interface MirrorTestResult {

    /** 尚未测试，或地址/模式改动后失效。 */
    data object Idle : MirrorTestResult

    /** 测试进行中。 */
    data object Testing : MirrorTestResult

    /** 收到 2xx 响应：镜像可用。 */
    data class Reachable(val latencyMillis: Long) : MirrorTestResult

    /** 连上了，但返回非 2xx：通常说明这个镜像不代理该接口。 */
    data class HttpError(val statusCode: Int) : MirrorTestResult

    /** 连不上（超时、DNS 失败、证书错误等）。 */
    data class Failed(val reason: String) : MirrorTestResult
}

/** 网络设置页的界面状态。 */
data class NetworkUiState(
    val mode: BangumiApiMode = BangumiApiMode.Direct,
    val mirrorUrl: String = "",
    val mirrorTest: MirrorTestResult = MirrorTestResult.Idle,
) {
    /** 是否处于镜像模式（决定地址输入与测试按钮是否可用）。 */
    val isMirrorMode: Boolean get() = mode == BangumiApiMode.Mirror

    /** 地址形如 `http(s)://host[/path]`，能解析出 host 才算可用。 */
    val hasUsableMirror: Boolean
        get() = mirrorUrl.trim().let { raw ->
            raw.isNotEmpty() && runCatching { java.net.URI(raw).host }.getOrNull() != null
        }

    /** 镜像模式下地址为空/非法时提示错误；直连模式不提示。 */
    val showMirrorError: Boolean
        get() = isMirrorMode && mirrorUrl.isNotEmpty() && !hasUsableMirror
}
