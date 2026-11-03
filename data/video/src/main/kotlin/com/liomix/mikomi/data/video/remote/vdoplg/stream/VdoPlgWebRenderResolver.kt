package com.liomix.mikomi.data.video.remote.vdoplg.stream

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 最后一条路：把播放页交给 WebView，让站点自己的播放器去取地址。
 *
 * 它不是「退而求其次」，而是当前最可靠的一条——实测 KazumiRules 全部 18 份源的播放页里
 * 都没有直链，地址由站点 JS 现算。区别只在于：交给 WebView 之后，我们还会在**网络层**
 * 拦住它真正发出的那个 `.m3u8` 请求（模块根包的 `VdoStreamSniffer`），拦到就升级回原生播放器。
 *
 * 所以这个 resolver 的结论永远是「需要渲染」，它只负责让链路有一个明确的终点。
 */
@Singleton
class VdoPlgWebRenderResolver @Inject constructor() : VdoPlgStreamResolver {

    override val name: String = "WebRender"

    override val order: Int = 100

    override suspend fun resolve(request: VdoPlgResolveRequest): VdoPlgResolveOutcome {
        val reason = if (request.rule.prefersSitePlayer) {
            "源声明使用站点播放器"
        } else {
            "静态解析与加密配置都没有拿到地址"
        }
        return VdoPlgResolveOutcome.NeedRender(reason)
    }
}
