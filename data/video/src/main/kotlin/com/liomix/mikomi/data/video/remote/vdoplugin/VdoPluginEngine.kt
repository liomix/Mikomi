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

    /**
     * 引擎专用 client：短超时 + 跟随重定向。
     *
     * 15 秒是「整条请求」的上限：站点大多在墙外，默认超时叠上重定向能拖到半分钟，
     * 而测试面板等的就是这个转圈。
     */
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
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
        throw lastFailure ?: VdoPluginException(VdoTestReason.StreamUnreachable, chapterUrl)
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
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Range", "bytes=0-$PROBE_BYTES")
            .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
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
                    url.contains(".mp4", ignoreCase = true) || contentType.startsWith("video/") -> VdoStreamKind.Mp4
                    contentType.contains("html") ->
                        throw VdoPluginException(VdoTestReason.StreamUnreachable, "返回的是网页，不是视频流")

                    else -> VdoStreamKind.Other
                }
                VdoStream(url = url, kind = kind, statusCode = response.code, byteCount = bytes.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: VdoPluginException) {
            throw e
        } catch (e: IOException) {
            throw VdoPluginException(VdoTestReason.StreamUnreachable, e.message ?: e::class.java.simpleName, e)
        }
    }

    /** 读响应开头固定字节数；服务器不支持 Range 时会一直读，所以自己封顶。 */
    private fun readHead(body: okhttp3.ResponseBody): ByteArray {
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
            VdoRuleSelector.resolve(baseUrl, unescape(match.groupValues[1]))?.let(::add)
        }
        PLAYER_URL_REGEX.findAll(html).forEach { match ->
            val raw = unescape(match.groupValues[1])
            if (raw.contains("://") || raw.contains(".m3u8", ignoreCase = true) || raw.contains(".mp4", ignoreCase = true)) {
                VdoRuleSelector.resolve(baseUrl, raw)?.let(::add)
            }
        }
    }

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

        /** 播放器配置里的地址字段：`url: '...'`、`"source": "..."`、`src = '...'`。 */
        val PLAYER_URL_REGEX =
            Regex("""\b(?:url|source|src|video)\b\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    }
}
