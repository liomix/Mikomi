package com.liomix.mikomi.feature.animap

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/** 巡礼地图（anitabi）页面地址。 */
const val ANIMAP_URL = "https://www.anitabi.cn/map?c=140.3673,36.1288&z=5"

/** 创建 WebView 的延迟，等首帧布局稳定后再加载 WebGL 地图。 */
private const val WEBVIEW_CREATE_DELAY_MS = 300L

/**
 * 巡礼地图（AniMap）。以纯 WebView 形式内嵌 anitabi 地图，展示动漫取景地。
 *
 * WebView 装在 [SurfaceWebViewContainer]（SurfaceView + VirtualDisplay）中，
 * 与宿主 View 树隔离，宿主厂商系统模块的反射遍历碰不到 WebView 本体。
 *
 * @param modifier 布局修饰符
 * @param url 地图页面地址
 */
@Composable
fun AniMapScreen(
    modifier: Modifier = Modifier,
    url: String = ANIMAP_URL,
) {
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    // 延迟到首帧布局稳定后再创建 WebView，
    // 否则 WebGL 地图在画布尺寸/像素比未就绪时初始化，会导致瓦片纹理渲染空白。
    var webViewReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(WEBVIEW_CREATE_DELAY_MS)
        webViewReady = true
    }

    AniMapBackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (webViewReady) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    SurfaceWebViewContainer(context, url).apply {
                        onLoadingChanged = { isLoading = it }
                        onPageFinished = { view -> canGoBack = view.canGoBack() }
                        onWebViewCreated = { webView = it }
                    }
                },
                onRelease = { it.destroy() },
            )
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
