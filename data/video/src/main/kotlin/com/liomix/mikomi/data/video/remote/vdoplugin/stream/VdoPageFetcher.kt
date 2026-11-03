package com.liomix.mikomi.data.video.remote.vdoplugin.stream

import com.liomix.mikomi.data.video.remote.vdoplugin.VdoPluginEngine

/**
 * 一次解析过程中的页面缓存。
 *
 * 为什么要它：静态解析和加密配置解析看的是**同一张播放页**。各抓一次就是两次请求，
 * 而这几个站点普遍在墙外、单次 300ms 起。缓存按 URL 存在请求作用域里，
 * 请求结束即释放——不做跨集复用，播放地址的页面本来就会变。
 *
 * 只缓存成功结果：抓失败再试一次是有意义的（刚好赶上一次抖动），缓存失败没有意义。
 */
class VdoPageFetcher internal constructor(
    private val engine: VdoPluginEngine,
) {

    private val cache = mutableMapOf<String, String>()

    /**
     * 成功抓到过几个页面。
     *
     * 出流层用它区分两种「没找到地址」：页面打开了但没有地址（真是 JS 拼的）、
     * 还是页面根本没打开（网络不通）。前者该进 WebView，后者进 WebView 只会得到一张白页。
     */
    var loadedPages: Int = 0
        private set

    internal suspend fun html(url: String, referer: String?): String {
        cache[url]?.let { return it }
        return engine.fetchPage(url, referer).also {
            cache[url] = it
            loadedPages++
        }
    }
}
