package com.liomix.mikomi.data.video.remote.vdoplg.stream

import com.liomix.mikomi.data.video.VdoSourceException
import com.liomix.mikomi.data.video.remote.vdoplg.VdoPlgEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 静态解析：把播放页（以及它嵌的 iframe）当成文本，翻出候选地址逐个探测。
 *
 * 为什么还要留着这条「看起来过时」的路：
 * - 能直链的线路一次命中，省掉一次 WebView 启动（那是几百毫秒起步）；
 * - 嗅探是**兜底**，不是唯一手段。源里明确写了 `useWebview: false` 的，本来就该走这里。
 *
 * 它失败时不报错，只返回「不适用」——后面还有加密配置和渲染两条路。
 */
@Singleton
class VdoPlgStaticHtmlResolver @Inject constructor(
    private val engine: VdoPlgEngine,
) : VdoPlgStreamResolver {

    override val name: String = "StaticHtml"

    override val order: Int = 20

    override suspend fun resolve(request: VdoPlgResolveRequest): VdoPlgResolveOutcome {
        // 逐层下钻 iframe。这类站的播放页常见「播放页 → 播放器壳 → 第三方播放器」两层，
        // 只抓第一层会停在壳页上（那里面往往只有一段初始化 JS，没有地址）。
        //
        // 上限两层，不是怕麻烦：三层以上基本都是站点自己的 JS 在现算参数，
        // 钻下去只会多花请求，最后一个字节也翻不出来。页面走 [VdoPlgPageFetcher] 缓存，
        // 和加密配置那条路共用，不会重复抓同一张页。
        val pages = mutableListOf(request.pageUrl to pageHtml(request))
        var depth = 1
        while (depth <= MAX_FRAME_DEPTH) {
            val (html, base) = pages.last()
            val frameUrl = VdoPlgStreamCandidates.iframeIn(html, base) ?: break
            val frameHtml = runCatching { request.pages.html(frameUrl, base) }.getOrNull() ?: break
            pages += frameUrl to frameHtml
            depth++
        }

        val candidates = pages
            .flatMap { (base, html) -> VdoPlgStreamCandidates.from(html, base) }
            .distinct()
            .take(VdoPlgStreamCandidates.MAX_CANDIDATES)

        if (candidates.isEmpty()) return VdoPlgResolveOutcome.NotApplicable

        // Referer 用**最深那层**的地址：站点判盗链看的是「谁嵌了我」，不是顶层的播放页。
        val referer = pages.last().first
        for (candidate in candidates) {
            val stream = try {
                engine.probeStream(candidate, referer)
            } catch (e: VdoSourceException) {
                continue
            }
            return VdoPlgResolveOutcome.Playable(stream)
        }
        return VdoPlgResolveOutcome.NotApplicable
    }

    private suspend fun pageHtml(request: VdoPlgResolveRequest): String =
        request.pages.html(request.pageUrl, request.rule.baseUrl.ifBlank { null })

    private companion object {
        /** 最多再往下钻两层 iframe（含主文档共三层页面）。 */
        const val MAX_FRAME_DEPTH = 2
    }
}
