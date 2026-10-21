package com.liomix.mikomi.feature.animap

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * 类似 flutter_inappwebview（useHybridComposition=false）的纯原生 WebView 容器。
 *
 * WebView 不直接放入窗口的 View 树，而是放进一个由 [SurfaceView] 提供的
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
) : FrameLayout(context), SurfaceHolder.Callback {

    /** 容器内的 WebView，创建后可调用 goBack 等。 */
    var webView: WebView? = null
        private set

    /** 页面开始加载/加载完成时回调 true/false。 */
    var onLoadingChanged: (Boolean) -> Unit = {}

    /** 页面加载完成（onPageFinished）时回调。 */
    var onPageFinished: (WebView) -> Unit = {}

    /** WebView 创建完成时回调。 */
    var onWebViewCreated: (WebView) -> Unit = {}

    private val surfaceView = SurfaceView(context)

    private var virtualDisplay: VirtualDisplay? = null
    private var hostContainer: FrameLayout? = null
    private var presentation: android.app.Presentation? = null

    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    init {
        addView(
            surfaceView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        surfaceView.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        createWebViewIfReady()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        ensureVirtualDisplay(holder, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseVirtualDisplay()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            createWebViewIfReady()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // SurfaceView 默认在其后挖洞，Compose 层需要透明时先清屏。
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        super.dispatchDraw(canvas)
    }

    /** 停止加载并释放 WebView 与虚拟显示。 */
    fun destroy() {
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
        presentation?.dismiss()
        presentation = null
        hostContainer?.let { detachHost(it) }
        hostContainer = null
        releaseVirtualDisplay()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebViewIfReady() {
        if (webView != null) return
        if (!surfaceView.holder.surface.isValid) return
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        ensureVirtualDisplay(surfaceView.holder, w, h)

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
                    onPageFinished(view)
                }
            }
        }
        host.addView(web, FrameLayout.LayoutParams(w, h))
        hostContainer = host
        attachHost(host, w, h)
        webView = web
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
                // 虚拟显示不可用时退回窗口挂载（仍低于 SurfaceView，触摸转发仍工作）。
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

    private fun ensureVirtualDisplay(holder: SurfaceHolder, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (virtualDisplay != null) return
        val density = context.resources.displayMetrics.densityDpi
        virtualDisplay = displayManager.createVirtualDisplay(
            "mikomi-animap-webview",
            w,
            h,
            density,
            holder.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun Context.isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
