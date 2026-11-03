package com.liomix.mikomi.data.video.remote.vdoplugin.stream

import com.liomix.mikomi.data.video.remote.VdoLineCapability
import com.liomix.mikomi.data.video.remote.VdoSourceException
import com.liomix.mikomi.data.video.remote.vdoplugin.VdoPluginEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 先验缓存：这条线路上次是直链，这次先验证那条地址还能不能用。
 *
 * 这是最便宜的一条路——一次 2KB 的探测，成功就直接播，连播放页都不用抓。
 * 但**必须验证**：地址可能带时效签名，昨天的链接今天就是 403。
 * 验证失败不报错，交给后面的策略重来一遍；连续两次不符时缓存会被作废。
 */
@Singleton
class VdoCachedDirectResolver @Inject constructor(
    private val engine: VdoPluginEngine,
) : VdoStreamResolver {

    override val name: String = "CachedDirect"

    override val order: Int = 10

    override suspend fun resolve(request: VdoResolveRequest): VdoResolveOutcome {
        val cached = request.cached as? VdoLineCapability.Direct ?: return VdoResolveOutcome.NotApplicable
        // 用当前会话重新算请求头：缓存里那张地址可能还要 Referer/Cookie，而过期的头只会添乱。
        val stream = try {
            engine.probeStream(cached.url, request.pageUrl)
        } catch (e: VdoSourceException) {
            return VdoResolveOutcome.NotApplicable
        }
        return VdoResolveOutcome.Playable(stream)
    }
}
