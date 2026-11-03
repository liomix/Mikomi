package com.liomix.mikomi.feature.player.component

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.liomix.mikomi.data.video.remote.VdoStreamSniffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * WebView 形态的画面：**先当嗅探器，拦不到流才继续当播放器**。
 *
 * 为什么必须有 WebView：规则里的 `useWebview` 意味着地址是 JS 现拼的。实测 7sefun 的播放页里
 * 只有一句 `<script>var player_aaaa = {"encrypt":2,"url":"..."}</script>`，那串地址要
 * base64 → urlencode 解一层，再跳到第三方播放器页，那一页里也只有广告 mp4。静态解析到此为止。
 *
 * 但**请求一定会在网络层出现**：播放器拿到地址后第一件事就是 GET 那个 `.m3u8`。
 * 所以这里挂 [WebViewClient.shouldInterceptRequest]，等它自己暴露（`onSubRequest` 回调），
 * 外层拿到后就切成 Media3——进度条、倍速、填充方式全部回到我们手里。
 * 这条路的决定性好处是**跨域 iframe 也拦得到**，因为请求走的是同一个 WebView 网络栈，
 * 而主文档的 JS 是进不去 iframe 的。
 *
 * 拦不到（页面要手动点、或者播放器做了特殊封装）就保持现状：用户看到的还是这个页面，
 * 可以用站点自己的播放器看，只是控制权在它那边。这是兜底，不是主路径。
 *
 * 几个必须开的设置（少一个就白屏/不自动播/点不动）：
 * - `javaScriptEnabled`：地址和播放器都是 JS 起的；
 * - `domStorageEnabled`：不少播放器把配置写 localStorage，关掉就初始化失败；
 * - `mediaPlaybackRequiresUserGesture = false`：否则自动播放被拦，**请求也就不会发出来**，
 *   嗅探直接失效（这是嗅探能工作的前提，不只是为了好看）；
 * - `MIXED_CONTENT_ALWAYS_ALLOW`：https 页面里嵌 http 流很常见，默认会被拦掉。
 *
 * UA 由调用方给（`VdoPluginEngine.userAgentOf` 保证非空）：**必须是桌面 UA**，
 * 这类站点见到 Android UA 会 302 到 `/app/android.php`，返回一个「下载 App」页。
 *
 * 站点播放器点自己的全屏按钮时，WebView 会回调 `onShowCustomView`——把那个 view 塞进
 * 一个覆盖层并通知外层进入全屏，否则那个按钮看起来能点、点了没反应。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PlayerWebView(
    url: String,
    reloadToken: Int,
    userAgent: String?,
    onRequestFullscreen: (Boolean) -> Unit,
    /**
     * 网络层拦到一次「像视频流」的子资源请求。
     *
     * 这是这条路上最关键的一个回调：站点播放器拿到地址后一定会去 GET 那个 `.m3u8`，
     * 我们在 WebView 的网络栈里截住它，交给外层换成原生播放器。**跨域 iframe 也拦得到**。
     *
     * @param pageKey 发起这次请求时 WebView 正在显示的播放页地址。外层拿它跟当前集比对，
     *   防止切集瞬间把上一集的流接到新一集上。
     */
    onSubRequest: (pageKey: String, url: String, headers: Map<String, String>, cookie: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(true) }

    // 上一次**请求过的**地址。不能用 `WebView.getUrl()` 比：站点自己会 302 / 改 query /
    // 在站内跳转，`getUrl()` 与传进来的地址永远不等，每次重组都会再 `loadUrl` 一次，
    // 表现就是「播两秒黑一下、再从头播」。
    //
    // 用 `AtomicReference` 而不是 Compose 状态：`shouldInterceptRequest` 在**网络线程**读它，
    // 在网络线程读 Compose 的 snapshot 状态不安全。
    val pageKey = remember(url) { AtomicReference(url) }

    // 站点播放器的全屏 view 挂在这里；平时 GONE，避免挡住 WebView 的触摸。
    val fullscreenHost = remember(context) {
        FrameLayout(context).apply { visibility = View.GONE }
    }

    // 音频焦点跟着 WebView 一起进出：播视频要 GAIN，不能让别人压着我们 duck。
    val audio = remember(context) { WebAudioController(context) }
    DisposableEffect(audio) {
        audio.acquire()
        onDispose { audio.release() }
    }

    // 已交接标记。一次页面里只认第一条视频流：拿到就能交给原生播放器了，
    // 再拦下去只会把播放器的后续分片当成新地址上报。
    // 不用 `remember(url)`：WebViewClient 是工厂里建一次就固定的，换了 key 它看不见新实例，
    // 所以换集时在 `update` 里手动复位。
    val handedOver = remember { AtomicBoolean(false) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        // 图片**不能**禁。Dart-Mikomi 那套是 Headless 的嗅探器，页面永远不给人看，
                        // 所以它敢 blockNetworkImage 省流量；我们这里页面是兜底形态——
                        // 嗅探失败时用户就要在这个页面上用站点播放器，禁图会变成一个"坏网页"。
                        // 允许页面自己缩放：站点播放器的控制条按它的视口排，硬禁缩放会挤成一团。
                        setSupportZoom(true)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    userAgent?.takeIf { it.isNotBlank() }?.let { settings.userAgentString = it }
                    setBackgroundColor(AndroidColor.BLACK)
                    webViewClient = object : WebViewClient() {
                        /**
                         * **网络层嗅探**——整条路的关键。
                         *
                         * 站点把地址藏得多深都没关系：播放器最终一定发出 `.m3u8` 或带 `Range` 的
                         * 媒体请求，这些都会经过这里。跨域 iframe 也一样，因为请求走的是同一个
                         * WebView 网络栈（主文档的 JS 反而进不去 iframe）。
                         *
                         * 注意两件事：
                         * 1. 这个回调在**网络线程**，不能直接改 Compose 状态，必须 `post` 回主线程；
                         * 2. **永远 return null**——我们只是旁观，不拦截响应。返回空响应会让播放器
                         *    报错中断，反而拿不到后面的请求。
                         */
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            if (request.isForMainFrame) return null
                            if (handedOver.get()) return null
                            val target = request.url.toString()
                            val headers = request.requestHeaders.orEmpty()
                            // 预筛：页面零件（图片/脚本/样式）在这一步就丢掉，不往上传。
                            if (!VdoStreamSniffer.looksLikeVideo(target, headers)) return null
                            // Cookie 不在 requestHeaders 里：Chromium 是在更底层注入的，只能单独取。
                            val cookie = runCatching {
                                CookieManager.getInstance().getCookie(target)
                            }.getOrNull()
                            // 带上「这次请求属于哪个播放页」，外层靠它挡掉切集瞬间的错配。
                            val owner = pageKey.get()
                            view.post {
                                if (handedOver.compareAndSet(false, true)) {
                                    onSubRequest(owner, target, headers, cookie)
                                }
                            }
                            return null
                        }

                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            pageLoading = false
                            // 站点播放器自己的 volume 常常只有 0.2~0.5（桌面版尤其），
                            // 这里进页面把 `<video>` 顶到 100%，并在 play 时再顶一次。
                            view.evaluateJavascript(WebAudioController.VOLUME_BOOST_JS, null)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                            fullscreenHost.removeAllViews()
                            fullscreenHost.addView(
                                view,
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                ),
                            )
                            fullscreenHost.visibility = View.VISIBLE
                            onRequestFullscreen(true)
                        }

                        override fun onHideCustomView() {
                            fullscreenHost.removeAllViews()
                            fullscreenHost.visibility = View.GONE
                            onRequestFullscreen(false)
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            update = { view ->
                // 只在**请求地址**变了（换集）才重新加载；同一地址不打断正在播的页面。
                if (pageKey.get() != url) {
                    pageKey.set(url)
                    // 新的一集要有自己的嗅探机会，否则上一集交接后就再也不拦了。
                    handedOver.set(false)
                    pageLoading = true
                    view.loadUrl(url)
                }
            },
            onRelease = { view ->
                // 顺序要紧：先从 View 树里摘掉再销毁，否则部分 ROM 会在销毁已附着 WebView 时崩。
                // 两个 client 不置空（Kotlin 侧是非空类型），`destroy()` 已经把 WebView 内部状态清掉了。
                (view.parent as? ViewGroup)?.removeView(view)
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
                webView = null
            },
            modifier = Modifier.fillMaxSize(),
        )

        AndroidView(
            factory = { fullscreenHost },
            modifier = Modifier.fillMaxSize(),
        )

        if (pageLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        }
    }

    // 已消费的重载令牌。初值就是拿到的那个，所以首次进入不会多打一次 `reload()`——
    // 上面 factory 已经 loadUrl 过一次了，紧跟一次 reload 会把刚起头的播放打断。
    var consumedToken by remember { mutableStateOf(reloadToken) }

    // 重试：同一个地址要真的再拉一次，不能只靠 update（地址没变它不会动）。
    LaunchedEffect(reloadToken) {
        if (reloadToken == consumedToken) return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        consumedToken = reloadToken
        pageLoading = true
        view.reload()
    }
}
