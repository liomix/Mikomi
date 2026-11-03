package com.liomix.mikomi.feature.animap

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * 注入页面的样式：藏掉地图上我们不需要的一整圈浮层与标记。
 *
 * 分三类，缺一类就会留下「看着像坏了」的残影：
 * 1. **控件本体**：随机跳转作品、定位到我、配色切换（`-schemes`）、缩放/指南针、比例尺。
 * 2. **控件的壳**：`.mapboxgl-ctrl-group` 与四个角容器自带白色圆角底板和内边距。
 *    只藏按钮时壳还在，屏幕上就是一排空白小方块——这就是「隐藏后出现几个点」的来源。
 *    这里直接藏 `.mapboxgl-ctrl-group`（mapbox 所有成组控件的统一外壳），
 *    但不碰四角容器本身、也不碰 `.mapboxgl-ctrl-attrib`：右下角的 mapbox 版权标识
 *    是协议要求必须保留的，它挂在角容器里、不属于任何 group。
 * 3. **地图自己画的位置标记**：`.mapboxgl-user-location*` 是定位蓝点/精度圈。
 *    它不是控件，是 mapbox 在定位激活后画在地图画布上方的一层 DOM，得单独点名。
 *
 * 站点那句「加载中...」**不是 DOM 节点，是 `html:after` 伪元素**（样式表里
 * `html:after{content:"加载中..."}`，`html[no-touch]` 时 `opacity:1` 显形），所以
 * 手动在开发者工具里翻 DOM、或者按文本扫描，都永远抓不到它——只能按伪元素规则点名，
 * 见下面样式里的 `html:after` 那一条。剩下的文本扫描逻辑留着当兜底：有些 loading 层
 * 确实是真实节点，命中「加载中 / 正在加载 / 载入中 / loading」这类短提示后，
 * 会顺着父级爬到只装着这句提示的容器整块藏掉；容器换成正文内容时自动解除隐藏。
 *
 * 用 `<style>` 而不是「找到节点再删」：站点是 SPA，控件由 mapbox 在首屏之后异步插进来，
 * 按节点删会漏；而且站点 JS 可能持有这些按钮的引用去挂监听，删节点反而让它抛异常。
 * CSS 规则一旦在 `<head>` 里，后插进来的节点同样被命中。每次注入先摘旧再挂新，
 * 规则升级后缓存下来的 WebView 也能跟着更新。
 */
