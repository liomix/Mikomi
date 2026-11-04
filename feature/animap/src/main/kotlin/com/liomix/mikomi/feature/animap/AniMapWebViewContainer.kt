package com.liomix.mikomi.feature.animap

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 导航开始后抢注样式的几个时间点（毫秒）。
 *
 * 0 是先排进消息队列、等这一帧的导航真正起来；后面几档覆盖「文档解析出 head」的时机。
 * 站点那块 loading 浮层从出现到被移除可能只有几百毫秒，单靠 onPageFinished 一定错过。
 */
private val EARLY_INJECT_DELAYS_MILLIS = longArrayOf(0L, 120L, 320L, 700L)

/**
 * 等地图画出来的轮询间隔（毫秒）。
 *
 * 只在 `onPageFinished` 之后跑，命中一次就停；120ms 对一屏的等待无感，
 * 又不会像 16ms 那样在主页面上反复跨 JSBridge。
 */
private const val MAP_READY_POLL_MILLIS = 120L

/**
 * 「瓦片齐了」这条弱信号的宽限期（毫秒）：过了这个点还没等到 `loaded()` 就先放行。
 *
 * 刚建图时瓦片队列是空的，`areTilesLoaded()` 会瞬间返回 true，
 * 不加宽限期就会出现「还没开始画就撤了遮罩」。
 */
private const val MAP_READY_TILES_GRACE_MILLIS = 2_500L

/**
 * 等地图的上限（毫秒）：超时就撤掉遮罩。
 *
 * 宁可露出一点半成品，也不要让人对着转圈干等 —— 弱网下瓦片可能几分钟才齐，
 * 但那不是「加载中」，是「地图能用但不好看」。
 */
private const val MAP_READY_TIMEOUT_MILLIS = 8_000L

/**
 * 点击命中后回读相机状态的时间点（毫秒）。
 *
 * 起飞本身有 60ms 延迟，脚本里的验收在 1.8s 收尾，所以这个数要压在它之后。
 * 只用于调试包里的 Toast，正式包不读。
 */
private const val DIAGNOSTIC_DELAY_MILLIS = 2000L

/**
 * 预热页挂回界面后等首帧的上限（毫秒）。
 *
 * 预热阶段已经把文档、JS、作品数据、地图对象都准备好了，挂回窗口只差「画第一帧」，
 * 这个数字给得比冷启动的 [MAP_READY_TIMEOUT_MILLIS] 短得多；等太久只会让用户
 * 对着一个其实已经能用的页面看转圈。
 */
private const val PRESENT_READY_TIMEOUT_MILLIS = 3_000L

/** 解析注入脚本回包用的序列化器（页面回的是 JSON；生成 JS 字面量的那份在 [AniMapScript]）。 */
private val JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 巡礼地图的 WebView 容器。
 *
 * 只负责「什么时候注入、注入哪一段、回包怎么解」：脚本本身全在 [AniMapScript] 里，
 * 这里不出现任何一行 JS。
 *
 * **这里刻意不碰窗口与渲染层**：WebView 就是一个普通的子 View，和 Compose 的
 * `AndroidView` 一起待在 Activity 窗口里，触摸、焦点、输入法全部走系统原生那一条路。
 *
 * 曾经的做法是把 WebView 塞进 `TextureView` 提供的 `VirtualDisplay`、由 `Presentation`
 * 承载，以躲开宿主 View 树上的反射遍历。隔离确实干净，代价是**键盘彻底用不了**：
 * 输入法只绑在默认显示上，虚拟显示里没有 IME，页内搜索框点了不会弹键盘，
 * `InputMethodManager.showSoftInput` 一律返回 false。地图页要搜片名、要打字，
 * 这个代价换不起——所以回到最朴素的内嵌方案。
 *
 * 对齐 flutter_inappwebview 插件的默认设置：
 * loadWithOverviewMode=true、useWideViewPort=true、textZoom=100。
 */
