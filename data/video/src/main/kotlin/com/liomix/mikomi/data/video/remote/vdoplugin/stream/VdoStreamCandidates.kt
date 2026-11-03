package com.liomix.mikomi.data.video.remote.vdoplugin.stream

import com.liomix.mikomi.data.video.remote.VdoStreamSniffer
import com.liomix.mikomi.data.video.remote.vdoplugin.rule.VdoRuleSelector

/**
 * 从 HTML 里把「可能的播放地址」捞出来，并按可信度排序。
 *
 * 这是一个**廉价的前置**，不是主路径。实测绝大多数源的地址是 JS 现拼的，
 * 静态页面里只剩下海报和统计脚本；真正靠得住的是网络层嗅探。
 * 但它仍然值得先跑一遍：能直链的线路就省掉一次 WebView，进度条和倍速也都回来了。
 *
 * 顺序是有讲究的：
 * 1. 带 `.m3u8` / `.mp4` 后缀的字面量——几乎不会错；
 * 2. `<video src>` / `<source src>`——明确的媒体标签；
 * 3. 播放器配置里的 `url:` / `source:` / `video:`——这层最脏，很多站把 `url` 用作别的东西，
 *    所以额外要求它长得像个地址。
 */
internal object VdoStreamCandidates {

    /** 一条线路最多试这么多个候选：再多就不是「解析」而是「瞎猜」了，白花请求。 */
    const val MAX_CANDIDATES = 4

    /** 播放页里的 `<iframe>` / `<frame>` 地址；没有就返回 null。 */
    fun iframeIn(html: String, pageUrl: String): String? =
        IFRAME_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { VdoRuleSelector.resolve(pageUrl, it) }

    fun from(html: String, baseUrl: String): List<String> = buildList {
        STREAM_LITERAL_REGEX.findAll(html).forEach { match ->
            VdoRuleSelector.resolve(baseUrl, unescape(match.groupValues[1]))
                ?.takeIf(::isPlayableAddress)
                ?.let(::add)
        }
        MEDIA_TAG_SRC_REGEX.findAll(html).forEach { match ->
            VdoRuleSelector.resolve(baseUrl, unescape(match.groupValues[1]))
                ?.takeIf(::isPlayableAddress)
                ?.let(::add)
        }
        PLAYER_URL_REGEX.findAll(html).forEach { match ->
            val raw = unescape(match.groupValues[1])
            val looksLikeAddress = raw.contains("://") ||
                raw.contains(".m3u8", ignoreCase = true) ||
                raw.contains(".mp4", ignoreCase = true)
            if (looksLikeAddress) {
                VdoRuleSelector.resolve(baseUrl, raw)?.takeIf(::isPlayableAddress)?.let(::add)
            }
        }
    }

    /**
     * 这个地址有没有可能是视频流。
     *
     * 实测教训：播放页里 `src=` / `url:` 命中的绝大多数是海报、头像、统计脚本，
     * 一个 200 的 png 会让「出流成功」这句谎话说得很像真的——播放器拿它去解码就是黑屏。
     * 另外广告也要挡：广告本身也是 mp4/m3u8，探起来一切正常，播出来却是广告片。
     *
     * 两条判据都来自 [VdoStreamSniffer]——嗅探那条路用的是同一套，别处不许再抄清单。
     */
    fun isPlayableAddress(url: String): Boolean =
        !VdoStreamSniffer.isStaticAsset(url) && !VdoStreamSniffer.isAd(url)

    /**
     * 还原 JS 字符串里的转义。
     *
     * 播放器配置里的地址常被写成 `https:\/\/host\/a.m3u8`（斜杠转义）或 `\u002F` 形式，
     * 不去掉的话拼出来的 URL 直接是坏的；`&amp;` 是 HTML 属性的实体，正则从原文里捞出来的也得还原。
     */
    private fun unescape(raw: String): String = raw
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("&amp;", "&")
        .trim()

    private val IFRAME_REGEX = Regex("""<i?frame[^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private val STREAM_LITERAL_REGEX =
        Regex("""["'](https?://[^"'\s]+?\.(?:m3u8|mp4)(?:\?[^"'\s]*)?)["']""", RegexOption.IGNORE_CASE)

    private val MEDIA_TAG_SRC_REGEX =
        Regex("""<(?:video|source)[^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private val PLAYER_URL_REGEX =
        Regex("""\b(?:url|source|video)\b\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
}
