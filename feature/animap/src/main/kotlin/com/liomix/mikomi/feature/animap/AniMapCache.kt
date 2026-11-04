package com.liomix.mikomi.feature.animap

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * 巡礼地图 WebView 的预热与复用。
 *
 * 打开巡礼页要等四段：Chromium 首次初始化 → HTML 往返 → JS 与作品数据 → mapbox 首帧。
 * 前三段都不要求页面**可见**，只有首帧要求。这个对象就是把前三段提前做掉：
 *
 * 1. [warmUp] 把页面加载进一个 `INVISIBLE` 的容器里。`INVISIBLE` 只停绘制，Chromium
 *    仍按真实尺寸布局、跑 JS、拉数据 —— 地图对象与作品数据全部就绪，而首页一帧都不会被抢。
 *    （这一点是 `alpha = 0` 做不到的：它照常每帧渲染，那才是「预热拖累首页」的真正原因。）
 * 2. 页面加载完再等一小会儿（站点还要拉数据、建地图），把 WebView 从容器上摘下来存着。
 *    此时它没有 parent、不参与任何绘制，只是内存里躺着一份「已经能用」的页面。
 * 3. [acquire] 时挂回新容器，`VISIBLE` 之后才开始画首帧。
 *
 * 换 tab 复用的还是同一套：容器随组合销毁，WebView 本体留在这里。
 *
 * 硬约束：所有方法都必须在主线程调用（WebView 只能在主线程创建与操作）。
 */
internal object AniMapCache : ComponentCallbacks2 {

    /**
     * 已就绪页面在后台保留的时长。
     *
     * 两分钟太短：切去首页看两眼、回个消息再回来，页面就没了，等于每次都要重来一遍。
     * 半小时够覆盖一轮「看番 → 聊天 → 回来查取景地」，也不至于整场挂着一个几十 MB 的
     * 渲染进程。
     */
    private const val CACHE_TTL_MILLIS = 30 * 60 * 1_000L

    /**
     * 预热失败后的最短重试间隔。
     *
     * 三条触发路径（首页空闲、横滑手势、点 tab）会不停调过来；弱网下预热又可能白做。
     * 没有这个间隔，等于每滑动一下就重建一次 Chromium。
     */
    private const val WARM_UP_RETRY_GAP_MILLIS = 5 * 60 * 1_000L

    /**
     * 页面 `onPageFinished` 之后继续留在窗口里的时间。
     *
     * 文档到了不代表地图到了：站点还要拉作品数据、建 mapbox、铺首屏瓦片。
     * 这段空窗正是要提前吃掉的部分，摘早了等于只预热了一个空壳 HTML。
     */
    private const val WARM_UP_SETTLE_MILLIS = 4_000L

    /** 预热总超时：页面一直不 finish（被拦、断网），别让隐藏容器永远挂在窗口上。 */
    private const val WARM_UP_TIMEOUT_MILLIS = 20_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var scheduledDestroy: Runnable? = null
    private var scheduledSettle: Runnable? = null
    private var scheduledTimeout: Runnable? = null

    /** 已加载完成、可直接挂回界面的 WebView（无 parent，不参与绘制）。 */
    private var cached: WebView? = null

    /** 最近完成加载的页面 URL（站点会重定向改写，记录的是最终地址）。 */
    private var cachedUrl: String? = null

    /** [cached] 是否来自预热：它还没在用户眼前出现过，挂回去要等一次真正的首帧。 */
    private var cachedFromWarmUp = false

    /** [cached] 是否已完成首屏加载（决定 [release] 时留不留它）。 */
    private var loaded = false

    /** 正在预热、还挂在窗口上的容器。 */
    private var warming: AniMapWebViewContainer? = null

    /** 预热容器挂在哪（Activity 的 content root）。 */
    private var warmingHost: ViewGroup? = null

    private var lastWarmUpAt = 0L
    private var callbacksRegistered = false

