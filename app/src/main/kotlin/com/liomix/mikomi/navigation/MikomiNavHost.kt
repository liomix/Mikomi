package com.liomix.mikomi.navigation

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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

/** 标准转场的退场缩放比：旧页轻微放大后淡出（远离观感，与 Flutter 的 zoom 返回一致）。 */
private const val ZOOM_OUT_TO = 1.06f

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
                    pushTransform(pageStyleId).using(null)
                },
                popTransitionSpec = {
                    popTransform(pageStyleId).using(null)
                },
                // 预测性返回复用返回转场：spring 会实时跟随手势进度，无需单独一套动画。
                predictivePopTransitionSpec = {
                    popTransform(pageStyleId).using(null)
                },
            )
        }

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

        // 封面飞行层放在**最后一个**子项：页面与底部导航栏都在它之下，
        // 与 Flutter Hero 画在 Navigator Overlay 最上层一致。它没有 pointerInput，不拦触摸。
        HeroFlightOverlay(controller = heroController)
    }
}

/**
 * 页面动效：新旧两页之间的过渡，与封面飞行（[HeroFlightController]）互不影响。
 *
 * 进与出刻意**不对称**：
 * - 进入（[pushTransform]）：新页动、旧页保持不透明；
 * - 退出（[popTransform]）：返回的目标页直接可见，只让将被销毁的旧页缩放淡出。
 *
 * 这条「任意时刻至少一页完全不透明，且要消失的那一页总有退场动作」的规则是关键：
 * 两页同时半透明会合成出 alpha < 1，透出窗口白底（表现为「白屏闪一下」）；
 * 而两页都关掉动画则会让返回变成硬切（表现为「返回没有退场」）。
 *
 * 不用 slide：slide 是布局位移，会让页面内元素上报的坐标带上位移，
 * 封面飞行拿不到正确终点（Dart 版干净正是因为 MaterialPageRoute 也是变换）。
 */
private fun pushTransform(styleId: String): ContentTransform = when (
    PageTransitionStyle.fromId(styleId)
) {
    PageTransitionStyle.Standard -> zoomIn() togetherWith ExitTransition.None
    // 只淡入新页、旧页不动：新页半透明时透出的是**不透明的旧页**，不会露白底。
    PageTransitionStyle.Fade -> fadeIn(fadeSpec()) togetherWith ExitTransition.None
    PageTransitionStyle.Scale -> scaleIn(
        scaleInMotion(),
        initialScale = SCALE_FROM,
    ) togetherWith ExitTransition.None
    PageTransitionStyle.None -> ContentTransform(EnterTransition.None, ExitTransition.None)
}

/** 页面动效的退出方向：目标页直接可见，旧页缩放淡出。 */
private fun popTransform(styleId: String): ContentTransform = when (
    PageTransitionStyle.fromId(styleId)
) {
    PageTransitionStyle.Standard -> EnterTransition.None togetherWith zoomOut()
    PageTransitionStyle.Fade -> EnterTransition.None togetherWith fadeOut(fadeSpec())
    // 与「弹入」对称：缩回原来的小尺寸再消失。
    PageTransitionStyle.Scale -> EnterTransition.None togetherWith (
        scaleOut(scaleInMotion(), targetScale = SCALE_FROM) + fadeOut(pageMotion())
        )
    PageTransitionStyle.None -> ContentTransform(EnterTransition.None, ExitTransition.None)
}

/** 标准进入：新页 0.88 → 1 放大并淡入（对齐 Dart 版 MaterialPageRoute 的缩放过渡）。 */
private fun zoomIn(): EnterTransition =
    scaleIn(pageMotion(), initialScale = ZOOM_FROM) + fadeIn(pageMotion())

/** 标准退出：旧页略微放大并淡出（远离观看者的观感，也是 Flutter zoom 返回的方向）。 */
private fun zoomOut(): ExitTransition =
    scaleOut(pageMotion(), targetScale = ZOOM_OUT_TO) + fadeOut(pageMotion())

/** 淡入淡出样式的统一缓动：溶解速度要稳定可预期，因此用 tween 而不是 spring。 */
private fun fadeSpec(): FiniteAnimationSpec<Float> =
    tween(durationMillis = FADE_MILLIS, easing = LinearOutSlowInEasing)

/** 缩放样式的弹簧：略微欠阻尼，带回弹，和「标准」的线性观感拉开距离。 */
private fun <T> scaleInMotion(): SpringSpec<T> = spring(
    dampingRatio = SCALE_SPRING_DAMPING,
    stiffness = SCALE_SPRING_STIFFNESS,
    visibilityThreshold = null,
)
