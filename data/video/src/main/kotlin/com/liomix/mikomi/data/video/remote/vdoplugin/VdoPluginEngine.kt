package com.liomix.mikomi.data.video.remote.vdoplugin

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.w3c.dom.Node

/** 测试失败的原因。只给原因，文案由 feature 层映射——数据层不认识多语言。 */
enum class VdoTestReason {
    /** 规则本身不能用：JSON 坏了、缺字段、XPath 写错。 */
    RuleBroken,

    /** 连不上：DNS、超时、被墙、TLS 失败。 */
    NetworkError,

    /** 连上了但状态码不对（403/404/5xx）。 */
    HttpError,

    /** 搜索页打得通，但按 `searchList` 一条都没匹配到。 */
    NoResults,

    /** 进了详情页，但按 `chapterRoads`/`chapterResult` 找不到剧集。 */
    NoEpisodes,

    /** 进了播放页，但页面里翻不出任何播放地址。 */
    NoStream,

    /** 找到了播放地址，但拉不下来（403、被防盗链拦、返回的是网页）。 */
    StreamUnreachable,

    /** 站点播放器靠 JS 现拼地址，静态 HTML 里没有——这类规则得走 WebView。 */
    NeedWebView,
}

/** 规则执行过程中的失败。[detail] 是现场细节（字段名、HTTP 码、地址），给人排查用。 */
class VdoPluginException(
    val reason: VdoTestReason,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception("Vdo plugin failed: $reason${detail?.let { " ($it)" }.orEmpty()}", cause)

/** 搜索命中的一部片。 */
data class VdoSearchHit(val name: String, val url: String)

/** 一集（或一个播放源）。 */
data class VdoEpisode(val name: String, val url: String)

/** 一条线路上的全部剧集。规则里 `chapterRoads` 有多个节点时就是多线路。 */
data class VdoEpisodeRoad(val name: String, val episodes: List<VdoEpisode>)

/** 出流结果。 */
data class VdoStream(
    val url: String,
    val kind: VdoStreamKind,
    val statusCode: Int,
    /** 采样到的字节数（只读了开头 2KB，够认出是 m3u8 还是 mp4）。 */
    val byteCount: Int,
    /**
     * 播放器请求这条流时要带的头：`User-Agent` / `Referer` / `Cookie`。
     *
     * 不能省：站点按 Referer 判盗链，按 Cookie 判会话，缺一样就是 403 或一张空列表。
     * 播放层（Media3）与出流层（OkHttp）不共享会话，所以这里把值拷出来交给它。
     */
    val headers: Map<String, String> = emptyMap(),
)

enum class VdoStreamKind {
    /** HLS 播放列表（`.m3u8`），播放器要 Media3 的 HLS 模块。 */
    Hls,

    /** 直链 mp4。 */
    Mp4,

    /** 认不出类型但确实拿到了二进制流。 */
    Other,
}

/**
 * 插件源引擎：**搜索 → 剧集 → 出流**，全部按规则里的 XPath 真跑一遍。
 *
 * 这是「插件源」这件事的核心，所以和解析、仓库一起放在 `remote/vdoplugin` 里：
 * 规则怎么解释、页面怎么翻、流怎么取出来，只在这一处有定义，别的地方都调这里。
 *
 * 三步各自独立、互不缓存：搜索页和播放页可能不是同一套模板，缓存中间结果只会掩盖问题。
 *
 * 出流的做法：播放页通常只给一个 `<iframe>`，真正的地址藏在 iframe 的 JS 里
 * （`player_aaaa` / `url:` / `source:`），所以先取 iframe，再在两份 HTML 里按
 * 「明确的 m3u8/mp4 字面量 → 播放器配置里的 url 字段」的顺序翻，
 * 最后**真拉一次**开头 2KB 来确认拿到的是播放列表/视频而不是网页。
 * 这一步不做假的「找到了就算过」——用户拿着一个假通过去排查，比直接报失败更费时间。
 */
@Singleton
class VdoPluginEngine @Inject constructor(
    okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** 会话 Cookie。声明在 [client] 之前：属性初始化按书写顺序跑，反了就拿到 null。 */
    private val cookieJar = VdoCookieJar()

    /**
     * 引擎专用 client：短超时 + 跟随重定向 + 内存 Cookie。
     *
     * 15 秒是「整条请求」的上限：站点大多在墙外，默认超时叠上重定向能拖到半分钟，
     * 而测试面板等的就是这个转圈。
     *
     * Cookie 必须留住：搜索页下发的会话要一路带到播放页与流地址，不然出流那一步会被判盗链。
     */
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .build()

    /**
     * 按规则搜一部片。
     *
     * @return 命中的条目，顺序即页面上出现的顺序；空列表表示规则能跑但没搜到。
     */
    suspend fun search(ruleJson: String, keyword: String): List<VdoSearchHit> {
        val rule = RuleConfig(ruleJson)
        val url = rule.searchUrl.replace(KEYWORD_TOKEN, URLEncoder.encode(keyword, "UTF-8"))
        val document = VdoRuleSelector.document(fetch(url, rule.baseUrl), url)
        return VdoRuleSelector.select(document, rule.searchList).mapNotNull { node ->
            val name = VdoRuleSelector.firstText(node, rule.searchName) ?: return@mapNotNull null
            val href = VdoRuleSelector.firstHref(node, rule.searchResult, url) ?: return@mapNotNull null
            VdoSearchHit(name = name, url = href)
        }
    }

    /**
     * 取一部片的剧集。
     *
     * `chapterRoads` 为空时按单线路处理：把整页当成一条线路，直接按 `chapterResult` 取链接。
     */
    suspend fun episodes(ruleJson: String, detailUrl: String): List<VdoEpisodeRoad> {
        val rule = RuleConfig(ruleJson)
        val document = VdoRuleSelector.document(fetch(detailUrl, rule.baseUrl), detailUrl)
        val roads: List<Node> = if (rule.chapterRoads.isEmpty()) {
            listOf(document)
        } else {
            VdoRuleSelector.select(document, rule.chapterRoads)
        }
        return roads.mapIndexed { index, road ->
            val episodes = VdoRuleSelector.select(road, VdoRuleSelector.scoped(rule.chapterResult))
                .mapIndexedNotNull { position, link ->
                    val href = VdoRuleSelector.resolve(
                        detailUrl,
                        link.attr("href") ?: link.attr("data-href"),
                    ) ?: return@mapIndexedNotNull null
                    VdoEpisode(
                        // 站点的集数文字经常是空的（封面或图标当链接），给个序号兜底，别让列表出现空行。
                        name = link.text().orEmpty().ifBlank { "第 ${position + 1} 集" },
                        url = href,
                    )
                }
            VdoEpisodeRoad(
                name = road.text()?.take(ROAD_NAME_LIMIT).orEmpty().ifBlank { "线路 ${index + 1}" },
                episodes = episodes,
            )
        }.filter { it.episodes.isNotEmpty() }
    }

    /**
     * 从播放页出流。
     *
     * @throws VdoPluginException 找不到地址（[VdoTestReason.NoStream] / [VdoTestReason.NeedWebView]），
     *   或找到了但拉不动（[VdoTestReason.StreamUnreachable]）。
     */
    suspend fun stream(ruleJson: String, chapterUrl: String): VdoStream {
        val rule = RuleConfig(ruleJson)
        val page = fetch(chapterUrl, rule.baseUrl)
        val frameUrl = iframeUrl(page, chapterUrl)
        // iframe 拉不下来不算失败：有些站的播放器把地址直接写在主文档里。
        val frameHtml = frameUrl?.let { runCatching { fetch(it, chapterUrl) }.getOrNull() }

        val candidates = buildList {
            addAll(streamCandidates(page, chapterUrl))
            if (frameHtml != null) addAll(streamCandidates(frameHtml, frameUrl.orEmpty()))
        }.distinct().take(MAX_STREAM_CANDIDATES)

        if (candidates.isEmpty()) {
            throw VdoPluginException(
                reason = if (rule.useWebview) VdoTestReason.NeedWebView else VdoTestReason.NoStream,
                detail = chapterUrl,
            )
        }

        var lastFailure: VdoPluginException? = null
        candidates.forEach { candidate ->
            try {
                return probe(candidate, referer = frameUrl ?: chapterUrl)
            } catch (e: VdoPluginException) {
                lastFailure = e
            }
        }
        // 候选全试完还是不行：站点标了 useWebview 的话，几乎可以断定地址是 JS 现拼的
        // （实测 7sefun 的播放页里只有海报和统计脚本，架构上就没有 m3u8）。
        // 这时候报「需要 WebView」比报「拉不动」有用得多——用户知道这是源的形态问题，不是网络问题。
        throw if (rule.useWebview) {
            VdoPluginException(VdoTestReason.NeedWebView, lastFailure?.detail ?: chapterUrl)
        } else {
            lastFailure ?: VdoPluginException(VdoTestReason.StreamUnreachable, chapterUrl)
        }
    }

    /**
     * 这份规则是不是「站点自带播放器」的形态。
     *
     * 判据是规则里的两个开关：
     * - `useNativePlayer: true` —— 站点希望用它自己的播放器（我们只能给个 WebView 让它跑）；
     * - `useWebview: true` —— 地址要渲染后才出现，静态 HTML 里没有。
     *
     * 实测整个 KazumiRules 目录（18 条）都是两个都开，也就是说**这些源注定出不了直链**：
     * 播放页里只有海报和统计脚本。硬去抽地址只会抽到一张 png。
     * 所以播放侧要按这个判定分流，而不是一律先试拔地址。
     */
    fun requiresWebView(ruleJson: String): Boolean =
        RuleConfig(ruleJson).let { it.useWebview || it.useNativePlayer }

    /**
     * 交给 WebView 的 UA：规则写了就用规则的，没写就用桌面 UA。
     *
     * **不能返回 null**。WebView 的默认 UA 是移动版，而这类站点（实测 7sefun）见到
     * Android UA 会 302 到 `/app/android.php`，返回一个 4.9KB 的「下载 App」页——
     * 里面既没有 `player_aaaa` 也没有播放器，WebView 就是一片白。
     * 桌面 UA 拿到的才是真正的播放页。
     */
    fun userAgentOf(ruleJson: String): String =
        RuleConfig(ruleJson).userAgent.takeIf { it.isNotEmpty() } ?: USER_AGENT

    /**
     * 把 WebView 里拦到的一次子资源请求，变成一条可播的流。
     *
     * 这是「不猜 HTML、等页面自己暴露地址」那条路：页面加载起来后，播放器（hls.js / dplayer）
     * 一定会去 GET 那个 `.m3u8`，`shouldInterceptRequest` 就把它抓住了。
     * 好处是**跨域 iframe 也拦得到**——主文档的 JS 进不去 iframe，网络层却看得见。
     *
     * 关键是把**请求头一并带出来**：拿到的地址离开 WebView 之后由 Media3 去请求，
     * 两边不共享会话，`Referer` / `Cookie` / `User-Agent` 缺一样就是 403。
     *
     * @param pageUrl 播放页地址，请求头里没带 Referer 时拿它兜底。
     * @param cookie WebView 自己的 Cookie（`CookieManager.getCookie`）。
     *   它不在 `requestHeaders` 里——Chromium 是在更底层注入的，所以得单独取。
     * @return 不像视频流时返回 null（调用方应当忽略这次请求）。
     */
    fun sniffStream(
        url: String,
        requestHeaders: Map<String, String>,
        pageUrl: String,
        cookie: String?,
    ): VdoStream? {
        if (!VdoStreamSniffer.looksLikeVideo(url, requestHeaders)) return null
        return VdoStream(
            url = url,
            kind = VdoStreamSniffer.kindOf(url),
            // 嗅探只证明「这个请求发出去了」，没有探测过响应体，这两项不适用。
            statusCode = 0,
            byteCount = 0,
            headers = playbackHeaders(url, pageUrl, requestHeaders, cookie),
        )
    }

    /** 嗅探到的流要带的头：优先用**页面自己那次请求**的值，那是最贴近真实播放器的。 */
    private fun playbackHeaders(
        url: String,
        pageUrl: String,
        requestHeaders: Map<String, String>,
        cookie: String?,
    ): Map<String, String> = buildMap {
        put("User-Agent", requestHeaders["User-Agent"] ?: requestHeaders["user-agent"] ?: USER_AGENT)
        put("Referer", requestHeaders["Referer"] ?: requestHeaders["referer"] ?: pageUrl)
        // WebView 的 Cookie 比我们内存 jar 的更准（那才是真正通过鉴权的会话）。
        (cookie?.takeIf { it.isNotBlank() } ?: cookieJar.headerFor(url))
            ?.let { put("Cookie", it) }
    }

    /**
     * 取一个页面。
     *
     * Referer 一定要带：这类站点的搜索结果与播放页普遍校验来源，缺了会给你一张空白页——
     * 那会被误判成「规则选择器写错了」。
     */
    private suspend fun fetch(url: String, referer: String?): String = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw VdoPluginException(VdoTestReason.HttpError, "HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: VdoPluginException) {
            throw e
        } catch (e: IOException) {
            throw VdoPluginException(VdoTestReason.NetworkError, e.message ?: e::class.java.simpleName, e)
        } catch (e: IllegalArgumentException) {
            throw VdoPluginException(VdoTestReason.RuleBroken, url, e)
        }
    }

    /**
     * 真拉一次流地址，确认它是能播的东西。
     *
     * 只读开头 2KB：HLS 播放列表第一行必是 `#EXTM3U`，mp4 的 `ftyp` 盒也在最前面，
     * 这点数据足够判断类型，又不会为了测试把一整部片子下载下来。
     */
    private suspend fun probe(url: String, referer: String?): VdoStream = withContext(ioDispatcher) {
        // 头要先算好：既用于这次探测，也原样交给播放器，两边必须完全一致。
        val headers = streamHeaders(url, referer)
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-$PROBE_BYTES")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw VdoPluginException(VdoTestReason.StreamUnreachable, "HTTP ${response.code}")
                }
                val bytes = response.body?.let(::readHead) ?: ByteArray(0)
                val head = String(bytes, Charsets.ISO_8859_1)
                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                val kind = when {
                    head.trimStart().startsWith("#EXTM3U") || contentType.contains("mpegurl") -> VdoStreamKind.Hls
                    contentType.startsWith("video/") || url.contains(".mp4", ignoreCase = true) -> VdoStreamKind.Mp4
                    // 图片、网页、脚本、JSON 都是页面零件（海报、统计、播放器配置），不是流。
                    // 以前一律当 Other 放过去，结果播放器拿一张 png 去解码：黑屏 + 一个看不懂的解码错误。
                    contentType.contains("html") ||
                        contentType.startsWith("image/") ||
                        contentType.startsWith("text/") ||
                        contentType.contains("json") ||
                        contentType.contains("javascript") ||
                        contentType.contains("xml") ->
                        throw VdoPluginException(
                            VdoTestReason.StreamUnreachable,
                            "返回的是 ${contentType.ifBlank { "非视频内容" }}，不是视频流",
                        )

                    else -> VdoStreamKind.Other
                }
                VdoStream(
                    url = url,
                    kind = kind,
                    statusCode = response.code,
                    byteCount = bytes.size,
                    headers = headers,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: VdoPluginException) {
            throw e
        } catch (e: IOException) {
            throw VdoPluginException(VdoTestReason.StreamUnreachable, e.message ?: e::class.java.simpleName, e)
        }
    }

    /**
     * 播放器请求这条流时要带的头。
     *
     * 三样各有来处：UA 是我们一贯用的桌面 UA（站点按 UA 给不同码率的流）；
     * Referer 取播放页或 iframe 页（站点靠它判盗链，缺了直接 403）；
     * Cookie 取当前会话（部分站点要播放页下发的短期票据）。
     */
    private fun streamHeaders(url: String, referer: String?): Map<String, String> = buildMap {
        put("User-Agent", USER_AGENT)
        referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
        cookieJar.headerFor(url)?.let { put("Cookie", it) }
    }

    /** 读响应开头固定字节数；服务器不支持 Range 时会一直读，所以自己封顶。 */    private fun readHead(body: okhttp3.ResponseBody): ByteArray {
        val buffer = ByteArray(PROBE_BYTES)
        var total = 0
        runCatching {
            body.byteStream().use { input ->
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read <= 0) break
                    total += read
                }
            }
        }
        return buffer.copyOf(total)
    }

    /** 播放页里的 `<iframe>` / `<frame>` 地址；没有就返回 null。 */
    private fun iframeUrl(html: String, pageUrl: String): String? =
        IFRAME_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { VdoRuleSelector.resolve(pageUrl, it) }

    /**
     * 从一份 HTML 里翻出可能的播放地址，按可信度排序。
     *
     * 顺序是有讲究的：`"xxx.m3u8"` 这种带后缀的字面量最可信；`url: 'xxx'` 是播放器配置，
     * 需要额外确认它像个地址（很多站把 `url` 用作别的东西）；相对地址按当前页拼绝对地址。
     */
    private fun streamCandidates(html: String, baseUrl: String): List<String> = buildList {
        STREAM_LITERAL_REGEX.findAll(html).forEach { match ->
            VdoRuleSelector.resolve(baseUrl, unescape(match.groupValues[1]))
                ?.takeIf(::isLikelyVideo)
                ?.let(::add)
        }
        // `<video src>` / `<source src>` 是明确的媒体标签，比裸 `src=` 可信得多。
        MEDIA_TAG_SRC_REGEX.findAll(html).forEach { match ->
            VdoRuleSelector.resolve(baseUrl, unescape(match.groupValues[1]))
                ?.takeIf(::isLikelyVideo)
                ?.let(::add)
        }
        PLAYER_URL_REGEX.findAll(html).forEach { match ->
            val raw = unescape(match.groupValues[1])
            if (raw.contains("://") || raw.contains(".m3u8", ignoreCase = true) || raw.contains(".mp4", ignoreCase = true)) {
                VdoRuleSelector.resolve(baseUrl, raw)?.takeIf(::isLikelyVideo)?.let(::add)
            }
        }
    }

    /**
     * 过滤页面零件。
     *
     * 实测教训：播放页里 `src=` / `url:` 命中的绝大多数是海报、头像、统计脚本，
     * 一个 200 的 png 会让「出流成功」这句谎话说得很像真的。按后缀先筛一遍，
     * 剩下的再交给 [probe] 用 content-type 复核。
     *
     * 后缀清单只有一份，在 [VdoStreamSniffer]——嗅探那条路用的是同一套判据。
     */
    private fun isLikelyVideo(url: String): Boolean = !VdoStreamSniffer.isStaticAsset(url)

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

    /** 规则里与**运行时**有关的那几项；解析失败一律算规则坏了。 */
    private class RuleConfig(json: String) {
        private val root = runCatching { JSONObject(json) }
            .getOrElse { throw VdoPluginException(VdoTestReason.RuleBroken, "JSON", it) }

        val baseUrl: String = root.optString("baseURL").trim()
        val useWebview: Boolean = root.optBoolean("useWebview", false)
        val useNativePlayer: Boolean = root.optBoolean("useNativePlayer", false)
        /** 规则自带的 UA；多数为空，表示用我们的默认 UA。 */
        val userAgent: String = root.optString("userAgent").trim()
        val searchUrl: String = require("searchURL")
        val searchList: String = require("searchList")
        val searchName: String = require("searchName")
        val searchResult: String = require("searchResult")
        val chapterResult: String = require("chapterResult")

        /** 允许为空：单线路规则不写这一项。 */
        val chapterRoads: String = root.optString("chapterRoads").trim()

        private fun require(key: String): String =
            root.optString(key).trim().takeIf { it.isNotEmpty() }
                ?: throw VdoPluginException(VdoTestReason.RuleBroken, key)
    }

    private companion object {
        /** 桌面 UA：移动版页面经常把搜索结果换成 JS 渲染，测试反而更容易看不懂。 */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        const val KEYWORD_TOKEN = "@keyword"
        const val CALL_TIMEOUT_SECONDS = 15L
        const val PROBE_BYTES = 2048
        const val MAX_STREAM_CANDIDATES = 4
        const val ROAD_NAME_LIMIT = 16

        /** `<iframe src=...>` / `<frame src=...>`，单双引号都认。 */
        val IFRAME_REGEX = Regex("""<i?frame[^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        /** 带后缀的字面量地址，最可信。 */
        val STREAM_LITERAL_REGEX =
            Regex("""["'](https?://[^"'\s]+?\.(?:m3u8|mp4)(?:\?[^"'\s]*)?)["']""", RegexOption.IGNORE_CASE)

        /** 播放器配置里的地址字段：`url: '...'`、`"source": "..."`、`video = '...'`。 */
        val PLAYER_URL_REGEX =
            Regex("""\b(?:url|source|video)\b\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        /** `<video src=...>` / `<source src=...>`：媒体标签里的地址，可信度仅次于字面量。 */
        val MEDIA_TAG_SRC_REGEX =
            Regex("""<(?:video|source)[^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    }
}
