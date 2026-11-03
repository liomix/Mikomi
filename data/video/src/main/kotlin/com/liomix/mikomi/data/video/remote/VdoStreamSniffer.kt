package com.liomix.mikomi.data.video.remote

/**
 * 「一次 WebView 子资源请求算不算视频流」的判定。
 *
 * 为什么要有这个类：凡是要把播放页交给站点播放器的线路（`useWebview`），**地址都是 JS 现拼的**，静态 HTML 里翻不到
 * （实测 7sefun：页面里只有 base64 加密串，再跳一层到第三方播放器）。但不管怎么加密，
 * 播放器拿到地址后第一件事一定是去 GET 那个 `.m3u8`——**请求一定会在网络层出现**。
 * 所以与其在 HTML 里猜，不如把页面加载起来，在 `shouldInterceptRequest` 里等它自己暴露。
 *
 * 这条路的决定性好处：**跨域 iframe 也拦得到**。请求走的是同一个 WebView 的网络栈，
 * 主文档的 JS 进不去 iframe，网络层却看得见。
 *
 * 判定本身是纯字符串逻辑、没有状态，所以放在共用层给所有源复用：
 * 播放层（`PlayerWebView`）用它做**预筛**，把明显不是视频的请求直接丢掉，不要往上传；
 * 出流层（`VdoPluginEngine.sniffStream` 等）用它复核并组装 [VdoStream]。
 */
object VdoStreamSniffer {

    /**
     * 静态资源后缀。
     *
     * 实测教训：播放页里命中的绝大多数请求是海报、头像、统计脚本，甚至还有把图片当播放器封面的。
     * 一个 200 的 png 会让「出流成功」这句谎话说得很像真的——播放器拿它去解码就是黑屏。
     */
    val STATIC_SUFFIXES: List<String> = listOf(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".bmp",
        ".css", ".js", ".json", ".xml", ".woff", ".woff2", ".ttf",
        ".html", ".htm", ".wasm",
    )

    /**
     * 广告域名/路径特征。
     *
     * 这些站的播放器旁边一定挂着广告，而**广告本身也是 m3u8/mp4**，形态上跟正片一模一样。
     * 唯一能区分的是域名，所以先按特征把它们挡掉，否则用户点开番剧先看到 15 秒广告。
     */
    private val AD_MARKERS: List<String> = listOf(
        "googleads", "googlesyndication", "doubleclick", "adtrafficquality",
        "adkwai", "prestrain", "/ad/", "adserver", "adsystem",
    )

    /** 这个地址是不是静态资源（图片/脚本/样式/字体/文档）。 */
    fun isStaticAsset(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return STATIC_SUFFIXES.any { path.endsWith(it) }
    }

    /**
     * 这个地址是不是广告。
     *
     * 静态解析那条路也要用它：广告本身也是 mp4/m3u8，探起来 200 + `video/mp4`，
     * 一切「正常」，播出来却是 15 秒广告。唯一能区分的是域名特征。
     */
    fun isAd(url: String): Boolean {
        val lower = url.lowercase()
        return AD_MARKERS.any { lower.contains(it) }
    }

    /**
     * 这次请求像不像视频流。
     *
     * @param url 请求地址（绝对地址，WebView 给的）
     * @param requestHeaders 这次请求带的头。mp4 靠 `Range` 认，没有它认不出来
     *   （很多 CDN 的 mp4 地址连后缀都没有）。
     */
    fun looksLikeVideo(url: String, requestHeaders: Map<String, String>): Boolean {
        if (isAd(url)) return false

        val lower = url.lowercase()
        val path = lower.substringBefore('?').substringBefore('#')
        // HLS 播放列表：第一层 master 和子列表都以 .m3u8 结尾，最可信。
        if (path.endsWith(".m3u8")) return true
        if (path.endsWith(".mp4")) return true

        // 没有后缀的媒体地址：播放器取片段一定带 `Range: bytes=...`，
        // 而页面零件（js/css/图片）从不带 Range。这是 Dart-Mikomi 用的同一条判据。
        val range = requestHeaders["Range"] ?: requestHeaders["range"] ?: return false
        if (!range.startsWith("bytes=", ignoreCase = true)) return false
        return !isStaticAsset(lower)
    }

    /** 按地址猜流类型；猜不出时交给 ExoPlayer 自己嗅探（`Other` → mimeType null）。 */
    fun kindOf(url: String): VdoStreamKind {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> VdoStreamKind.Hls
            lower.contains(".mp4") -> VdoStreamKind.Mp4
            else -> VdoStreamKind.Other
        }
    }
}
