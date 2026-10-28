package com.liomix.mikomi.navigation

import android.app.Activity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import com.liomix.mikomi.core.model.TransitionStyle
import com.liomix.mikomi.core.navigation.AniMapDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.HomeDestination
import com.liomix.mikomi.core.navigation.ImmersionBridge
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.topLevelDestinations

/** 进入巡礼页后自动隐藏底部导航栏的延时（毫秒）：不靠滑动判定，进页面 2s 后自动进入沉浸。 */
private const val NAV_BAR_AUTO_HIDE_DELAY_MILLIS = 2_000L

/** 转场动画统一 spring 参数（近似 Material 运动规范，流畅不拖沓）。 */
private const val SPRING_DAMPING = 1.0f
private const val SPRING_STIFFNESS = 1600.0f

/** 缩放转场的起始/结束缩放比。 */
private const val SCALE_FROM = 0.85f

/**
 * 页面转场统一缓动。
 *
 * `dampingRatio = 1` 无过冲、`stiffness = 1600` 干脆利落；泛型化以便
 * slide（`IntOffset`）/ fade·scale（`Float`）共用同一套参数。
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
    transitionStyleId: String = TransitionStyle.Standard.id,
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

    // 非巡礼沉浸态的返回事件始终按导航栈处理；栈内只剩根页面时退出应用。
    val navBackHandler: () -> Unit = {
        if (shouldRevealNavBar) {
            immersionBridge.onNavBarTempChange(true)
        } else if (!navigator.goBack()) {
            (view.context as? Activity)?.finish()
        }
    }

    // 根容器统一铺主题背景：各页面不再需要各自设置底色，
    // 夜间模式下除巡礼页（WebView 地图不透明，直接盖住）外全部为纯黑。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavDisplay(
            backStack = navigator.backStack,
            onBack = navBackHandler,
            entryProvider = navEntryProvider,
            transitionSpec = { transitionSpec(transitionStyleId) },
            popTransitionSpec = { popTransitionSpec(transitionStyleId) },
            // 预测性返回复用返回转场：spring 会实时跟随手势进度，无需单独一套动画。
            predictivePopTransitionSpec = { popTransitionSpec(transitionStyleId) },
        )

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
 * 进入转场：根据用户选择的 [TransitionStyle] 决定页面进入动画。
 *
 * 方向语义由 [movingForward] 决定，返回转场只是把方向反过来，避免维护两份对称的动画表。
 */
private fun AnimatedContentTransitionScope<*>.transitionSpec(
    styleId: String,
): ContentTransform = pageTransform(
    style = TransitionStyle.fromId(styleId),
    movingForward = true,
)

/**
 * 返回转场：方向与进入相反。
 *
 * 预测性返回（`predictivePopTransitionSpec`）也复用本样式：
 * 手势驱动的进度由 spring 实时跟随，与普通返回保持视觉一致。
 */
private fun AnimatedContentTransitionScope<*>.popTransitionSpec(
    styleId: String,
): ContentTransform = pageTransform(
    style = TransitionStyle.fromId(styleId),
    movingForward = false,
)

/** 按样式组装一对「进入 + 退出」动画。 */
private fun pageTransform(
    style: TransitionStyle,
    movingForward: Boolean,
): ContentTransform = when (style) {
    TransitionStyle.Standard -> slideTransform(movingForward)
    TransitionStyle.Fade -> fadeTransform()
    TransitionStyle.Scale -> scaleTransform()
}

/** 标准：新页整屏横向滑入，旧页反向微移，形成层次感。 */
private fun slideTransform(movingForward: Boolean): ContentTransform {
    val enterOffsetX: (Int) -> Int = { full -> if (movingForward) full else -full / 4 }
    val exitOffsetX: (Int) -> Int = { full -> if (movingForward) -full / 4 else full }
    val enter = slideInHorizontally(pageMotion(), initialOffsetX = enterOffsetX) +
        fadeIn(pageMotion())
    val exit = slideOutHorizontally(pageMotion(), targetOffsetX = exitOffsetX) +
        fadeOut(pageMotion())
    return enter togetherWith exit
}

/** 淡入淡出：无位移，最轻的过渡。 */
private fun fadeTransform(): ContentTransform =
    fadeIn(pageMotion()) togetherWith fadeOut(pageMotion())

/** 缩放：从小放大进入，退出时缩回。 */
private fun scaleTransform(): ContentTransform {
    val enter = scaleIn(pageMotion(), initialScale = SCALE_FROM) + fadeIn(pageMotion())
    val exit = scaleOut(pageMotion(), targetScale = SCALE_FROM) + fadeOut(pageMotion())
    return enter togetherWith exit
}
