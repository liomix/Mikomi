package com.liomix.mikomi.feature.animap

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Gravity
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * 类似 flutter_inappwebview（useHybridComposition=false）的纯原生 WebView 容器。
 *
 * WebView 不直接放入窗口的 View 树，而是放进一个由 [TextureView] 提供的
 * [VirtualDisplay]（虚拟显示），渲染输出到 Surface，触摸坐标缩放后转发。
 * WebView 因此处于独立的窗口层级，宿主 View 树上的反射遍历/劫持
 * （如 vivo「无障碍增强」模块）碰不到 WebView 本体，Canvas/WebGL 正常渲染。
 *
 * 对齐 Flutter 插件默认：loadWithOverviewMode=true、useWideViewPort=true、
 * textZoom=100、databaseEnabled=false。
 */
class SurfaceWebViewContainer(
    context: Context,
    private val initialUrl: String,
    private val cachedWebView: WebView? = null,
) : FrameLayout(context), TextureView.SurfaceTextureListener {

    /** 容器内的 WebView，创建后可调用 goBack 等。 */
    var webView: WebView? = null
        private set

    /** 页面开始加载/加载完成时回调 true/false。 */
    var onLoadingChanged: (Boolean) -> Unit = {}

    /** 页面加载完成（onPageFinished）时回调，携带页面当前 URL（可能被站点重定向改写）。 */
    var onPageFinished: (WebView, String?) -> Unit = { _, _ -> }

    /** WebView 创建完成时回调。 */
    var onWebViewCreated: (WebView) -> Unit = {}

    /** 底部边缘向上拖动时回调，用于临时唤出底部导航栏（进入沉浸由定时器触发，不靠手势）。 */
    var onEdgeDrag: () -> Unit = {}

    private val textureView = TextureView(context)

    private var virtualDisplay: VirtualDisplay? = null
    private var hostContainer: FrameLayout? = null
    private var presentation: android.app.Presentation? = null

    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var scrollDetected = false

    /** 真实用户手指是否正按在容器上（ACTION_DOWN 置 true，ACTION_UP/CANCEL 置 false）。 */
    private var userPointerDown = false

    init {
        addView(
            textureView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        textureView.surfaceTextureListener = this
        // WebView 挂在 Presentation 里，没有自己的输入焦点，触摸事件须手动转发。
        // 否则点按/滑动无响应，地图收不到任何手势。
        setOnTouchListener { _, event ->
            val web = webView ?: return@setOnTouchListener false
            web.onTouchEvent(event)
            handleGesture(event)
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                web.requestFocus()
            }
            true
        }
    }

    /**
     * 从转发的触摸流中识别唤出手势（不影响 WebView 收到的事件）：
     * 起点在屏幕底部约 20% 高度内的明显向上拖动：[onEdgeDrag]（临时唤出底部导航栏）。
     * 进入沉浸由 [AniMapScreen] 的定时器触发（进页面 1s 后自动隐藏导航栏），
     * 不再靠滑动/缩放判定，故地图滑动不触发任何沉浸状态变化。
     *
     * 仅在真实用户手指按在容器上（[userPointerDown]）时才判定手势：
     * WebView 在 VirtualDisplay/Presentation 独立窗口中自身渲染、惯性滚动、
     * 手指落点抖动都会产生 MotionEvent，无条件转发判定会误触发唤出。
     */
    private fun handleGesture(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                userPointerDown = true
                downX = event.x
                downY = event.y
                scrollDetected = false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                userPointerDown = false
                scrollDetected = false
            }

            MotionEvent.ACTION_MOVE -> {
                // 仅真实用户手指按住时才判定手势，屏蔽 WebView 自产事件/惯性滚动。
                if (!userPointerDown) return
                if (scrollDetected || event.pointerCount > 1) return
                val dy = event.y - downY
                val slop = touchSlop.toFloat()
                // 起点在底部区域 + 明显向上拖动才唤出导航栏；其余滑动不触发任何手势。
                val isEdgeDrag = downY > height * EDGE_DRAG_FRACTION &&
                    dy < -EDGE_DRAG_MIN_UPWARD_FRACTION * touchSlop
                if (isEdgeDrag) {
                    scrollDetected = true
                    onEdgeDrag()
                }
            }
        }
    }

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        // SurfaceTexture 通常为设备像素，TextureView 显示区域可能更大，
        // 用单位矩阵不做缩放，虚拟显示分辨率跟物理像素一致。
        textureView.setTransform(Matrix())
        createWebViewIfReady()
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        ensureVirtualDisplay(surfaceTexture, width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        releaseVirtualDisplay()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            createWebViewIfReady()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // TextureView 不挖洞，但 Compose 层需要透明时先清屏。
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        super.dispatchDraw(canvas)
    }

    /** 停止加载并释放 WebView 与虚拟显示。 */
    fun destroy() {
        destroyWebView()
        presentation?.dismiss()
        presentation = null
        hostContainer?.let { detachHost(it) }
        hostContainer = null
        releaseVirtualDisplay()
    }

    /**
     * 仅释放容器外壳（虚拟显示、窗口挂载），保留 WebView 本体由 [AniMapWebCache] 持有，
     * 供下次进入复用。调用后容器不可再用。
     */
    fun detachForCache() {
        webView?.let { hostContainer?.removeView(it) }
        webView = null
        presentation?.dismiss()
        presentation = null
        hostContainer?.let { detachHost(it) }
        hostContainer = null
        releaseVirtualDisplay()
    }

    /** 销毁 WebView 本体并释放虚拟显示（彻底清理，不再复用）。 */
    private fun destroyWebView() {
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebViewIfReady() {
        if (hostContainer != null) return
        val surfaceTexture = textureView.surfaceTexture ?: return
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        // 每次容器重建都先恢复 VirtualDisplay。缓存命中路径若跳过这一步，会退化为
        // TYPE_APPLICATION_PANEL 窗口；该窗口位于 Compose 浮层之上，进而遮住底部导航栏和手势条。
        ensureVirtualDisplay(surfaceTexture, w, h)

        val cached = cachedWebView
        if (cached != null) {
            // 缓存命中：跳过重建与 loadUrl，直接挂载到虚拟显示上。
            // onSurfaceTextureAvailable 与 onSizeChanged 都会触发本方法，
            // 且缓存 WebView 可能仍挂在旧的 host 上（detachForCache 未能摘除的兜底），
            // 挂载前必须先从旧 parent 摘下，否则 addView 抛
            // IllegalStateException "The specified child already has a parent."。
            (cached.parent as? android.view.ViewGroup)?.removeView(cached)
            hostContainer = FrameLayout(context).also { host ->
                host.addView(cached, FrameLayout.LayoutParams(width, height))
                attachHost(host, width, height)
            }
            webView = cached
            cached.isFocusable = true
            cached.isFocusableInTouchMode = true
            cached.requestFocus()
            onWebViewCreated(cached)
            // 缓存 WebView 已加载完成，不再触发加载指示。
            return
        }
        if (webView != null) return

        val host = FrameLayout(context)
        val web = WebView(context).apply {
            // 保持 WebView 默认渲染模式：
            // 不要用 LAYER_TYPE_SOFTWARE（强制软件渲染会废掉 WebGL）或 LAYER_TYPE_HARDWARE。
            setLayerType(View.LAYER_TYPE_NONE, null)
            // 不暴露无障碍节点，宿主 View 树上的反射遍历无处可劫持。
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            WebView.setWebContentsDebuggingEnabled(context.isDebuggable())
            settings.apply {
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = true
                // 对齐 flutter_inappwebview 插件默认（InAppWebViewSettings）。
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
            // 禁用长按弹出文本选择菜单。
            setOnLongClickListener { true }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, finishedUrl: String?) {
                    onLoadingChanged(false)
                    // 显式限定到类属性，避免与本匿名类重写的 onPageFinished 方法同名遮蔽，
                    // 否则 Kotlin 会解析成递归调用自身导致 StackOverflow。
                    this@SurfaceWebViewContainer.onPageFinished(view, finishedUrl)
                }
            }
        }
        host.addView(web, FrameLayout.LayoutParams(w, h))
        hostContainer = host
        attachHost(host, w, h)
        webView = web
        // Presentation 窗口创建在自己的显示上，不随 Activity 焦点走；
        // 显式请求焦点，保证键盘/输入事件能进 WebView。
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.requestFocus()
        onLoadingChanged(true)
        onWebViewCreated(web)
        web.loadUrl(initialUrl)
    }

    private fun attachHost(host: FrameLayout, w: Int, h: Int) {
        val target = virtualDisplay?.display
        if (target != null) {
            runCatching {
                val shown = object : android.app.Presentation(
                    context,
                    target,
                    android.R.style.Theme_DeviceDefault_NoActionBar,
                ) {
                    override fun onCreate(savedInstanceState: android.os.Bundle?) {
                        super.onCreate(savedInstanceState)
                        window?.setGravity(Gravity.TOP or Gravity.START)
                        setContentView(host)
                    }
                }
                shown.show()
                presentation = shown
                hostContainer = host
            }.onFailure {
                // 虚拟显示不可用时退回窗口挂载（仍低于 TextureView，触摸转发仍工作）。
                attachHostWindow(host, w, h)
            }
        } else {
            attachHostWindow(host, w, h)
        }
    }

    private fun attachHostWindow(host: FrameLayout, w: Int, h: Int) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        ).apply {
            width = w
            height = h
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        runCatching { wm.addView(host, params) }
            .onFailure {
                // 窗口挂载失败时至少保留 WebView 对象，便于日志排查。
                hostContainer = null
            }
    }

    private fun detachHost(host: FrameLayout) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { wm.removeViewImmediate(host) }
    }

    private fun ensureVirtualDisplay(st: SurfaceTexture, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (virtualDisplay != null) return
        val density = context.resources.displayMetrics.densityDpi
        // OWN_CONTENT_ONLY：只渲染 WebView，跳过虚拟显示上无关内容；
        // AUTO_MIRROR 需要源显示镜像，这里不需要，避免额外一帧拷贝。
        virtualDisplay = displayManager.createVirtualDisplay(
            "mikomi-animap-webview",
            w,
            h,
            density,
            android.view.Surface(st),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun Context.isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private companion object {
        /** 底部边缘区域高度比例，仅此区域内的**向上拖动**视为唤出导航栏。 */
        const val EDGE_DRAG_FRACTION = 0.20f

        /** 唤出手势所需最小向上位移（以 touch slop 为单位），低于此视为地图平移。 */
        const val EDGE_DRAG_MIN_UPWARD_FRACTION = 2.5f
    }
}
