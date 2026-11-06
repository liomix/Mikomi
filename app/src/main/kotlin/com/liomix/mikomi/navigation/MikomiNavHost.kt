package com.liomix.mikomi.navigation

import android.app.Activity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.liomix.mikomi.R
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.animationsEnabled
import com.liomix.mikomi.core.model.PageTransitionStyle
import com.liomix.mikomi.core.model.TransitionStyle
import com.liomix.mikomi.core.navigation.AniMapDestination
import com.liomix.mikomi.core.navigation.CommunityDestination
import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.HomeDestination
import com.liomix.mikomi.core.navigation.ImmersionBridge
import com.liomix.mikomi.core.navigation.LocalNavigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.rememberNavigator
import com.liomix.mikomi.core.navigation.topLevelDestinations
import com.liomix.mikomi.core.ui.component.business.HeroFlightController
import com.liomix.mikomi.core.ui.component.business.HeroFlightOverlay
import com.liomix.mikomi.core.ui.component.business.LocalHeroFlightController
import com.liomix.mikomi.core.ui.component.layout.LocalBottomBarSpace
import com.liomix.mikomi.feature.animap.AniMapWarmer

/** 进入巡礼页后自动隐藏底部导航栏的延时（毫秒）：不靠滑动判定，进页面 2s 后自动进入沉浸。 */
private const val NAV_BAR_AUTO_HIDE_DELAY_MILLIS = 2_000L

/**
 * 首页停留多久之后开始预热巡礼页（毫秒）。
 *
 * 太早会和首屏组合、封面解码、首次布局挤在一起；太晚则用户已经点走了。
 * 4 秒大致是「首屏看完、开始细看内容」的位置，此时主线程不再是满负荷。
 */
private const val ANIMAP_WARM_UP_DELAY_MILLIS = 4_000L

/** 顶级页面左右滑动切换的触发距离。 */
private val TOP_LEVEL_SWIPE_THRESHOLD = 80.dp

