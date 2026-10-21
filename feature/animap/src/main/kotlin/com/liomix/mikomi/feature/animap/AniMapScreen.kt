package com.liomix.mikomi.feature.animap

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import android.util.Log
import com.liomix.mikomi.core.navigation.ImmersionBridge

/** 巡礼地图（anitabi）页面地址。 */
const val ANIMAP_URL = "https://www.anitabi.cn/map?c=138.5098%2C37.2073&z=5.2"

/** 沉浸模式下底部导航栏临时唤出的自动收起延时（毫秒）：返回手势/手势条唤出后 2s 收起。 */
private const val NAV_BAR_HIDE_DELAY_MILLIS = 2_000L

/** 进入巡礼页后自动隐藏底部导航栏的延时（毫秒）：不靠滑动判定，进页面 2s 后自动进入沉浸。 */
private const val NAV_BAR_AUTO_HIDE_DELAY_MILLIS = 2_000L

/**
 * 巡礼地图（AniMap）。以纯 WebView 形式内嵌 anitabi 地图，展示动漫取景地。
 *
 * WebView 装在 [SurfaceWebViewContainer]（TextureView + VirtualDisplay）中，
 * 与宿主 View 树隔离，宿主厂商系统模块的反射遍历碰不到 WebView 本体。
 *
 * 沉浸式交互设计：
 * - 状态栏常驻，页面内容避开状态栏区域（statusBars padding）；
 * - 进入巡礼页 [NAV_BAR_AUTO_HIDE_DELAY_MILLIS] 后自动进入沉浸（隐藏应用底部导航栏），
 *   不靠滑动/缩放判定，直接写 [ImmersionBridge.immersive]；
 * - 沉浸中底部显示常驻手势条：点按或拖动可临时唤出底部导航栏（2s 自动收起），
 *   直接写 [ImmersionBridge.navBarTemp]；返回手势同样唤出（由宿主 NavDisplay.onBack 处理）。
 *
 * 沉浸状态权威副本直接放在 [ImmersionBridge]（进程级单例 MutableState），
 * 跨 entry 重建、跨 Activity 重建存活，且不依赖任何绑定时序。
 *
 * @param modifier 布局修饰符
 * @param url 地图页面地址
 * @param immersionBridge 沉浸状态桥（进程级单例），页面读写状态与宿主共用同一实例
 */
@Composable
fun AniMapScreen(
    modifier: Modifier = Modifier,
    url: String = ANIMAP_URL,
    immersionBridge: ImmersionBridge,
) {
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    // 地图页面加载指示：true 时显示环形进度。
    var loading by remember { mutableStateOf(true) }
    // 沉浸状态权威副本：进程级单例 MutableState，跨 entry 重建存活。
    val immersive by immersionBridge.immersive
    val showNavBar by immersionBridge.navBarTemp

    // 手势条点按唤出后 2s 自动收起；收起后若仍在沉浸页面则重新进入沉浸。
    // 用 LaunchedEffect(showNavBar) 键：navBarTemp 从 false→true 启动定时器，
    // 收起后写 false 会再次触发（if 不满足直接返回），不影响状态。
    LaunchedEffect(showNavBar) {
        Log.d("AniMapScreen", "LaunchedEffect(showNavBar=$showNavBar) entered")
        if (showNavBar) {
            kotlinx.coroutines.delay(NAV_BAR_HIDE_DELAY_MILLIS)
            Log.d("AniMapScreen", "LaunchedEffect(showNavBar) 2s elapsed, collapsing navBarTemp")
            immersionBridge.onNavBarTempChange(false)
            // 收起后重新进入沉浸：immersive 仍为 true（返回唤出不写 false），
            // 这里显式写 true 确保手势条重新出现、导航栏隐藏。
            if (immersionBridge.immersive.value) {
                Log.d("AniMapScreen", "LaunchedEffect(showNavBar) re-entering immersive")
                immersionBridge.onImmersiveChange(true)
            }
        }
    }

    // 自动进入沉浸（隐藏底部导航栏）计时器已迁移到宿主 MikomiNavHost（进程级 scope）：
    // 切回本页时 AndroidView factory 重建 SurfaceWebViewContainer → 挂新 Presentation 窗口
    // → ViewRootImpl relayout / ImeFocusController 焦点抢占会让 entry effect scope 被销毁
    // 重建，delay 协程被取消后键（immersive）未变不再重启，导致 2s 计时器永不完成
    // （真机日志已证实：LaunchedEffect(immersive=false) entered 后 2s 无任何后续日志）。
    // 页面 entry 不再承担计时，避免被 WebView 窗口挂载打断。
    Box(modifier = modifier.fillMaxSize()) {
        // 加载指示须放在 AndroidView 之后声明：Box 子节点按声明顺序绘制，
        // TextureView 不支持 zIndex，后声明的 Compose 内容才会盖到 WebView 之上。
        AndroidView(
            // 地图容器避开状态栏区域：地图位于状态栏之下（内容不被状态栏遮挡）。
            // 仅状态栏避让，底部仍全屏（手势条/导航栏覆盖在地图之上，符合沉浸设计）。
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            factory = { context ->
                // 复用缓存的 WebView：切换回巡礼页不再重新加载地图。
                AniMapWebCache.acquire(context, url).also { container ->
                    container.onLoadingChanged = { isLoading -> loading = isLoading }
                    container.onWebViewCreated = { view -> webView = view }
                    container.onPageFinished = { view, finishedUrl ->
                        canGoBack = view.canGoBack()
                        // 用页面最终 URL 记录缓存；anitabi 会重定向改写 URL，
                        // 与初始 url 不等会导致缓存永不命中。
                        AniMapWebCache.onLoaded(finishedUrl ?: view.url ?: url)
                    }
                    container.onEdgeDrag = {
                        Log.d("AniMapScreen", "onEdgeDrag: exit immersive + show navBarTemp")
                        // 底部边缘拖动：退出沉浸 + 临时唤出导航栏（地图滑动不再改变沉浸状态）。
                        immersionBridge.onImmersiveChange(false)
                        immersionBridge.onNavBarTempChange(true)
                    }
                    // 缓存命中时容器已持有加载完成的 WebView，不再显示加载指示。
                    loading = container.webView == null
                }
            },
            onRelease = {
                // 仅释放容器外壳（虚拟显示/挂载），WebView 本体由缓存持有，不销毁。
                AniMapWebCache.release(it)
            },
        )

        if (loading) {
            // 地图加载指示：Material 风格环形进度 + 说明文字，居中显示。
            val loadingText = stringResource(R.string.animap_loading)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .semantics(mergeDescendants = true) { contentDescription = loadingText },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    Text(
                        text = loadingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 沉浸中的常驻手势条：Material 地图模式式的底部 pill，
        // 点按切换导航栏显隐，拖动同样唤出，是"如何再显示导航栏"的可发现入口。
        // 条件读桥内权威副本（immersive && !showNavBar），跨 entry 重建存活。
        if (immersive && !showNavBar) {
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
                        detectTapGestures(
                            onTap = { immersionBridge.onNavBarTempChange(true) },
                        )
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.45f)),
            )
        }
    }
}
