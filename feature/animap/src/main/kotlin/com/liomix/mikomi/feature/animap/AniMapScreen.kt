package com.liomix.mikomi.feature.animap

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** 巡礼地图（anitabi）页面地址。 */
const val ANIMAP_URL = "https://www.anitabi.cn/map?c=140.3673,36.1288&z=5"

/** 沉浸模式下底部导航栏临时唤出的自动收起延时（毫秒）。 */
private const val NAV_BAR_HIDE_DELAY_MILLIS = 2_500L

/**
 * 巡礼地图（AniMap）。以纯 WebView 形式内嵌 anitabi 地图，展示动漫取景地。
 *
 * WebView 装在 [SurfaceWebViewContainer]（TextureView + VirtualDisplay）中，
 * 与宿主 View 树隔离，宿主厂商系统模块的反射遍历碰不到 WebView 本体。
 *
 * 沉浸式交互设计：
 * - 状态栏常驻，页面内容避开状态栏区域（statusBars padding）；
 * - 检测到地图被滑动（单指拖动超过 touch slop）才进入沉浸，
 *   仅隐藏应用底部导航栏，由 [onImmersiveChange] 通知宿主；
 * - 沉浸中底部显示常驻手势条：点按或拖动可临时唤出底部导航栏（2.5s 自动收起），
 *   由 [onNavBarTempChange] 通知宿主控制导航栏显隐。
 *
 * @param modifier 布局修饰符
 * @param url 地图页面地址
 * @param onImmersiveChange 沉浸模式变化回调，true 表示页面希望隐藏底部导航栏
 * @param onNavBarTempChange 沉浸中临时唤出/收起底部导航栏回调
 */
@Composable
fun AniMapScreen(
    modifier: Modifier = Modifier,
    url: String = ANIMAP_URL,
    onImmersiveChange: (Boolean) -> Unit = {},
    onNavBarTempChange: (Boolean) -> Unit = {},
) {
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    // 地图被滑动后进入沉浸：隐藏底部导航栏；状态栏常驻不动。
    var immersiveActive by remember { mutableStateOf(false) }
    // 沉浸中临时唤出的底部导航栏，超时自动收起。
    var showNavBar by remember { mutableStateOf(false) }

    LaunchedEffect(immersiveActive) { onImmersiveChange(immersiveActive) }
    LaunchedEffect(showNavBar) {
        onNavBarTempChange(showNavBar)
        if (showNavBar) {
            kotlinx.coroutines.delay(NAV_BAR_HIDE_DELAY_MILLIS)
            showNavBar = false
        }
    }

    AniMapBackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            factory = { context ->
                SurfaceWebViewContainer(context, url).apply {
                    onPageFinished = { view -> canGoBack = view.canGoBack() }
                    onWebViewCreated = { webView = it }
                    onMapScrolled = { immersiveActive = true }
                    onEdgeDrag = {
                        immersiveActive = false
                        showNavBar = true
                    }
                }
            },
            onRelease = {
                it.destroy()
                immersiveActive = false
                showNavBar = false
            },
        )

        // 沉浸中的常驻手势条：Material 地图模式式的底部 pill，
        // 点按切换导航栏显隐，拖动同样唤出，是"如何再显示导航栏"的可发现入口。
        if (immersiveActive && !showNavBar) {
            val handleDescription = stringResource(R.string.animap_show_nav_handle)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp)
                    .width(96.dp)
                    .height(20.dp)
                    .semantics { contentDescription = handleDescription }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showNavBar = true })
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.45f)),
            )
        }
    }
}
