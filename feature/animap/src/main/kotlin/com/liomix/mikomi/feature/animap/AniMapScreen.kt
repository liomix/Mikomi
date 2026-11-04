package com.liomix.mikomi.feature.animap

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.liomix.mikomi.core.navigation.ImmersionBridge
import com.liomix.mikomi.core.ui.component.LottieLoading

/** 巡礼地图（anitabi）页面地址。 */
const val ANIMAP_URL = "https://www.anitabi.cn/map?c=138.5098%2C37.2073&z=5.2"

/** 沉浸模式下底部导航栏临时唤出的自动收起延时（毫秒）：返回手势/手势条唤出后 2s 收起。 */
private const val NAV_BAR_HIDE_DELAY_MILLIS = 2_000L

/** 搜索输入防抖时长（毫秒）：检索在页面内完成，防抖只为减少跨 JSBridge 的次数。 */
private const val SEARCH_DEBOUNCE_MILLIS = 240L

/**
 * 「最近更新」还没拿到时的重试次数与间隔。
 *
 * 典型场景：地图刚打开、作品数据还在路上，用户已经点开搜索框。
 * 5 × 400ms 足够覆盖这段空窗，又不会在确实没数据时一直卡着「加载中」。
 */
private const val LATEST_RETRY_COUNT = 5
private const val LATEST_RETRY_DELAY_MILLIS = 400L

/**
 * 巡礼地图（AniMap）。以纯 WebView 形式内嵌 anitabi 地图，展示动漫取景地。
 *
 * WebView 装在 [AniMapWebViewContainer] 中，就是一个普通的子 View：
 * 触摸、焦点、输入法全部交给系统，页内搜索框才能正常弹键盘。
 *
 * 沉浸式交互设计：
 * - 状态栏常驻，页面内容避开状态栏区域（statusBars padding）；
 * - 进入巡礼页 2 秒后自动进入沉浸（隐藏应用底部导航栏），
 *   不靠滑动/缩放判定，直接写 [ImmersionBridge.immersive]；
 * - 沉浸中底部显示常驻手势条：仅点按可临时唤出底部导航栏（2s 自动收起），
 *   直接写 [ImmersionBridge.navBarTemp]；返回手势同样唤出（由宿主 NavDisplay.onBack 处理）。
 *
 * 沉浸状态权威副本直接放在 [ImmersionBridge]（Activity Retained 作用域），
 * 跨 entry 重建、跨 Activity 配置变更存活，且不依赖任何绑定时序。
 *
 * @param modifier 布局修饰符
 * @param url 地图页面地址
 * @param immersionBridge 沉浸状态桥，页面读写状态与宿主共用同一实例
 */