@Composable
fun MikomiNavHost(
    entryProviderInstallers: Set<EntryProviderInstaller>,
    immersionBridge: ImmersionBridge,
    heroStyleId: String = TransitionStyle.Translate.id,
    pageTransitionId: String = PageTransitionStyle.Push.id,
) {
    // 返回栈由组合期创建（可跨配置变更与进程死亡恢复），见 rememberNavigator。
    // 不再从 Hilt 注入：ActivityRetained 单例只在配置变更时活着，进程被回收后栈全丢。
    val navigator = rememberNavigator()
    val currentTopLevel = navigator.currentTopLevel

    val homeLabel = stringResource(R.string.nav_home)
    val aniMapLabel = stringResource(R.string.nav_animap)
    val communityLabel = stringResource(R.string.nav_community)
    val profileLabel = stringResource(R.string.nav_profile)

    // 从左到右：首页 / 巡礼地图 / 社区 / 我的
    val navItems = remember(homeLabel, aniMapLabel, communityLabel, profileLabel) {
        listOf(
            MikomiNavItem(
                key = HomeDestination,
                icon = MikomiIcons.Home,
                selectedIcon = MikomiIcons.Home,
                label = homeLabel,
            ),
            MikomiNavItem(
                key = AniMapDestination,
                icon = MikomiIcons.Map,
                selectedIcon = MikomiIcons.Map,
                label = aniMapLabel,
            ),
            MikomiNavItem(
                key = CommunityDestination,
                icon = MikomiIcons.Forum,
                selectedIcon = MikomiIcons.Forum,
                label = communityLabel,
            ),
            MikomiNavItem(
                key = ProfileDestination,
                icon = MikomiIcons.Person,
                selectedIcon = MikomiIcons.Person,
                label = profileLabel,
            ),
        )
    }

    // 左右滑动切换顶级页面的顺序：与底部导航栏从左到右一致。
    val topLevelOrder: List<NavKey> = remember(navItems) {
        navItems.mapNotNull { it.key as? NavKey }
    }

    // 栈顶是否落在顶级目的地：进入详情/播放器/时间表等二级页面时栈顶为二级目的地，
    // 底部导航栏随之隐藏；返回顶级目的地后恢复显示。
    val isTopLevel = navigator.current in topLevelDestinations

    // 沉浸状态权威副本直接在 Activity Retained 作用域的 ImmersionBridge，
    // 跨 entry 重建和配置变更存活，且不依赖任何绑定时序。
    val immersiveRequested by immersionBridge.immersive
    val showNavBar by immersionBridge.navBarTemp

    // 顶级目的地切换、巡礼页自动沉浸计时由同一个 effect 协调，
    // 避免多个 effect 竞争写入同一份沉浸状态。
    LaunchedEffect(currentTopLevel) {
        immersionBridge.resetForTopLevelNavigation()
        if (currentTopLevel != AniMapDestination) return@LaunchedEffect
        kotlinx.coroutines.delay(NAV_BAR_AUTO_HIDE_DELAY_MILLIS)
        if (!immersionBridge.navBarTemp.value) {
            immersionBridge.onImmersiveChange(true)
        }
    }

    val navEntryProvider = remember {
        entryProvider {
            entryProviderInstallers.forEach { installer ->
                // installer 是「带 EntryProviderScope 接收者的 lambda」，这里显式传进去：
                // 依赖隐式接收者能编译，但读代码的人看不出目的地到底注册到了哪。
                installer.invoke(this)
            }
        }
    }

    // 系统栏一律不动：可见性、配色、edge-to-edge 全部由 MainActivity 的 enableEdgeToEdge
    // 统一决定，巡礼页不参与。曾经在这里按沉浸状态强制 show(systemBars()) 并重设
    // setDecorFitsSystemWindows——那等于每次进出巡礼页都往窗口上盖一次章，
    // 与播放页的全屏（要隐藏系统栏）互相打架，也让状态栏行为变成「巡礼页专属」。
    // 巡礼页的沉浸只是隐藏我们自己画的底部导航栏，和系统栏无关。
    val view = LocalView.current

    // 预热巡礼页需要一个 Activity：WebView 要挂在它的窗口上（不可见）才能真正布局与加载。
    val activity = view.context as? Activity

    // 首页静止时预热巡礼页：这是唯一能把「点进去等七八秒」变成「点进去就有」的时机。
    //
    // 巡礼页的等待里，Chromium 初始化、HTML 往返、JS 与作品数据都不要求页面可见，
    // 只有最后那一帧要求。把前三段提前到首页完成，用户进来时只剩首帧。
    //
    // 为什么要延后：WebView 的构造必须在主线程，首次初始化要 100~400ms。
    // 放在首屏刚上来的时刻会和组合、图片解码、首次布局抢同一条队列；
    // 等 4 秒再动手，用户已经在看内容，那一下的代价不可见。
    LaunchedEffect(currentTopLevel, isTopLevel, activity) {
        if (currentTopLevel != HomeDestination || !isTopLevel) return@LaunchedEffect
        if (activity == null) return@LaunchedEffect
        kotlinx.coroutines.delay(ANIMAP_WARM_UP_DELAY_MILLIS)
        AniMapWarmer.warmUp(activity)
    }

    // 根屏仅在巡礼页处于沉浸状态时才拦截返回，用于临时显示导航栏。
    // 其它目的地交还给系统返回，避免首页或“我的”页无法正常退出应用。
    val isRootScreen = !navigator.canGoBack
    val shouldRevealNavBar =
        currentTopLevel == AniMapDestination && immersiveRequested && !showNavBar
    BackHandler(enabled = isRootScreen && shouldRevealNavBar) {
        immersionBridge.onNavBarTempChange(true)
    }

    // 封面飞行控制器：整个导航宿主共用一份，样式跟随用户偏好。
    val heroController = remember { HeroFlightController() }
    SideEffect { heroController.style = TransitionStyle.fromId(heroStyleId) }

    // 非巡礼沉浸态的返回事件始终按导航栈处理；栈内只剩根页面时退出应用。
    val navBackHandler: () -> Unit = {
        if (shouldRevealNavBar) {
            immersionBridge.onNavBarTempChange(true)
        } else {
            // 从详情页返回时先起反向飞行（详情封面 → 列表卡封面），再出栈。
            if (navigator.current is DetailDestination) {
                heroController.beginReverse(key = null)
            }
            if (!navigator.goBack()) {
                (view.context as? Activity)?.finish()
            }
        }
    }

    // 转场方向：栈变长 = 进入（新页要从右 48dp 处淡入），变短 = 返回（目标页直接可见）。
    // 上一次的栈深存在普通数组里而不是 State：它只在组合期间被读，写入放 SideEffect，
    // 避免「组合里改状态 → 再排一次组合」。
    val stackDepth = navigator.stack.size
    val lastStackDepth = remember { intArrayOf(stackDepth) }
    val motionDirection =
        if (stackDepth >= lastStackDepth[0]) PageMotionDirection.Enter else PageMotionDirection.Back
    SideEffect { lastStackDepth[0] = stackDepth }

    // 根容器统一铺主题背景：各页面不再需要各自设置底色，
    // 夜间模式下除巡礼页（WebView 地图不透明，直接盖住）外全部为纯黑。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 顶级页面之间左右滑动切换：竖向手势与子级横向滚动（轮播、分页）都优先，
            // 只有它们没消费的横向手势才落到这里，因此不会抢详情页/轮播的滑动。
            .pointerInput(isTopLevel, topLevelOrder, currentTopLevel, activity) {
                // 巡礼页例外：那个页面的主体是真实 WebView（地图），平移要原地收到每一帧
                // move。而 Compose 的指针事件先于原生子 View 派发——这里一旦判定成横向
                // 拖动并 consume，WebView 就再也收不到 move，真机表现正是「上下能拖、
                // 左右只挪一个 touchSlop 就卡住」。横向平移是地图的核心交互，切页只是便利，
                // 所以巡礼页放弃横滑，要换页点底部导航栏（沉浸中先点底部手势条唤出）。
                if (!isTopLevel || currentTopLevel == AniMapDestination) return@pointerInput
                var dragged = 0f
                var warmUpRequested = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragged = 0f
                        warmUpRequested = false
                    },
                    onDragEnd = {
                        val threshold = TOP_LEVEL_SWIPE_THRESHOLD.toPx()
                        val index = topLevelOrder.indexOf(currentTopLevel)
                        if (index >= 0) {
                            if (dragged <= -threshold) {
                                topLevelOrder.getOrNull(index + 1)?.let(navigator::selectTopLevel)
                            }
                            if (dragged >= threshold) {
                                topLevelOrder.getOrNull(index - 1)?.let(navigator::selectTopLevel)
                            }
                        }
                        dragged = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        dragged += amount
                        change.consume()
                        // 滑过阈值一半就认为「多半要切页」：提前把巡礼页建起来，
                        // 松手时 Chromium 已经初始化完、HTML 也在路上了。
                        // 幂等，且真正没滑够时只是浪费一个后台页面（T半小时自行回收）。
                        if (!warmUpRequested) {
                            val half = TOP_LEVEL_SWIPE_THRESHOLD.toPx() / 2
                            if (dragged <= -half || dragged >= half) {
                                warmUpRequested = true
                                activity?.let { AniMapWarmer.warmUp(it) }
                            }
                        }
                    },
                )
            },
    ) {
        // 48dp 位移按当前密度换算一次：slideOut 的 offset lambda 没有 Density 作用域。
        val pageOffsetPx = with(LocalDensity.current) { PAGE_MOTION_TRAVEL.roundToPx() }
        // 系统关掉动画时（开发者选项「动画时长 0」、无障碍「移除动画」、部分省电模式），
        // 自绘帧仍然会跑，只是每一帧都直接算到终点 —— 用户一帧都看不到，CPU 照付。
        // 与夜间切换的圆形揭示用同一条判据：直接落位，什么都不播。
        val systemAnimationsEnabled = remember(view) { view.context.animationsEnabled() }
        val pageTransition = remember(pageTransitionId, systemAnimationsEnabled) {
            if (systemAnimationsEnabled) {
                PageTransitionStyle.fromId(pageTransitionId)
            } else {
                PageTransitionStyle.None
            }
        }
        // 页面入场动画挂在 entry 装饰器上，装饰器实例必须稳定，否则每次重组都会重建一遍
        // 装饰链、连带把每个页面的动画状态也重置。换样式时**也不能**重建（重建 = 当前页
        // 被重新挂载，屏幕会闪一下并重播入场）；档位改为经 CompositionLocal 下发，
        // 由每个页面在首次组合时自己冻结一份，只影响之后新开的页面。
        val saveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
        // per-entry 的 ViewModelStore：`hiltViewModel()` 取到的 owner 由它下发。
        //
        // 少了这一层，装饰链里没有 `LocalViewModelStoreOwner` 的 entry 级提供者，
        // `hiltViewModel()` 会一路向上找到 Activity —— 于是**所有页面共用同一个
        // ViewModel 实例**：详情页换一部番不会重建 ViewModel（只能靠 `bind()` 手工换绑），
        // 播放器返回后也不会 `onCleared()`（只能靠 `DisposableEffect` 手工停）。
        // 这两个补丁此前都写在 Route 里，根因就在这里。
        val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
        val pageMotionEntryDecorator = remember { pageMotionDecorator() }
        val entryDecorators = remember(
            saveableStateDecorator,
            viewModelStoreDecorator,
            pageMotionEntryDecorator,
        ) {
            // 顺序有讲究：SaveableState 最外层（页面状态跟着 entry 走），
            // 其次 ViewModelStore（它的生命周期同样按 entry 计），
            // 动画装饰器最内层 —— 它只负责把内容包进一层位移/透明度，不参与状态归属。
            listOf(saveableStateDecorator, viewModelStoreDecorator, pageMotionEntryDecorator)
        }
        CompositionLocalProvider(
            LocalHeroFlightController provides heroController,
            // 底栏占多高由壳告诉页面，页面不必知道那是一条导航栏 —— 见 LocalBottomBarSpace。
            LocalBottomBarSpace provides MikomiNavBarBottomSpace,
            // 页面不再从构造参数收 Navigator，统一从这里取。
            LocalNavigator provides navigator,
            // 只影响「首次组合」那一瞬间的页面：装饰器会把它冻结，之后不再跟变。
            LocalPageMotionDirection provides motionDirection,
            LocalPageTransitionStyle provides pageTransition,
        ) {
            NavDisplay(
                backStack = navigator.stack,
                onBack = navBackHandler,
                entryProvider = navEntryProvider,
                entryDecorators = entryDecorators,
                // 关掉 AnimatedContent 的尺寸动画：页面本就是整屏，尺寸动画只会让新旧页
                // 互相裁剪（表现为页面被裁成一条/半张再展开）。
                //
                // 进入这一步的插值不由框架算：新页自己按帧推进（见 TelegramPageMotion），
                // 这里只给旧页挂一个肉眼不可见的退场，用它的时长当「转场窗口」。
                transitionSpec = {
                    // 进入时的旧页：推进档让它几乎不动地留住（等新页滑到位），
                    // 淡入档让它正常淡出（与新页交叉），无动效档直接撤。
                    (EnterTransition.None togetherWith pageHold(pageTransition)).using(null)
                },
                popTransitionSpec = {
                    (EnterTransition.None togetherWith pageExit(pageTransition, pageOffsetPx))
                        .using(null)
                },
                // 预测性返回复用返回转场：tween 会按手势进度 seek，拖动时旧页跟手右移。
                predictivePopTransitionSpec = {
                    (EnterTransition.None togetherWith pageExit(pageTransition, pageOffsetPx))
                        .using(null)
                },
            )
        }

        // 沉浸式或非顶级页面（详情/播放器等二级页面）下隐藏底部导航栏，
        // 滑入/滑出动画；其余页面正常显示。沉浸中可通过手势条临时唤出（showNavBar），
        // 2 秒后自动收起。
        AnimatedVisibility(
            visible = isTopLevel && (!immersiveRequested || showNavBar),
            // 显隐时长走 token：默认的 spring 收尾很长，底栏读起来像「飘」上来，
            // 与全站 150/200ms 的节奏不是一套。进场 Standard、退场 Accelerate，
            // 和页面进出用同一组曲线（tg 的 EASE_OUT / EASE_IN）。
            enter = slideInVertically(
                animationSpec = tween(
                    MikomiMotion.Duration.Short,
                    easing = MikomiMotion.Curve.Standard,
                ),
                initialOffsetY = { it },
            ) + fadeIn(
                tween(MikomiMotion.Duration.Short, easing = MikomiMotion.Curve.Standard),
            ),
            exit = slideOutVertically(
                animationSpec = tween(
                    MikomiMotion.Duration.Short,
                    easing = MikomiMotion.Curve.Accelerate,
                ),
                targetOffsetY = { it },
            ) + fadeOut(
                tween(MikomiMotion.Duration.Short, easing = MikomiMotion.Curve.Accelerate),
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                // 外边距由导航栏自己的常量给出（16dp 左右 / 8dp 底部），页面按同一份规格给底栏让位。
                .padding(
                    horizontal = MikomiNavBarHorizontalMargin,
                    vertical = MikomiNavBarBottomMargin,
                )
                .widthIn(max = 520.dp),
        ) {
            MikomiNavigationBar(
                items = navItems,
                selectedKey = currentTopLevel,
                onSelect = { key ->
                    (key as? NavKey)?.let { destination ->
                        // 点巡礼 tab 时补一次预热：首页停留不足 4 秒就点进来时，
                        // 空闲预热还没触发，这里至少能把 Chromium 初始化塞进转场动画。
                        // 已经预热过则直接返回，没有额外开销。
                        if (destination == AniMapDestination) {
                            activity?.let { AniMapWarmer.warmUp(it) }
                        }
                        // 先同步复位，避免切回巡礼页的首帧沿用上一次的沉浸状态而隐藏底栏。
                        immersionBridge.resetForTopLevelNavigation()
                        navigator.selectTopLevel(destination)
                    }
                },
            )
        }

        // 封面飞行层放在**最后一个**子项：页面与底部导航栏都在它之下，
        // 与 Flutter Hero 画在 Navigator Overlay 最上层一致。它没有 pointerInput，不拦触摸。
        HeroFlightOverlay(controller = heroController)
    }
}

