package com.liomix.mikomi.data.video.remote

/**
 * 一次失败的**原因**。只给原因，文案由 feature 层映射——数据层不认识多语言。
 *
 * 三大源共用这一套：用户看到的都是「搜不到 / 没剧集 / 出不了流」，
 * 换一个源不该换一套错误语义。各源只在 [detail] 里写自己的现场细节。
 */
enum class VdoFailureReason {
    /** 规则本身不能用：JSON 坏了、缺字段、XPath 写错。API 源没有规则，用不到这一项。 */
    RuleBroken,

    /** 连不上：DNS、超时、被墙、TLS 失败。 */
    NetworkError,

    /** 连上了但状态码不对（403/404/5xx）。 */
    HttpError,

    /** 搜索页打得通，但一条都没匹配到。 */
    NoResults,

    /** 进了详情页，但找不到剧集。 */
    NoEpisodes,

    /** 进了播放页，但页面里翻不出任何播放地址。 */
    NoStream,

    /** 找到了播放地址，但拉不下来（403、被防盗链拦、返回的是网页）。 */
    StreamUnreachable,

    /** 地址靠 JS 现拼，静态拿不到——这类只能交给 WebView。 */
    NeedWebView,
}

/**
 * 源失败的统一异常。
 *
 * [detail] 是现场细节（字段名、HTTP 码、地址、策略链结论），给人排查用；
 * 界面只读 [reason]，按它选一句人话。
 */
class VdoSourceException(
    val reason: VdoFailureReason,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception("Vdo source failed: $reason${detail?.let { " ($it)" }.orEmpty()}", cause)
