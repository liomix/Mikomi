package com.liomix.mikomi.navigation

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.liomix.mikomi.R
import com.liomix.mikomi.core.designsystem.component.MikomiNavItem
import com.liomix.mikomi.core.designsystem.component.MikomiNavigationBar
import com.liomix.mikomi.core.model.PageTransitionStyle
import com.liomix.mikomi.core.model.TransitionStyle
import com.liomix.mikomi.core.navigation.AniMapDestination
import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.HomeDestination
import com.liomix.mikomi.core.navigation.ImmersionBridge
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.topLevelDestinations
import com.liomix.mikomi.core.ui.component.HeroFlightController
import com.liomix.mikomi.core.ui.component.HeroFlightOverlay
import com.liomix.mikomi.core.ui.component.LocalHeroFlightController

/** 进入巡礼页后自动隐藏底部导航栏的延时（毫秒）：不靠滑动判定，进页面 2s 后自动进入沉浸。 */
private const val NAV_BAR_AUTO_HIDE_DELAY_MILLIS = 2_000L

/**
 * 页面转场统一 spring 参数。
 *
 * `stiffness` 从 1600 降到 400：1600 只有约 120ms，加上封面飞行同时进行，
 * 几个样式在肉眼上几乎分辨不出来（会被当成「没生效」）。400 约 300ms，
 * 与 Dart 版 MaterialPageRoute 的时长一致。
 */
private const val SPRING_DAMPING = 1.0f
private const val SPRING_STIFFNESS = 400.0f

/** 缩放转场的起始缩放比（明显小于「标准」的 0.88，保证肉眼可辨）。 */
private const val SCALE_FROM = 0.55f

/** 缩放样式的弹簧参数：欠阻尼带回弹。 */
private const val SCALE_SPRING_DAMPING = 0.72f
private const val SCALE_SPRING_STIFFNESS = 520.0f

/** 交叉溶解（淡入淡出）时长。 */
private const val FADE_MILLIS = 320

/** 标准转场的起始缩放比：比 [SCALE_FROM] 更收敛，接近 Android 12 默认的页面缩放。 */
private const val ZOOM_FROM = 0.88f

/**
 * 页面转场统一缓动。
 *
 * `dampingRatio = 1` 无过冲、`stiffness = 1600` 干脆利落；泛型化以便
 * fade / scale（`Float`）等不同数值类型共用同一套参数。
 */
private fun <T> pageMotion(): SpringSpec<T> = spring(
    dampingRatio = SPRING_DAMPING,
    stiffness = SPRING_STIFFNESS,
    visibilityThreshold = null,
)

@Composable
fun MikomiNavHost(
    navigator: Navigator,
    entryProviderInstallers: Set<EntryProviderInstaller>,
    immersionBridge: ImmersionBridge,
    heroStyleId: String = TransitionStyle.Translate.id,
    pageStyleId: String = PageTransitionStyle.Standard.id,
) {
    val homeLabel = stringResource(R.string.nav_home)
    val aniMapLabel = stringResource(R.string.nav_animap)
    val profileLabel = stringResource(R.string.nav_profile)

    // 从左到右：首页 / 巡礼地图 / 我的
    val navItems = remember(homeLabel, aniMapLabel, profileLabel) {
        listOf(
            MikomiNavItem(
                key = HomeDestination,
                icon = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home,
                label = homeLabel,
            ),
            MikomiNavItem(
                key = AniMapDestination,
                icon = Icons.Outlined.Map,
                selectedIcon = Icons.Filled.Map,
                label = aniMapLabel,
            ),
            MikomiNavItem(
                key = ProfileDestination,
                icon = Icons.Outlined.Person,
                selectedIcon = Icons.Filled.Person,
                label = profileLabel,
            ),
        )
    }

    val currentTopLevel = navigator.backStack.lastOrNull { it in topLevelDestinations }
        ?: HomeDestination

    // 栈顶是否落在顶级目的地：进入详情/播放器/时间表等二级页面时栈顶为二级目的地，
    // 底部导航栏随之隐藏；返回顶级目的地后恢复显示。
    val isTopLevel = navigator.backStack.lastOrNull() in topLevelDestinations

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
                installer(immersionBridge)
            }
        }
    }

    // AniMap 沉浸式：仅隐藏应用底部导航栏。状态栏常驻，不改动任何系统栏可见性
    // （地图延伸到状态栏之下但状态栏保持可见）。系统导航栏也保持可见，与状态栏一致。
    // 页面通过 ImmersiveCallback 上报"地图已被滑动"，仅此一条链路，不猜测页面状态。
    val view = LocalView.current
    LaunchedEffect(immersiveRequested, view) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        activity.window?.let { window ->
            // 保持 edge-to-edge：内容延伸到系统栏之下，但系统栏本身始终显示。
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 根屏仅在巡礼页处于沉浸状态时才拦截返回，用于临时显示导航栏。
    // 其它目的地交还给系统返回，避免首页或“我的”页无法正常退出应用。
    val isRootScreen = navigator.backStack.size <= 1
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
            if (navigator.backStack.lastOrNull() is DetailDestination) {
                heroController.beginReverse(key = null)
            }
            if (!navigator.goBack()) {
                (view.context as? Activity)?.finish()
            }
        }
    }

    // 根容器统一铺主题背景：各页面不再需要各自设置底色，
    // 夜间模式下除巡礼页（WebView 地图不透明，直接盖住）外全部为纯黑。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(LocalHeroFlightController provides heroController) {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = navBackHandler,
                entryProvider = navEntryProvider,
                // 关掉 AnimatedContent 的尺寸动画：页面本就是整屏，尺寸动画只会让新旧页
                // 互相裁剪（表现为页面被裁成一条/半张再展开）。
                transitionSpec = {
                    pageTransform(pageStyleId, forward = true).using(null)
                },
                popTransitionSpec = {
                    pageTransform(pageStyleId, forward = false).using(null)
                },
                // 预测性返回复用返回转场：spring 会实时跟随手势进度，无需单独一套动画。
                predictivePopTransitionSpec = {
                    pageTransform(pageStyleId, forward = false).using(null)
                },
            )
        }

        // 封面飞行层：画在所有页面之上，页面转场怎么滑/怎么缩都不影响它
        // （等价 Flutter Hero 画在 Navigator Overlay 上）。
        HeroFlightOverlay(controller = heroController)

        // 沉浸式或非顶级页面（详情/播放器等二级页面）下隐藏底部导航栏，
        // 滑入/滑出动画；其余页面正常显示。沉浸中可通过手势条临时唤出（showNavBar），
        // 2 秒后自动收起。
        AnimatedVisibility(
            visible = isTopLevel && (!immersiveRequested || showNavBar),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .widthIn(max = 520.dp),
        ) {
            MikomiNavigationBar(
                items = navItems,
                selectedKey = currentTopLevel,
                onSelect = { key ->
                    (key as? NavKey)?.let { destination ->
                        // 先同步复位，避免切回巡礼页的首帧沿用上一次的沉浸状态而隐藏底栏。
                        immersionBridge.resetForTopLevelNavigation()
                        navigator.selectTopLevel(destination)
                    }
                },
            )
        }
    }
}