/**
 * 页面转场：新旧两页之间的过渡，与封面飞行（[HeroFlightController]）互不影响。
 *
 * 进与出刻意**不对称**，这是 Telegram `ActionBarLayout` 的做法：
 * - 进入：新页从右 48dp 处淡入，旧页保持不透明、原地不动（`:1887`）；
 * - 退出：目标页直接可见且不动，只让将被销毁的旧页右移 48dp 淡出（`:1906`）。
 *
 * 进入的插值不在这里算——它在页面里按帧累加（见 [PageMotionSurface]），
 * 所以这里的新页是 `EnterTransition.None`，[pageHold] 只负责旧页的存活窗口。
 *
 * 退出仍然用 spec：预测性返回手势要按手指进度逐帧 seek，只有 spec 驱动才跟手。
 * 那里手指本身就是时钟，不存在「掉帧跳格」的问题。
 *
 * 位移用 slide 而不是缩放：Telegram 的行程只有 48dp，读作「整块内容推进来」，
 * 不是「页面被缩小」。封面飞行靠 `onGloballyPositioned` 逐帧修正终点，
 * 位移期间会跟着一起归位，落点不会偏。
 *
 * 三档动效在这里只改旧页的退场方式，新页的入场统一由 [PageMotionSurface] 画：
 * 推进档把旧页**冻住**（等新页滑到位，左侧让位缝里看到的是它），
 * 淡入档让旧页正常淡出（与新页交叉），无动效档直接撤。
 */
