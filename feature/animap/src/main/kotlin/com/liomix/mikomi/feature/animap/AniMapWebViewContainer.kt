package com.liomix.mikomi.feature.animap

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * 注入页面的样式：把 anitabi 右下角那块「更新日志」悬浮块（`.func-change-logs-fixed`）藏掉。
 *
 * 它固定在右下角、层级最高，正好压在地图的操作区上，点它还会跳去更新日志页；
 * 对一个只想看地图的场景，这就是一块纯粹的遮挡物。
 *
 * 用 `<style>` 而不是「找到节点再删」：站点是 SPA，那块 DOM 常常在首屏之后才由 JS 插进来，
 * 按节点删会漏；CSS 规则一旦在 `<head>` 里，后插进来的节点同样被命中。
 * 带 id 守卫，重复注入只会有一次。
 */
private const val HIDE_CHANGELOG_JS = """
(function() {
  if (document.getElementById('mikomi-hide-changelog')) return;
  var style = document.createElement('style');
  style.id = 'mikomi-hide-changelog';
  style.textContent = '.func-change-logs-fixed{display:none!important}';
  (document.head || document.documentElement).appendChild(style);
})();
"""

/**
 * 巡礼地图的 WebView 容器。
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
) : FrameLayout(context) {

    /** 容器内的 WebView，创建后可调用 goBack 等。 */
    var webView: WebView? = null
        private set

    /** 页面开始加载/加载完成时回调 true/false。 */
    var onLoadingChanged: (Boolean) -> Unit = {}

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
            // 缓存页面的样式早就注入过；SPA 偶尔会重建 head，补一次不吃亏。
            web.evaluateJavascript(HIDE_CHANGELOG_JS, null)
        }
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
     * 仅把 WebView 从容器上摘下，保留本体由 [AniMapWebCache] 持有，供下次进入复用。
     * 调用后容器不可再用。
     */
    fun detachForCache() {
        webView?.let { (it.parent as? ViewGroup)?.removeView(it) }
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
            override fun onPageFinished(view: WebView, finishedUrl: String?) {
                view.evaluateJavascript(HIDE_CHANGELOG_JS, null)
                onLoadingChanged(false)
                // 显式限定到类属性，避免与本匿名类重写的 onPageFinished 方法同名遮蔽，
                // 否则 Kotlin 会解析成递归调用自身导致 StackOverflow。
                this@AniMapWebViewContainer.onPageFinished(view, finishedUrl)
            }
        }
    }

    private fun Context.isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
