package com.liomix.mikomi.data.video.remote.vdoplg

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.data.video.VdoCookieJar
import com.liomix.mikomi.data.video.VdoEpisode
import com.liomix.mikomi.data.video.VdoEpisodeRoad
import com.liomix.mikomi.data.video.VdoFailureReason
import com.liomix.mikomi.data.video.VdoSearchHit
import com.liomix.mikomi.data.video.VdoSourceException
import com.liomix.mikomi.data.video.VdoStream
import com.liomix.mikomi.data.video.VdoStreamKind
import com.liomix.mikomi.data.video.VdoStreamSniffer
import com.liomix.mikomi.data.video.remote.vdoplg.rule.InvalidVdoPlgSourceException
import com.liomix.mikomi.data.video.remote.vdoplg.rule.VdoPlgDocument
import com.liomix.mikomi.data.video.remote.vdoplg.rule.VdoPlgSelector
import com.liomix.mikomi.data.video.remote.vdoplg.rule.attr
import com.liomix.mikomi.data.video.remote.vdoplg.rule.text
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
import org.w3c.dom.Node

/**
 * 插件源引擎：**搜索 → 剧集**，外加两个基础动作（抓页面、探流）。
 *
 * 它不再是「出流」的入口——出流由 `VdoPlgStreamPlanner` 编排，按线路选策略。
 * 引擎只提供**不含判断的能力**：按源翻页面、发一次请求、验一次地址。
 * 这样「哪种办法先试」这类决策就不会渗进 HTTP 代码里。
 *
 * 两步各自独立、互不缓存：搜索页和播放页可能不是同一套模板，缓存中间结果只会掩盖问题。
 */
@Singleton
class VdoPlgEngine @Inject constructor(
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
     * 按源搜一部片。
     *
     * @return 命中的条目，顺序即页面上出现的顺序；空列表表示源能跑但没搜到。
     */
    suspend fun search(ruleJson: String, keyword: String): List<VdoSearchHit> {
        val rule = VdoPlgDocument.parse(ruleJson)
        val url = rule.searchUrl.replace(KEYWORD_TOKEN, URLEncoder.encode(keyword, "UTF-8"))
        val document = VdoPlgSelector.document(fetchPage(url, rule.baseUrl), url)
        return VdoPlgSelector.select(document, rule.searchList).mapNotNull { node ->
            val name = VdoPlgSelector.firstText(node, rule.searchName) ?: return@mapNotNull null
            val href = VdoPlgSelector.firstHref(node, rule.searchResult, url) ?: return@mapNotNull null
            VdoSearchHit(name = name, url = href)
        }
    }

    /**
     * 取一部片的剧集。
     *
     * `chapterRoads` 为空时按单线路处理：把整页当成一条线路，直接按 `chapterResult` 取链接。
     */
    suspend fun episodes(ruleJson: String, detailUrl: String): List<VdoEpisodeRoad> {
        val rule = VdoPlgDocument.parse(ruleJson)
        val document = VdoPlgSelector.document(fetchPage(detailUrl, rule.baseUrl), detailUrl)
        val roads: List<Node> = if (rule.chapterRoads.isEmpty()) {
            listOf(document)
        } else {
            VdoPlgSelector.select(document, rule.chapterRoads)
        }
        return roads.mapIndexed { index, road ->
            val episodes = VdoPlgSelector.select(road, VdoPlgSelector.scoped(rule.chapterResult))
                .mapIndexedNotNull { position, link ->
                    val href = VdoPlgSelector.resolve(
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
     * 交给 WebView 的 UA：源写了就用源里的，没写就用桌面 UA。
     *
     * **不能返回 null**。WebView 的默认 UA 是移动版，而这类站点（实测 7sefun）见到
     * Android UA 会 302 到 `/app/android.php`，返回一个 4.9KB 的「下载 App」页——
     * 里面既没有 `player_aaaa` 也没有播放器，WebView 就是一片白。
     * 桌面 UA 拿到的才是真正的播放页。
     */
    fun userAgentOf(ruleJson: String): String = try {
        VdoPlgDocument.parse(ruleJson).userAgent.takeIf { it.isNotEmpty() } ?: USER_AGENT
    } catch (e: InvalidVdoPlgSourceException) {
        // 取 UA 是顺手的事，不值得为一份坏 json 抛出去：回默认桌面 UA 就好。
        // 真正需要规则内容的地方（搜索/剧集/出流）会各自如实报错。
        USER_AGENT
    }

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

    /**
     * 取一个页面。给 [VdoPlgStreamPlanner] 的策略链用。
     *
     * Referer 一定要带：这类站点的搜索结果与播放页普遍校验来源，缺了会给你一张空白页——
     * 那会被误判成「XPath 选择器写错了」。
     */
    internal suspend fun fetchPage(url: String, referer: String?): String = withContext(ioDispatcher) {
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
                    throw VdoSourceException(VdoFailureReason.HttpError, "HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: VdoSourceException) {
            throw e
        } catch (e: IOException) {
            throw VdoSourceException(VdoFailureReason.NetworkError, e.message ?: e::class.java.simpleName, e)
        } catch (e: IllegalArgumentException) {
            throw VdoSourceException(VdoFailureReason.RuleBroken, url, e)
        }
    }

    /**
     * 真拉一次流地址，确认它是能播的东西。
     *
     * 只读开头 2KB：HLS 播放列表第一行必是 `#EXTM3U`，mp4 的 `ftyp` 盒也在最前面，
     * 这点数据足够判断类型，又不会为了测试把一整部片子下载下来。
     */
    internal suspend fun probeStream(url: String, referer: String?): VdoStream = withContext(ioDispatcher) {
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
                    throw VdoSourceException(VdoFailureReason.StreamUnreachable, "HTTP ${response.code}")
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
                        throw VdoSourceException(
                            VdoFailureReason.StreamUnreachable,
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
        } catch (e: VdoSourceException) {
            throw e
        } catch (e: IOException) {
            throw VdoSourceException(VdoFailureReason.StreamUnreachable, e.message ?: e::class.java.simpleName, e)
        }
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

    private companion object {
        /** 桌面 UA：移动版页面经常把搜索结果换成 JS 渲染，测试反而更容易看不懂。 */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        const val KEYWORD_TOKEN = "@keyword"
        const val CALL_TIMEOUT_SECONDS = 15L
        const val PROBE_BYTES = 2048
        const val ROAD_NAME_LIMIT = 16
    }
}
