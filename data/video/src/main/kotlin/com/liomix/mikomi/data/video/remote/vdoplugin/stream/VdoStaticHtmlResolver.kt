package com.liomix.mikomi.data.video.remote.vdoplugin.stream

import com.liomix.mikomi.data.video.remote.VdoSourceException
import com.liomix.mikomi.data.video.remote.vdoplugin.VdoPluginEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 静态解析：把播放页（以及它嵌的 iframe）当成文本，翻出候选地址逐个探测。
 *
 * 为什么还要留着这条「看起来过时」的路：
 * - 能直链的线路一次命中，省掉一次 WebView 启动（那是几百毫秒起步）；
 * - 嗅探是**兜底**，不是唯一手段。规则明确写了 `useWebview: false` 的源，本来就该走这里。
 *
 * 它失败时不报错，只返回「不适用」——后面还有加密配置和渲染两条路。
 */
@Singleton
class VdoStaticHtmlResolver @Inject constructor(
    private val engine: VdoPluginEngine,
) : VdoStreamResolver {

    override val name: String = "StaticHtml"

    override val order: Int = 20

    override suspend fun resolve(request: VdoResolveRequest): VdoResolveOutcome {
        val pageHtml = request.pages.html(request.pageUrl, request.rule.baseUrl.ifBlank { null })

        // iframe 拉不下来不算失败：有些站的播放器把地址直接写在主文档里。
        val frameUrl = VdoStreamCandidates.iframeIn(pageHtml, request.pageUrl)
        val frameHtml = frameUrl?.let { url ->
            runCatching { request.pages.html(url, request.pageUrl) }.getOrNull()
        }

        val candidates = buildList {
            addAll(VdoStreamCandidates.from(pageHtml, request.pageUrl))
            if (frameHtml != null && frameUrl != null) {
                addAll(VdoStreamCandidates.from(frameHtml, frameUrl))
            }
        }.distinct().take(VdoStreamCandidates.MAX_CANDIDATES)

        if (candidates.isEmpty()) return VdoResolveOutcome.NotApplicable

        // Referer 用 iframe 的地址：站点判盗链看的是「谁嵌了我」，不是顶层播放页。
        val referer = frameUrl ?: request.pageUrl
        for (candidate in candidates) {
            val stream = try {
                engine.probeStream(candidate, referer)
            } catch (e: VdoSourceException) {
                continue
            }
            return VdoResolveOutcome.Playable(stream)
        }
        return VdoResolveOutcome.NotApplicable
    }
}