class AniMapWebViewContainer(
    context: Context,
    private val initialUrl: String,
    private val cachedWebView: WebView? = null,
    private val presentPending: Boolean = false,
) : FrameLayout(context) {

    /** 容器内的 WebView，创建后可调用 goBack 等。 */
    var webView: WebView? = null
        private set

    /**
     * 页面最终 URL（站点会把初始地址重定向改写）。
     *
     * 预热出去的容器要靠它记缓存键：拿到它的时候 `onPageFinished` 早就触发过了，
     * 界面上那个回调不会再来第二次。
     */
    internal var pageUrl: String? = null

    /** 是否正在等「挂回窗口后的第一帧」。 */
    private var presentPendingFrame = presentPending

    /**
     * 是否还需要等一次首帧才能撤掉加载遮罩。
     *
     * 来自预热或刚转正的容器为 true：页面数据齐了但还没画过，直接撤遮罩会先闪一屏底色。
     * 已经完整展示过的缓存页为 false，切回 tab 时应当瞬时出现。
     */
    internal val needsPresentWait: Boolean
        get() = presentPendingFrame

    /** 页面开始加载/加载完成时回调 true/false。 */
    var onLoadingChanged: (Boolean) -> Unit = {}

    /**
     * 轮询代次：每次导航自增。
     *
     * 上一页那次「等地图就绪」的轮询可能还在排队，新导航开始后必须让它作废，
     * 否则它一命中就会把新页面的加载指示撤掉（或反之，把已经画好的页面重新盖住）。
     */
    private var readyGeneration = 0

    /** 「最近更新」全量结果的页内缓存（跟着这个容器一起活，页面重载即失效）。 */
    private var cachedLatest = AniMapSearchPayload()

    /** 页面加载完成（onPageFinished）时回调，携带页面当前 URL（可能被站点重定向改写）。 */
    var onPageFinished: (WebView, String?) -> Unit = { _, _ -> }

    /** 是否复用了已加载完成的缓存 WebView。 */
    internal val hasCachedWebView: Boolean
        get() = cachedWebView != null

    init {
        val web = cachedWebView ?: createWebView(context)
        // 缓存命中时 WebView 可能仍挂在旧容器上（detachForCache 未摘除的兜底），
        // 挂载前必须先摘下，否则 addView 抛
        // IllegalStateException "The specified child already has a parent."。
        (web.parent as? ViewGroup)?.removeView(web)
        addView(web, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        webView = web
        if (cachedWebView == null) {
            onLoadingChanged(true)
            web.loadUrl(initialUrl)
        } else {
            // 摘下来时暂停过（见 detachForCache），挂回窗口必须恢复：
            // 不恢复的话页面不跑 JS 定时器，地图不会响应点击与缩放。
            web.resumeTimers()
            web.onResume()
            // 缓存页面的样式早就注入过；SPA 偶尔会重建 head，补一次不吃亏。
            injectOverlayStyle(web)
            if (presentPending) {
                // 预热页的数据和样式都好了，但它是不可见状态下准备的，首帧还没画过。
                // 挂回窗口后必须等一次真正的首帧，否则会先露出「底色 + 半个地图」。
                web.post { awaitMapReady(web, PRESENT_READY_TIMEOUT_MILLIS) }
            }
        }
    }

    /**
     * 把预热中的容器转正为界面上的容器。
     *
     * 与「缓存页挂回新容器」的区别：这里连容器本体一起接手，页面还在加载中，
     * 不能重建（重建等于白预热）。所以只做三件事：显示、作废预热期那一轮判定、
     * 重新起一轮「等首帧」。
     */
    internal fun promoteFromWarmUp() {
        val view = webView ?: return
        visibility = View.VISIBLE
        presentPendingFrame = true
        // 预热期那次轮询可能还在排队，而它是在「不可见」状态下判的，结论不作数。
        readyGeneration++
        view.post { awaitMapReady(view, PRESENT_READY_TIMEOUT_MILLIS) }
    }

    /** 停止加载并销毁 WebView。 */
    fun destroy() {
        webView?.let {
            it.stopLoading()
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
    }

    /**
     * 仅把 WebView 从容器上摘下，保留本体由 [AniMapCache] 持有，供下次进入复用。
     * 调用后容器不可再用。
     *
     * 摘下来还不够：实测 WebView 离开窗口之后 **rAF 依旧在跑**，`document.visibilityState`
     * 仍是 `visible`，mapbox 的渲染循环会以每秒上百帧空转——这正是「后台预热拖累首页」
     * 的实际来源。所以这里必须显式 [WebView.onPause] + [WebView.pauseTimers]。
     *
     * `pauseTimers` 是进程级的（会影响所有 WebView）。当前只有地图这一个 WebView，
     * 将来若新增，需要在各自恢复时重新 `resumeTimers`。
     */
    fun detachForCache() {
        webView?.let { web ->
            web.onPause()
            web.pauseTimers()
            (web.parent as? ViewGroup)?.removeView(web)
        }
        webView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView = WebView(context).apply {
        // 保持 WebView 默认渲染模式：不要用 LAYER_TYPE_SOFTWARE（会废掉 WebGL）。
        setLayerType(View.LAYER_TYPE_NONE, null)
        // 不暴露无障碍节点：地图页没有需要读屏遍历的内容，也省掉一遍树遍历。
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        WebView.setWebContentsDebuggingEnabled(context.isDebuggable())
        settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            loadWithOverviewMode = true
            useWideViewPort = true
            textZoom = 100
            // 地图用 Canvas/WebGL 手势缩放，须关掉 WebView 自带缩放，
            // 否则 WebView 拦截双指捏合，地图收不到 pinch、底图渲染错位。
            setSupportZoom(false)
            setBuiltInZoomControls(false)
            setDisplayZoomControls(false)
            setSupportMultipleWindows(false)
        }
        // 禁用长按弹出文本选择菜单；输入框的键盘不受影响。
        setOnLongClickListener { true }
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 每次导航都重新盖住：加载指示的语义是「这一页还没画出来」，
                // 不是「这一次打开还没完成」，站内跳转/刷新手势同样要等。
                readyGeneration++
                onLoadingChanged(true)
                // 站点那块「加载中」浮层在首屏数据到达后就被从 DOM 上摘掉了：
                // 等 onPageFinished 时它早就不在，连手动抓 HTML 都抓不到。
                // 所以从导航一开始就分几轮抢注——文档一旦有了 head 就把规则落下去，
                // 后续插进来的节点交给脚本里的 MutationObserver。
                for (delay in EARLY_INJECT_DELAYS_MILLIS) {
                    view.postDelayed({ injectOverlayStyle(view) }, delay)
                }
            }

            override fun onPageFinished(view: WebView, finishedUrl: String?) {
                // 先自己记一笔：预热出去的那次加载没有界面在听回调，
                // 缓存层只能从这里拿最终地址（站点会重定向改写 URL）。
                pageUrl = finishedUrl ?: view.url
                injectOverlayStyle(view)
                // 这里**不再**直接把 loading 置 false：HTML 到齐时地图还是空白的，
                // 站点原本用那块浅蓝启动页盖住这段时间，启动页被藏掉后得由我们接上，
                // 否则用户会先看到一片空白再突然出图。
                awaitMapReady(view)
                // 显式限定到类属性，避免与本匿名类重写的 onPageFinished 方法同名遮蔽，
                // 否则 Kotlin 会解析成递归调用自身导致 StackOverflow。
                this@AniMapWebViewContainer.onPageFinished(view, finishedUrl)
            }
        }
    }

    /** 把隐藏规则注入当前文档；文档还没就绪时脚本自己会退出，交给下一轮重试。 */
    private fun injectOverlayStyle(view: WebView) {
        runCatching { view.evaluateJavascript(HIDE_OVERLAY_JS, null) }
    }

    /**
     * 轮询 [MAP_READY_JS]，地图画出来之后再把加载指示撤掉。
     *
     * 为什么不在 `onPageFinished` 直接收：那是「文档下载完」，不是「地图能看」。
     * anitabi 的首页要在 JS 里再拉一次作品数据、等 mapbox 铺完首屏瓦片；
     * 站点自己用 `.ui-loading` 盖住这段时间，我们把那块藏了，就得自己接住这段空窗。
     *
     * 三道保险：`2` 立刻收；`1`（瓦片齐但 loaded() 没转真）过了宽限期就收；
     * 整体超过 [timeoutMillis] 强制收 —— 弱网下宁可先露出半成品，
     * 也不能让人对着转圈等到天荒地老。
     * 页面已经卸载或又发起新导航时，这一轮静默作废（见 [readyGeneration]）。
     *
     * @param timeoutMillis 这一轮最多等多久。冷启动用默认值；预热页挂回来时数据都齐了，
     *   只差首帧，用更短的上限，别让用户对着一个其实已经能用的页面看转圈
     */
    private fun awaitMapReady(
        view: WebView,
        timeoutMillis: Long = MAP_READY_TIMEOUT_MILLIS,
    ) {
        val generation = readyGeneration
        val startedAt = SystemClock.uptimeMillis()
        val poll = object : Runnable {
            private val self = this
            override fun run() {
                // 容器已卸载 / 已经换了页面：直接作废，别去动现在的 loading。
                if (view !== webView || generation != readyGeneration) return
                val elapsed = SystemClock.uptimeMillis() - startedAt
                if (elapsed >= timeoutMillis) {
                    presentPendingFrame = false
                    onLoadingChanged(false)
                    return
                }
                val release = {
                    if (generation == readyGeneration) {
                        presentPendingFrame = false
                        onLoadingChanged(false)
                    }
                }
                runCatching {
                    view.evaluateJavascript(MAP_READY_JS) { raw ->
                        if (generation != readyGeneration) return@evaluateJavascript
                        // evaluateJavascript 回的是 JSON：数字就是 "0" / "1" / "2"。
                        val state = raw?.trim()?.trim('"')?.toIntOrNull() ?: 0
                        val tilesEnough = elapsed >= MAP_READY_TILES_GRACE_MILLIS
                        if (state >= 2 || (state == 1 && tilesEnough)) {
                            release()
                        } else {
                            view.postDelayed(self, MAP_READY_POLL_MILLIS)
                        }
                    }
                }.onFailure {
                    // 求值本身失败（页面正在销毁等）：给一次机会再来，别一次失败就永久转圈。
                    view.postDelayed(self, MAP_READY_POLL_MILLIS)
                }
            }
        }
        view.postDelayed(poll, MAP_READY_POLL_MILLIS)
    }

    /**
     * 在页面里检索，返回命中（作品 / 地标 / 城市三类混排）。
     *
     * 必须在主线程调用（WebView 的硬约束）；Compose 里由 `rememberCoroutineScope`
     * 起的协程默认就在主线程，因此这里不再切换调度器。
     * WebView 已销毁或页面处在不可求值状态时返回空包，不向调用方抛异常——
     * 搜索是随输入触发的，一次失败不应该让界面停在「搜索中」。
     */
    suspend fun search(keyword: String): AniMapSearchPayload {
        val view = webView ?: return AniMapSearchPayload()
        val script = searchScript(keyword)
        return suspendCancellableCoroutine { continuation ->
            val dispatched = runCatching {
                view.evaluateJavascript(script) { raw ->
                    if (continuation.isActive) continuation.resume(parsePayload(raw))
                }
            }
            // evaluateJavascript 本身抛了异常时回调不会再来了，必须在这里收尾，否则协程永久挂起。
            if (dispatched.isFailure && continuation.isActive) {
                continuation.resume(AniMapSearchPayload())
            }
        }
    }

    /**
     * 未输入时给建议框垫底的「最近更新」作品（前 [LATEST_HIT_LIMIT] 条，按最近更新降序）。
     *
     * 与 [search] 同一套回包结构，界面上不用分叉；只是排序键从「关键词匹配」换成了
     * 站点的 `modified` 时间。数据没到位时 `ready` 为 false，调方据此显示「加载中」
     * 而不是「没有内容」。
     *
     * 结果按页面缓存一份：列表不随用户输入变化，每次展开都重跑一遍脚本没有意义。
     * 缓存跟页面实例同寿，页面重载后容器会重建，自然失效。
     */
    suspend fun latestHits(): AniMapSearchPayload {
        if (cachedLatest.ready && cachedLatest.hits.isNotEmpty()) return cachedLatest
        val view = webView ?: return AniMapSearchPayload()
        val script = latestScript()
        return suspendCancellableCoroutine { continuation ->
            val dispatched = runCatching {
                view.evaluateJavascript(script) { raw ->
                    val payload = parsePayload(raw)
                    if (payload.ready) cachedLatest = payload
                    if (continuation.isActive) continuation.resume(payload)
                }
            }
            if (dispatched.isFailure && continuation.isActive) {
                continuation.resume(AniMapSearchPayload())
            }
        }
    }

    /**
     * 打开一条命中：作品缩放到取景地范围、地标落到具体坐标、城市圈住该城市全部取景点。
     *
     * 按 [AniMapHit.id] 在页面里现取对象，不用搜索时的下标——那份快照会过期。
     * 不重新加载页面，站点自己的筛选 / 标题 / URL 同步都保留。
     *
     * @param onResult 注入脚本的返回值 + 2s 后回读的相机状态，
     *   形如 `ok|map=1|pts=2 / from=1.50@… to=14.56@… | end=14.56@… (jumped)`。
     *   前半段说明点击走到哪一步（`miss-bangumi` / `no-geo` / `no-app` 等），
     *   后半段说明相机到底动没动；抓不到 WebView 时不会回调。
     */
    fun openHit(hit: AniMapHit, onResult: ((String?) -> Unit)? = null) {
        val view = webView ?: return
        val script = openHitScript(hit.kind, hit.id, hit.title)
        runCatching {
            view.evaluateJavascript(script) { raw ->
                if (onResult == null) return@evaluateJavascript
                // 同步拿到的只是「脚本走到哪一步」；相机是 60ms 后才起跑的，
                // 真正有没有动起来要等脚本里的验收回调（1.3s）写完页内记录再取。
                view.postDelayed({
                    runCatching {
                        view.evaluateJavascript("window.__mikomiFit || ''") { diag ->
                            onResult("${raw.asText()} / ${diag.asText()}")
                        }
                    }.onFailure { onResult("${raw.asText()} / diag-throw:${it.message}") }
                }, DIAGNOSTIC_DELAY_MILLIS)
            }
        }.onFailure { onResult?.invoke("throw:${it.message}") }
    }

    /** `evaluateJavascript` 回来的是 JSON 字符串字面量，去掉外层引号再给人看。 */
    private fun String?.asText(): String =
        this?.trim().orEmpty().removeSurrounding("\"").replace("\\\"", "\"")

    private fun parsePayload(raw: String?): AniMapSearchPayload {
        if (raw.isNullOrBlank() || raw == "null") return AniMapSearchPayload()
        return runCatching { JSON.decodeFromString<AniMapSearchPayload>(raw) }
            .getOrElse { AniMapSearchPayload() }
    }

    private fun Context.isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
