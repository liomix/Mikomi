package com.liomix.mikomi.navigation

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
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
import com.liomix.mikomi.core.navigation.AniMapDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.HomeDestination
import com.liomix.mikomi.core.navigation.ImmersionBridge
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.topLevelDestinations

/** 进入巡礼页后自动隐藏底部导航栏的延时（毫秒）：不靠滑动判定，进页面 2s 后自动进入沉浸。 */
private const val NAV_BAR_AUTO_HIDE_DELAY_MILLIS = 2_000L

@Composable
fun MikomiNavHost(
    navigator: Navigator,
    entryProviderInstallers: Set<EntryProviderInstaller>,
    immersionBridge: ImmersionBridge,
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

    // 沉浸状态权威副本直接在 ImmersionBridge（进程级单例 MutableState），
    // 跨 entry 重建、跨 Activity 重建存活，且不依赖任何绑定时序。
    val immersiveRequested by immersionBridge.immersive
    val showNavBar by immersionBridge.navBarTemp

    // 切换顶级 tab 时无条件复位沉浸状态（包括切回 AniMap 本身）：
    // ImmersionBridge 是进程级单例，immersive/navBarTemp 跨页面存活，
    // 若切回 AniMap 时残留 true，导航栏会被瞬间隐藏（用户没滑动就进了沉浸）。
    // 复位后切回 AniMap 默认显示导航栏，只有用户再次滑动地图才重新进入沉浸。
    LaunchedEffect(currentTopLevel) {
        Log.d("AniMapNavHost", "LaunchedEffect(currentTopLevel=$currentTopLevel) resetting immersive state")
        immersionBridge.resetForTopLevelNavigation()
    }

    // 进入巡礼页 2s 后自动进入沉浸（隐藏底部导航栏）。
    // 计时器必须放在宿主层（MikomiNavHost 是进程级 scope，跨 entry 重建存活），
    // 不能放在 AniMapScreen entry 的 LaunchedEffect 里：切回瞬间 AndroidView factory
    // 重建 SurfaceWebViewContainer → 挂新 Presentation 窗口 → ViewRootImpl relayout /
    // ImeFocusController 焦点抢占会让 entry effect scope 被销毁重建，delay 协程被取消后
    // 键（immersive）未变不再重启，导致 "2s elapsed" 与 onImmersiveChange(true) 永不出现
    // （真机日志已证实：LaunchedEffect(immersive=false) entered 后 2s 无任何后续日志）。
    // 键用 currentTopLevel：只在真正切换顶级 tab 时重新起算 2s，进入沉浸后不会反复重启。
    LaunchedEffect(currentTopLevel) {
        if (currentTopLevel != AniMapDestination) return@LaunchedEffect
        Log.d("AniMapNavHost", "auto-hide timer: 2s countdown starting")
        kotlinx.coroutines.delay(NAV_BAR_AUTO_HIDE_DELAY_MILLIS)
        Log.d("AniMapNavHost", "auto-hide timer: 2s elapsed, immersive=${immersionBridge.immersive.value} navBarTemp=${immersionBridge.navBarTemp.value}")
        // 仅在用户未手动唤出导航栏时才自动进入沉浸：
        // 若用户在 2s 内已通过返回手势/手势条唤出导航栏（showNavBar=true），
        // 不应覆盖用户的显式操作。
        if (!immersionBridge.navBarTemp.value) {
            Log.d("AniMapNavHost", "auto-hide timer: entering immersive")
            immersionBridge.onImmersiveChange(true)
        } else {
            Log.d("AniMapNavHost", "auto-hide timer: navBarTemp=true, skip auto-hide")
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
            WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Nav3 根屏（backStack.size == 1）返回事件兜底：
    // navigation3 的 NavDisplay 内部用 NavigationBackHandler(isBackEnabled = previousEntries.isNotEmpty())
    // 禁用了根屏的返回事件，导致 NavDisplay.onBack 在单 entry 时永不触发。
    // 这里挂一个 BackHandler 接住返回，实现"返回手势唤出底部导航栏"。
    // 仅当返回栈只剩根屏（无可弹栈 entry）时启用，避免与 NavDisplay.onBack 抢占返回栈弹出。
    val isRootScreen = navigator.backStack.size <= 1
    BackHandler(enabled = isRootScreen) {
        // 返回唤出导航栏：只写 navBarTemp=true，不写 onImmersiveChange(false)。
        // 保持 immersive=true（沉浸态仍在），navBarTemp 收起后 AniMapScreen 会重新进入沉浸。
        immersionBridge.onNavBarTempChange(true)
    }

    // 返回事件在宿主层统一处理：页面内的 BackHandler 会被 NavDisplay 抢先消费，
    // 所以唤出导航栏的逻辑必须放这里。只要导航栏未唤出就退出沉浸并唤出，
    // 已唤出且可回退时再回退 WebView，否则交由 navigator 弹栈。
    val navBackHandler: () -> Unit = {
        if (!showNavBar) {
            // 返回唤出导航栏：只写 navBarTemp=true，不写 onImmersiveChange(false)。
            // 保持 immersive=true（沉浸态仍在），navBarTemp 收起后 AniMapScreen 会重新进入沉浸。
            immersionBridge.onNavBarTempChange(true)
        } else {
            navigator.goBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = navigator.backStack,
            onBack = navBackHandler,
            entryProvider = navEntryProvider,
        )

        // 沉浸式下隐藏底部导航栏，滑入/滑出动画；其余页面正常显示。
        // 沉浸中可通过手势条临时唤出（showNavBar），2.5s 后自动收起。
        AnimatedVisibility(
            visible = !immersiveRequested || showNavBar,
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
