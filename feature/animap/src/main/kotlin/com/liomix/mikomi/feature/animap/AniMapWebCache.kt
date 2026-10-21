package com.liomix.mikomi.feature.animap

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/**
 * 巡礼地图 WebView 进程级缓存。
 *
 * WebView 本体挂在 [SurfaceWebViewContainer] 的虚拟显示上，切换 tab 时容器被销毁；
 * 这里把 WebView 从容器中摘下保留，下次进入 [AniMapScreen] 时通过
 * [SurfaceWebViewContainer] 的 cachedWebView 参数重新挂载，避免重复加载地图。
 *
 * WebView 必须在主线程创建/销毁；[cached] 仅主线程访问。
 */
internal object AniMapWebCache {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 缓存的 WebView（已加载完成的地图页面）。 */
    private var cached: WebView? = null

    /** 缓存的 URL，与 [cached] 保持一致。 */
    private var cachedUrl: String? = null

    /** 缓存是否已完成首屏加载（决定是否显示加载指示）。 */
    private var loaded: Boolean = false

    /**
     * 获取（或创建）对应 [url] 的 WebView，并构建挂载它的容器。
     * 返回的容器已持有 WebView，可直接交给 AndroidView 显示。
     */
    // 命中判定同样不比对 cachedUrl 与 url 的字符串相等：anitabi 会重定向改写 URL，
    // cachedUrl 记录的是页面最终 URL。只要存在缓存 WebView 本体就命中复用。
    fun acquire(context: Context, url: String): SurfaceWebViewContainer {
        val existing = cached
        return if (existing != null) {
            SurfaceWebViewContainer(context, url, existing)
        } else {
            // 无缓存：销毁旧缓存，走全新加载路径（容器内部会创建 WebView）。
            destroyNow()
            SurfaceWebViewContainer(context, url)
        }
    }

    /**
     * 释放容器外壳（虚拟显示/窗口挂载），但保留 WebView 本体供下次复用。
     * 若 WebView 尚未完成加载，则不缓存，随容器一起销毁。
     */
    fun release(container: SurfaceWebViewContainer) {
        val web = container.webView
        // 不比对 web.url 与 cachedUrl 的字符串相等：anitabi 会重定向改写 URL，
        // web.url 与记录值不等时严格相等会让缓存永不命中。
        // 只要首屏加载完成过（loaded && cachedUrl != null），WebView 即可缓存复用。
        if (web != null && loaded && cachedUrl != null) {
            container.detachForCache()
            cached = web
        } else {
            container.destroy()
            cached = null
            cachedUrl = null
            loaded = false
        }
    }

    /** WebView 首屏加载完成时更新缓存状态。 */
    fun onLoaded(url: String) {
        cachedUrl = url
        loaded = true
    }

    /** 销毁缓存（主线程）。 */
    fun destroy() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyNow()
        } else {
            mainHandler.post { destroyNow() }
        }
    }

    private fun destroyNow() {
        cached?.let {
            it.stopLoading()
            it.destroy()
        }
        cached = null
        cachedUrl = null
        loaded = false
    }
}