private const val HIDE_OVERLAY_JS = """
(function() {
  // 抢注时文档可能还没有 <head>（onPageStarted 后的一小段时间里），直接退出，
  // 由下一轮延迟注入接手；在 about:blank 上插进去的样式会随导航一起被丢弃，无副作用。
  var root = document.head || document.documentElement;
  if (!root) return;

  // 每次都先摘掉旧版本注入的 style：规则会随版本演进（新版本要藏更多东西），
  // 靠「有 id 就跳过」会让缓存下来的 WebView 永远停在旧规则上。
  ['mikomi-hide-changelog', 'mikomi-hide-overlay'].forEach(function(id) {
    var node = document.getElementById(id);
    if (node) node.parentNode.removeChild(node);
  });
  var style = document.createElement('style');
  style.id = 'mikomi-hide-overlay';
  style.textContent = [
    '.func-change-logs-fixed',
    '.mapboxgl-ctrl-group',
    '.mapboxgl-ctrl-random-to-bangumi',
    '.mapboxgl-ctrl-geolocate',
    '.mapboxgl-ctrl-schemes',
    '.mapboxgl-ctrl-zoom-in',
    '.mapboxgl-ctrl-zoom-out',
    '.mapboxgl-ctrl-compass',
    // 左下角的比例尺（mapbox 原生控件，独立一个 div，不在 group 里）。
    '.mapboxgl-ctrl-scale',
    // mapbox 自己贴的一个空链接触摸区（点中会跳 service.html），看不见但能误触。
    '.mbgl-service-link',
    '.mapboxgl-user-location',
    '.mapboxgl-user-location-dot',
    '.mapboxgl-user-location-accuracy-circle'
  ].join(',') + '{display:none!important}' +
    // 兜底：万一还有没点名的控件壳（站点自定义容器），把底板/阴影/边框一起抹掉，
    // 空壳就只剩透明像素，不会在画面上留灰色小方块。attribution 保留原样。
    '.mapboxgl-ctrl:not(.mapboxgl-ctrl-attrib){' +
      'background:none!important;box-shadow:none!important;border:0!important}' +
    // 供脚本动态打标：必须是样式表里的 !important，站点之后再改 inline style 也顶不掉。
    '.mikomi-hidden{display:none!important}' +
    // 站点右上角那个「加载中...」是 **html 伪元素**（样式表里 html:after{content:"加载中..."}，
    // html[no-touch] 时 opacity:1 显示），DOM 里根本没有对应节点 —— 所以文本扫描永远抓不到，
    // 只能按伪元素规则点名。content:none 直接不生成盒子，比 display:none 更干净。
    'html:after,html[no-touch]:after{content:none!important;display:none!important}';
  root.appendChild(style);

  // 「加载中…」按文本认人。标记放 window 上，onPageFinished 会重复注入，只装一套观察者。
  if (window.mikomiLoadingSweep) return;
  window.mikomiLoadingSweep = true;

  var HIDDEN = 'mikomi-hidden';
  // 判定为「提示语」而不是「正文」的长度上限：去掉空白后最多这么多个字。
  var MAX_TIP_LENGTH = 16;
  var hiddenCount = 0;
  var lastRefresh = 0;

  function norm(text) {
    return (text || '').replace(/\s+/g, '');
  }

  function isTipText(text) {
    var t = norm(text).toLowerCase();
    if (!t || t.length > MAX_TIP_LENGTH) return false;
    return t.indexOf('加载中') >= 0 ||
      t.indexOf('正在加载') >= 0 ||
      t.indexOf('载入中') >= 0 ||
      t.indexOf('loading') >= 0;
  }

  // 从命中文字的那一行往上爬：只要父级的文本同样是一句短提示，就继续把父级也算进来。
  // 这样弹窗的壳（底板、遮罩、转圈图标）会一起被藏；遇到体量正常的内容容器就停手，
  // 不会一路爬到地图容器或 body 上。
  function hideShell(el) {
    var target = el;
    for (var up = 0; up < 4; up++) {
      var parent = target.parentElement;
      if (!parent || parent === document.body || parent === document.documentElement) break;
      var text = norm(parent.textContent);
      if (!text || text.length > MAX_TIP_LENGTH) break;
      target = parent;
    }
    if (!target.classList.contains(HIDDEN)) {
      target.classList.add(HIDDEN);
      hiddenCount++;
    }
  }

  function sweepLoading(root) {
    if (!root || !root.querySelectorAll) return;
    var list = root.querySelectorAll('div,span,p,section,strong,em');
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      // 只处理「光秃秃一段字」的元素：里面还套着东西的是布局容器，藏了会连地图一起没。
      if (el.childElementCount > 1) continue;
      if (isTipText(el.textContent)) hideShell(el);
    }
  }

  // 同一个容器经常被复用：loading 结束后把子节点换成一堆真实内容，
  // 此时文本不再是提示语，必须把 mikomi-hidden 摘掉，否则内容永远不显示。
  function refreshHidden() {
    var list = document.querySelectorAll('.' + HIDDEN);
    hiddenCount = list.length;
    for (var i = 0; i < list.length; i++) {
      if (!isTipText(list[i].textContent)) list[i].classList.remove(HIDDEN);
    }
  }

  sweepLoading(document);

  new MutationObserver(function(mutations) {
    for (var i = 0; i < mutations.length; i++) {
      var added = mutations[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        var node = added[j];
        if (node.nodeType !== 1) continue;
        // 先比文本再遍历：地图上的 marker 增删很频繁，绝大多数节点不含这些字，
        // 直接跳过，避免每帧都在 DOM 上跑一遍选择器。
        if (norm(node.textContent).toLowerCase().indexOf('loading') < 0 &&
            norm(node.textContent).indexOf('载') < 0) continue;
        if (node.childElementCount === 0) {
          if (isTipText(node.textContent)) hideShell(node);
        } else {
          sweepLoading(node);
        }
      }
    }
    // 有隐藏中的壳时才回头复查，且 500ms 节流：地图平移时 DOM 变动非常密集。
    if (hiddenCount > 0 && Date.now() - lastRefresh > 500) {
      lastRefresh = Date.now();
      refreshHidden();
    }
  }).observe(document.documentElement, {
    childList: true,
    subtree: true,
    characterData: true
  });
})();
"""

/**
 * 导航开始后抢注样式的几个时间点（毫秒）。
 *
 * 0 是先排进消息队列、等这一帧的导航真正起来；后面几档覆盖「文档解析出 head」的时机。
 * 站点那块 loading 浮层从出现到被移除可能只有几百毫秒，单靠 onPageFinished 一定错过。
 */
private val EARLY_INJECT_DELAYS_MILLIS = longArrayOf(0L, 120L, 320L, 700L)

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
            injectOverlayStyle(web)
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
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 站点那块「加载中」浮层在首屏数据到达后就被从 DOM 上摘掉了：
                // 等 onPageFinished 时它早就不在，连手动抓 HTML 都抓不到。
                // 所以从导航一开始就分几轮抢注——文档一旦有了 head 就把规则落下去，
                // 后续插进来的节点交给脚本里的 MutationObserver。
                for (delay in EARLY_INJECT_DELAYS_MILLIS) {
                    view.postDelayed({ injectOverlayStyle(view) }, delay)
                }
            }

            override fun onPageFinished(view: WebView, finishedUrl: String?) {
                injectOverlayStyle(view)
                onLoadingChanged(false)
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

    private fun Context.isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