    /**
     * 提前把巡礼页加载起来。幂等：已有可用页面、正在预热、或距上次失败不足
     * [WARM_UP_RETRY_GAP_MILLIS] 时直接返回。
     *
     * @param activity 只用来拿窗口（content root）与构造 WebView；不长期持有
     */
    fun warmUp(activity: Activity, url: String) {
        if (activity.isFinishing || activity.isDestroyed) return

        if (warming != null || cached != null) {
            // 已经有东西可用：把即将到期的销毁撤掉，别让刚预热好的页面在用户点击前被回收。
            cancelScheduledDestroy()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastWarmUpAt < WARM_UP_RETRY_GAP_MILLIS) return

        val host = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        lastWarmUpAt = now
        registerCallbacks(activity.applicationContext)

        val container = AniMapWebViewContainer(activity, url)
        // INVISIBLE 而不是 alpha = 0：前者让 Chromium 停止绘制，我们只要它的布局与数据；
        // 后者仍会每帧跑 mapbox 的渲染循环，首页滑动时掉的就是首页的帧。
        container.visibility = View.INVISIBLE
        container.onPageFinished = { view, finishedUrl ->
            container.pageUrl = finishedUrl ?: view.url ?: url
            scheduleSettle()
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        host.addView(container, params)
        warming = container
        warmingHost = host
        scheduleTimeout()
    }

    /**
     * 获取（或创建）对应 [url] 的容器，交给 `AndroidView` 显示。
     *
     * 三种来源，优先级从高到低：
     * - **正在预热** → 直接转正。用户已经进来了，再建一个 WebView 等于白做一次
     *   Chromium 初始化，还会把下到一半的页面丢掉。
     * - **已缓存的 WebView** → 套进新容器挂回去（切 tab 的主要路径）。
     * - 都没有 → 走全新加载。
     */
    fun acquire(context: Context, url: String): AniMapWebViewContainer {
        cancelScheduledDestroy()

        warming?.let { container ->
            val host = warmingHost
            warming = null
            warmingHost = null
            cancelScheduledSettle()
            cancelScheduledTimeout()
            if (host != null && container.parent === host) host.removeView(container)
            // 预热时页面可能已经 finish 了，而 Screen 的 onPageFinished 已经不会再触发；
            // 把「加载完成」这一笔补记上，否则用户切走时这个页面会被当成没加载完销毁。
            container.pageUrl?.let(::onLoaded)
            container.promoteFromWarmUp()
            return container
        }

        val existing = cached
        return if (existing != null) {
            cached = null
            val pending = cachedFromWarmUp
            cachedFromWarmUp = false
            AniMapWebViewContainer(context, url, existing, presentPending = pending)
        } else {
            AniMapWebViewContainer(context, url)
        }
    }

    /**
     * 容器随组合卸载时调用：把 WebView 从容器上摘下来留给下次复用。
     * 页面没加载完就切走（或加载失败）则不缓存，随容器一起销毁。
     */
    fun release(container: AniMapWebViewContainer) {
        val web = container.webView
        // 不比对 web.url 与 cachedUrl 的字符串相等：anitabi 会重定向改写 URL，
        // 只要首屏加载完成过（loaded && cachedUrl != null）即可复用。
        if (web != null && loaded && cachedUrl != null) {
            container.detachForCache()
            cached = web
            // 已经在用户眼前展示过，下次挂回去可以直接出图，不用再等首帧。
            cachedFromWarmUp = false
            scheduleDestroy()
        } else {
            container.destroy()
            clearCachedState()
        }
    }

    /** WebView 首屏加载完成时更新缓存状态。 */
    fun onLoaded(url: String) {
        cachedUrl = url
        loaded = true
    }

    /** 销毁全部缓存（主线程）。 */
    fun destroy() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyNow()
        } else {
            mainHandler.post { destroyNow() }
        }
    }

    /**
     * 内存吃紧时立刻丢掉预热页与缓存页。
     *
     * 地图页是常驻几十 MB 的渲染进程，首页本来就在内存峰值上；留着它换来的只是
     * 「下次快一点」，代价却可能是整个进程被 LMK 挑中。
     */
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) destroy()
    }

    override fun onLowMemory() {
        destroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    private fun registerCallbacks(context: Context) {
        if (callbacksRegistered) return
        callbacksRegistered = true
        context.applicationContext.registerComponentCallbacks(this)
    }

    private fun scheduleSettle() {
        cancelScheduledSettle()
        val action = Runnable {
            scheduledSettle = null
            finishWarming(discard = false)
        }
        scheduledSettle = action
        mainHandler.postDelayed(action, WARM_UP_SETTLE_MILLIS)
    }

    private fun scheduleTimeout() {
        cancelScheduledTimeout()
        val action = Runnable {
            scheduledTimeout = null
            // 迟迟不 finish：留在窗口上只会白占资源，交给 finishWarming 判断要不要留。
            finishWarming(discard = false)
        }
        scheduledTimeout = action
        mainHandler.postDelayed(action, WARM_UP_TIMEOUT_MILLIS)
    }

    /**
     * 收尾一次预热：把容器从窗口上摘掉，能用的 WebView 存进 [cached]。
     *
     * @param discard true 表示明确丢弃（内存吃紧、宿主已失效）
     */
    private fun finishWarming(discard: Boolean) {
        cancelScheduledSettle()
        cancelScheduledTimeout()
        val container = warming ?: return
        val host = warmingHost
        warming = null
        warmingHost = null

        // 容器已经跟着 Activity 一起 detach：它的 Context 已经废了，存下来再用必崩。
        val hostAlive = container.isAttachedToWindow
        if (host != null && container.parent === host) host.removeView(container)

        val web = container.webView
        val pageUrl = container.pageUrl
        if (discard || !hostAlive || web == null || pageUrl.isNullOrBlank()) {
            container.destroy()
            return
        }
        container.detachForCache()
        cached = web
        cachedUrl = pageUrl
        // 预热页还没在用户眼前出现过，挂回去时要等一次真正的首帧。
        cachedFromWarmUp = true
        loaded = true
        scheduleDestroy()
    }

    private fun scheduleDestroy() {
        cancelScheduledDestroy()
        val action = Runnable {
            scheduledDestroy = null
            destroyNow()
        }
        scheduledDestroy = action
        mainHandler.postDelayed(action, CACHE_TTL_MILLIS)
    }

    private fun cancelScheduledDestroy() {
        scheduledDestroy?.let(mainHandler::removeCallbacks)
        scheduledDestroy = null
    }

    private fun cancelScheduledSettle() {
        scheduledSettle?.let(mainHandler::removeCallbacks)
        scheduledSettle = null
    }

    private fun cancelScheduledTimeout() {
        scheduledTimeout?.let(mainHandler::removeCallbacks)
        scheduledTimeout = null
    }

    private fun destroyNow() {
        cancelScheduledDestroy()
        cancelScheduledSettle()
        cancelScheduledTimeout()

        warming?.let { container ->
            val host = warmingHost
            if (host != null && container.parent === host) host.removeView(container)
            container.destroy()
        }
        warming = null
        warmingHost = null

        cached?.let {
            it.stopLoading()
            it.destroy()
        }
        clearCachedState()
    }

    private fun clearCachedState() {
        cached = null
        cachedUrl = null
        cachedFromWarmUp = false
        loaded = false
    }
}