@Composable
fun AniMapScreen(
    modifier: Modifier = Modifier,
    url: String = ANIMAP_URL,
    immersionBridge: ImmersionBridge,
) {
    // 地图页面加载指示：true 时显示环形进度。
    var loading by remember { mutableStateOf(true) }
    // 沉浸状态权威副本：Activity Retained 作用域，跨 entry 重建和配置变更存活。
    val immersive by immersionBridge.immersive
    val showNavBar by immersionBridge.navBarTemp

    // 手势条点按唤出后 2s 自动收起；收起后若仍在沉浸页面则重新进入沉浸。
    // 用 LaunchedEffect(showNavBar) 键：navBarTemp 从 false→true 启动定时器，
    // 收起后写 false 会再次触发（if 不满足直接返回），不影响状态。
    LaunchedEffect(showNavBar) {
        if (showNavBar) {
            kotlinx.coroutines.delay(NAV_BAR_HIDE_DELAY_MILLIS)
            immersionBridge.onNavBarTempChange(false)
            // 收起后重新进入沉浸：immersive 仍为 true（返回唤出不写 false），
            // 这里显式写 true 确保手势条重新出现、导航栏隐藏。
            if (immersionBridge.immersive.value) {
                immersionBridge.onImmersiveChange(true)
            }
        }
    }

    // 自动进入沉浸（隐藏底部导航栏）计时器位于宿主 MikomiNavHost：
    // 切回本页时 AndroidView factory 重建容器（缓存命中则把旧 WebView 挂回来）
    // → ViewRootImpl relayout / ImeFocusController 焦点抢占会让 entry effect scope 被销毁
    // 重建，delay 协程被取消后键（immersive）未变不再重启，导致 2s 计时器永不完成
    // （真机日志已证实：LaunchedEffect(immersive=false) entered 后 2s 无任何后续日志）。
    // 页面 entry 不再承担计时，避免被 WebView 窗口挂载打断。
    //
    // 搜索态：展开后顶部变成输入框，建议列表盖在地图上。
    // 检索完全在页面内进行（数据就在页面内存里，见 AniMapHit），这里只持有展示所需的状态。
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<AniMapHit>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    // 页面脚本是否已就绪：地图数据没到位时「没找到」是假象，不能就这么告诉用户。
    var searchReady by remember { mutableStateOf(false) }
    // 调试包才把注入脚本的返回值报出来：这个页面全靠脚本驱动，
    // 出问题时得能分清「脚本没跑 / 对象没找到 / 飞了但落点不对」。
    val context = LocalContext.current
    val isDebuggable = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    // 当前挂载的容器：搜索与定位都要往页面里求值，必须拿到活着的那一个 WebView。
    var container by remember { mutableStateOf<AniMapWebViewContainer?>(null) }

    // 输入防抖：每次输入取消上一轮，只保留最后一次的结果。
    // 检索不走网络（页面内存里筛），防抖是为了不在每个按键上都跨一次 JSBridge。
    LaunchedEffect(searchExpanded, searchQuery) {
        if (!searchExpanded) return@LaunchedEffect
        val keyword = searchQuery.trim()
        if (keyword.isEmpty()) {
            // 还没输入：先摆「最近更新」占位。站点自己那块搜索框也是这么做的，
            // 空着一条都不给，用户只能凭记忆硬敲关键词。
            searchLoading = true
            var payload = container?.latestHits() ?: AniMapSearchPayload()
            // 首屏数据常在地图加载完之后才到，此刻列表空是「还不能查」而不是「没有作品」。
            // 展开搜索的一瞬间就撞上这种情况很常见，退几档重试，不用用户手动再点一次。
            var attempt = 0
            while (!payload.ready && attempt < LATEST_RETRY_COUNT) {
                kotlinx.coroutines.delay(LATEST_RETRY_DELAY_MILLIS)
                payload = container?.latestHits() ?: AniMapSearchPayload()
                attempt++
            }
            searchHits = payload.hits
            searchReady = payload.ready
            searchLoading = false
            return@LaunchedEffect
        }
        searchLoading = true
        kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MILLIS)
        val payload = container?.search(keyword) ?: AniMapSearchPayload()
        searchHits = payload.hits
        searchReady = payload.ready
        searchLoading = false
    }

    // 搜索面板的底边（px，相对本 Box）。点空白的判定区域从这里往下，
    // 不能把建议条目和输入框本身算进去，否则「点条目」会先被当成「点空白」。
    var searchBarBottomPx by remember { mutableIntStateOf(0) }

    // 搜索态的收起是一件事：三处入口（返回键、点空白、选中条目）必须完全一致，
    // 尤其要一起清掉 query/hits —— 只关面板不清数据，下次展开会先闪一屏上次的结果。
    val collapseSearch: () -> Unit = {
        searchExpanded = false
        searchQuery = ""
        searchHits = emptyList()
    }

    // 搜索态优先吃掉返回键：先收起键盘/搜索，而不是直接离开巡礼页。
    BackHandler(enabled = searchExpanded) {
        collapseSearch()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 加载指示须放在 AndroidView 之后声明：Box 子节点按声明顺序绘制，
        // 后声明的 Compose 内容才会盖到 WebView 之上。
        AndroidView(
            // 状态栏避让：地图位于状态栏之下，搜索框不会被状态栏压住。
            // ime 避让：键盘弹起时整块地图让出去，站点才会把聚焦的输入框滚到可见区
            // （WebView 只有自身尺寸变了，才会当成 viewport 变化去重排/滚动）。
            // 例外是搜索态：这时打字的是 App 自己的输入框，地图再缩一次只会白跳一下。
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .then(if (searchExpanded) Modifier else Modifier.imePadding()),
            factory = { context ->
                // 复用缓存的 WebView：切换回巡礼页不再重新加载地图。
                AniMapCache.acquire(context, url).also { acquired ->
                    container = acquired
                    acquired.onLoadingChanged = { isLoading -> loading = isLoading }
                    acquired.onPageFinished = { view, finishedUrl ->
                        // 用页面最终 URL 记录缓存；anitabi 会重定向改写 URL，
                        // 与初始 url 不等会导致缓存永不命中。
                        AniMapCache.onLoaded(finishedUrl ?: view.url ?: url)
                    }
                    // 缓存 WebView 已完成首屏加载；其挂载发生在 AndroidView 布局阶段，
                    // 因此不能通过此刻的 container.webView 是否为空判断加载状态。
                    // 来自预热的页面数据齐了但还没画过首帧，仍要先盖住一下：
                    // 直接撤遮罩会先露出底色和半个地图，再跳成完整地图。
                    loading = !acquired.hasCachedWebView || acquired.needsPresentWait
                }
            },
            onRelease = {
                // 容器随本次组合卸载；搜索不能再对着一个已经摘下来的 WebView 求值。
                container = null
                // 仅把 WebView 从容器上摘下，本体由缓存持有，下次进入直接挂回来。
                AniMapCache.release(it)
            },
        )

        if (loading) {
            // 地图加载遮罩：站点自己的启动动画 + 说明文字，居中显示。
            //
            // 这块现在**顶替了站点的启动页**：站点的 `.ui-loading`（浅蓝底 + 地图 SVG）
            // 已被样式藏掉，而它原本盖住的正是「HTML 到了、地图还没画出来」这段空窗。
            // 因此这里必须是不透明底色，否则用户会先看到一片半成品画面再突然出图；
            // 取站点地图的浅蓝而不是主题色：巡礼页的地图始终是浅色的，夜间模式也不该黑一下再变亮。
            val loadingText = stringResource(R.string.animap_loading)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AniMapLoadingBackground)
                    .semantics(mergeDescendants = true) { contentDescription = loadingText },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LottieLoading(
                        rawRes = R.raw.map,
                        size = 96.dp,
                        // 这份动画自带配色（青/绿/橙），本来就是在同色底图上用的；
                        // 统一染色会把它压成一坨单色，反而看不出画的是什么。
                        tint = null,
                    )
                    Text(
                        text = loadingText,
                        style = MaterialTheme.typography.bodyMedium,
                        // 巡礼页始终是浅色地图，不随夜间模式变深：文字固定用浅色主题的次要色。
                        color = AniMapOverlayTextColor,
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

        // 点建议框以外的任何地方都能收起搜索。范围从面板底边往下铺满，
        // 不盖住输入框与建议条目（否则点条目会先被这里吃掉）。
        //
        // 关键：这里**不消费**手势，只在 Initial 阶段旁听，判据是「按下到抬起几乎没有位移」。
        // 遮罩下面就是地图 WebView，一旦像普通 clickable 那样 consume 掉 down，
        // 搜索态下地图就彻底拖不动了 —— 和当初「横滑切页掐断地图拖动」是同一个坑。
        // 所以：拖动照旧交给地图，只有轻点才算「点空白」。
        if (searchExpanded) {
            val topInset = with(LocalDensity.current) { searchBarBottomPx.toDp() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                var dragged = false
                                var up = false
                                while (!up) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial)
                                        .changes.firstOrNull { it.id == down.id } ?: break
                                    if (!dragged &&
                                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    ) {
                                        dragged = true
                                    }
                                    up = !change.pressed
                                }
                                if (!dragged) collapseSearch()
                            }
                        }
                    },
            )
        }

        // 搜索条最后声明：浮在加载遮罩之上，用户不必等地图画完才能搜。
        AniMapSearchBar(
            expanded = searchExpanded,
            query = searchQuery,
            hits = searchHits,
            searching = searchLoading,
            ready = searchReady,
            onQueryChange = { searchQuery = it },
            onExpand = { searchExpanded = true },
            onCollapse = collapseSearch,
            onHitClick = { hit ->
                // 作品缩放到取景地范围、地标落到具体坐标、城市圈住该城市全部取景点；
                // 按 id 在页面里现取对象，不重新加载页面。
                container?.openHit(hit) { result ->
                    // 调试包才回报：这个页面全靠注入脚本驱动，出问题时要能看到是脚本没跑、
                    // 对象没找到，还是飞了但落点不对。正式包里不打扰用户。
                    if (isDebuggable) {
                        Toast.makeText(context, "地图定位：$result", Toast.LENGTH_SHORT).show()
                    }
                }
                collapseSearch()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                // 记下面板的实际底边：点空白的判定范围要从这里往下，不能盖住建议条目。
                .onGloballyPositioned { searchBarBottomPx = it.boundsInParent().bottom.toInt() },
        )
    }
}

/** 巡礼页浮层文字色（固定浅色主题的次要文字色，不参与夜间切换）。 */
private val AniMapOverlayTextColor = Color(0xFF49454F)

/**
 * 加载遮罩底色：与站点地图底色一致（`#9DDBF1`），不随夜间模式变深。
 *
 * 它顶替的是站点那块同色的启动页（`.ui-loading`），作用是把「还没画出来的地图」整块盖住。
 * 取同一个色值，遮罩撤掉的那一刻不会有底色跳变，看起来就是「地图在上面画出来了」。
 */
private val AniMapLoadingBackground = Color(0xFF9DDBF1)