/**
 * 页面动效：新旧两页之间的过渡，与封面飞行（[HeroFlightController]）互不影响。
 *
 * 三种样式刻意做成不同性质的动作，否则都是「新页盖在旧页上淡入」，只差几个百分点缩放：
 * - 标准：小幅缩放 + 淡入（Material 的 zoom 观感，最克制）
 * - 溶解：旧页淡出 + 新页淡入的真·交叉溶解，无缩放
 * - 弹入：从明显更小的尺寸带回弹地放大，不淡入
 * - 无：页面不做过渡，只保留封面飞行
 *
 * 不用 slide：slide 是布局位移，会让页面内元素上报的坐标带上位移，
 * 封面飞行拿不到正确终点（Dart 版干净正是因为 MaterialPageRoute 也是变换）。
 */
private fun pageTransform(styleId: String, forward: Boolean): ContentTransform = when (
    PageTransitionStyle.fromId(styleId)
) {
    PageTransitionStyle.Standard -> zoomTransform(forward)
    PageTransitionStyle.Fade -> fadeTransform()
    PageTransitionStyle.Scale -> scaleTransform()
    PageTransitionStyle.None -> ContentTransform(EnterTransition.None, ExitTransition.None)
}

/** 标准：新页 0.88 → 1 放大并淡入（对齐 Dart 版 MaterialPageRoute 的缩放过渡）。 */
private fun zoomTransform(forward: Boolean): ContentTransform {
    val scaleFrom = if (forward) ZOOM_FROM else 1f / ZOOM_FROM
    val enter = scaleIn(pageMotion(), initialScale = scaleFrom) + fadeIn(pageMotion())
    return enter togetherWith ExitTransition.None
}

/**
 * 溶解：旧页淡出、新页淡入的交叉溶解，两页同时渐变、无位移无缩放。
 *
 * 这是唯一会出现「两页都半透明」的样式，本来就是这个样式的定义；
 * 用 tween 而不是 spring，是为了让溶解速度稳定可预期。
 */
private fun fadeTransform(): ContentTransform {
    val spec = tween<Float>(durationMillis = FADE_MILLIS, easing = LinearOutSlowInEasing)
    return fadeIn(spec) togetherWith fadeOut(spec)
}

/**
 * 弹入：新页从明显更小的尺寸弹入（带回弹），不淡入。
 *
 * 起点取 [SCALE_FROM]（0.55）而不是 0.85，否则和「标准」的 0.88 只差一点点，
 * 用户会觉得两个样式一模一样。
 */
private fun scaleTransform(): ContentTransform =
    scaleIn(scaleInMotion(), initialScale = SCALE_FROM) togetherWith ExitTransition.None

/** 缩放样式的弹簧：略微欠阻尼，带回弹，和「标准」的线性观感拉开距离。 */
private fun <T> scaleInMotion(): SpringSpec<T> = spring(
    dampingRatio = SCALE_SPRING_DAMPING,
    stiffness = SCALE_SPRING_STIFFNESS,
    visibilityThreshold = null,
)
