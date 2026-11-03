package com.liomix.mikomi.data.video.remote.vdoplugin.stream

import com.liomix.mikomi.data.video.remote.VdoSourceException
import com.liomix.mikomi.data.video.remote.VdoStream
import com.liomix.mikomi.data.video.remote.VdoStreamSniffer
import com.liomix.mikomi.data.video.remote.vdoplugin.VdoPluginEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密配置：解开 `player_aaaa` 里那串地址，再决定它是流还是下一跳页面。
 *
 * 两种结局都见过：
 * - 解出来就是 `.m3u8`/`.mp4` → 直接探；
 * - 解出来是另一个播放页（7sefun → lmm85 就是这种） → 再抓一页、按同样的候选规则翻一遍。
 *
 * 只解一层，不做递归：这类链条最多两级，再往下就是站点自己的 JS 在算参数了，
 * 硬解下去只会写出一堆针对某个站的死代码。
 */
@Singleton
class VdoEncryptedConfigResolver @Inject constructor(
    private val engine: VdoPluginEngine,
) : VdoStreamResolver {

    override val name: String = "EncryptedConfig"

    override val order: Int = 30

    override suspend fun resolve(request: VdoResolveRequest): VdoResolveOutcome {
        val pageHtml = request.pages.html(request.pageUrl, request.rule.baseUrl.ifBlank { null })
        val decoded = VdoPlayerConfig.decode(pageHtml) ?: return VdoResolveOutcome.NotApplicable

        // 情况一：解出来就是媒体地址。
        if (!VdoStreamSniffer.isStaticAsset(decoded) && !VdoStreamSniffer.isAd(decoded)) {
            probe(decoded, request.pageUrl)?.let { return VdoResolveOutcome.Playable(it) }
        }

        // 情况二：解出来是下一跳页面，抓它再翻候选。
        val nextHtml = runCatching { request.pages.html(decoded, request.pageUrl) }.getOrNull()
            ?: return VdoResolveOutcome.NotApplicable
        val candidates = VdoStreamCandidates.from(nextHtml, decoded)
            .take(VdoStreamCandidates.MAX_CANDIDATES)
        for (candidate in candidates) {
            val stream = probe(candidate, decoded) ?: continue
            return VdoResolveOutcome.Playable(stream)
        }
        return VdoResolveOutcome.NotApplicable
    }

    /** 探一次；拉不动就返回 null（这条候选不行，换下一个）。 */
    private suspend fun probe(url: String, referer: String): VdoStream? = try {
        engine.probeStream(url, referer)
    } catch (e: VdoSourceException) {
        null
    }
}
