package com.liomix.mikomi.feature.player.component

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
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

/**
 * WebView 形态的画面：把**站点自己的播放页**装进来，让它的播放器自己跑。
 *
 * 为什么必须有这条路：KazumiRules 目录里的规则全部是 `useNativePlayer` + `useWebview`，
 * 播放页里那句 `<script>var player_aaaa = {"encrypt":2,"url":"..."}</script>` 中的地址是
 * **加密后由页面自己的 JS 解开并再跳一层**的（实测 7sefun：base64 → urlencode → lmm85 播放页 → 真正的 m3u8）。
 * 静态解析最多只能扒到那串密文，想真的出画面只能把页面交给系统浏览器内核。
 *
 * 几个必须开的设置（少一个就白屏/不自动播/点不动）：
 * - `javaScriptEnabled`：地址和播放器都是 JS 起的；
 * - `domStorageEnabled`：不少播放器把配置写 localStorage，关掉就初始化失败；
 * - `mediaPlaybackRequiresUserGesture = false`：否则自动播放被拦，表现为「黑屏但有控制条」；
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(true) }

    // 上一次**请求过的**地址。不能用 `WebView.getUrl()` 比：站点自己会 302 / 改 query /
    // 在站内跳转，`getUrl()` 与传进来的地址永远不等，每次重组都会再 `loadUrl` 一次，
    // 表现就是「播两秒黑一下、再从头播」。
    var requestedUrl by remember { mutableStateOf(url) }

    // 站点播放器的全屏 view 挂在这里；平时 GONE，避免挡住 WebView 的触摸。
    val fullscreenHost = remember(context) {
        FrameLayout(context).apply { visibility = View.GONE }
    }

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
                        // 允许页面自己缩放：站点播放器的控制条按它的视口排，硬禁缩放会挤成一团。
                        setSupportZoom(true)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    userAgent?.takeIf { it.isNotBlank() }?.let { settings.userAgentString = it }
                    setBackgroundColor(AndroidColor.BLACK)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            pageLoading = false
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
                if (requestedUrl != url) {
                    requestedUrl = url
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