private fun AnimatedContentTransitionScope<*>.pageHold(
    style: PageTransitionStyle,
): ExitTransition = when (style) {
    PageTransitionStyle.None -> ExitTransition.None
    PageTransitionStyle.Fade -> fadeOut(
        animationSpec = tween(PAGE_FADE_MILLIS, easing = PageMotionEasing),
    )
    PageTransitionStyle.Push -> fadeOut(
        animationSpec = tween(style.motionWindowMillis),
        // 只降 0.5%：肉眼不可见，纯粹是为了让框架「有事可做」从而把旧页留在组合里。
        // 没有它，AnimatedContent 会在第一帧就把上一页撤掉，新页半透明时透出的是页面底色。
        targetAlpha = 0.995f,
    ) + ExitTransition.KeepUntilTransitionsFinished
}

/** 退出转场：被关闭的页按档位退场，目标页始终完全不动。 */
private fun pageExit(style: PageTransitionStyle, offsetPx: Int): ExitTransition = when (style) {
    PageTransitionStyle.None -> ExitTransition.None
    PageTransitionStyle.Fade -> fadeOut(
        animationSpec = tween(PAGE_FADE_MILLIS, easing = PageMotionEasing),
    )
    PageTransitionStyle.Push -> fadeOut(
        tween(PAGE_MOTION_MILLIS, easing = PageMotionEasing),
    ) + slideOutHorizontally(
        animationSpec = tween(PAGE_MOTION_MILLIS, easing = PageMotionEasing),
        targetOffsetX = { offsetPx },
    )
}
