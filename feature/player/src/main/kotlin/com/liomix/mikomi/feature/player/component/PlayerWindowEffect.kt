package com.liomix.mikomi.feature.player.component

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.liomix.mikomi.feature.player.PlayerPlaybackState
import kotlinx.coroutines.flow.distinctUntilChanged

/** 单边 ≥ 600dp 视为大屏：全屏不再强制横屏。 */
private const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600

/**
 * 播放页的窗口副作用——整个 App 里唯一碰 Window 的播放器代码。
 *
 * 放在这里而不是塞进布局里，是因为这些效果**不属于任何一块 UI**：
 * 系统栏显隐、屏幕方向、刘海、常亮、返回键，全部是窗口级状态，
 * 而 `PlayerSurface` 只管画面与控制层长什么样。
 *
 * 全屏走「同一个 Activity、同一个 window，只换布局约束」的路线：
 * 新开 Activity / Dialog / 新路由都会换 window，ExoPlayer 的 `Surface` 必须搬家，
 * 表现为黑一帧甚至重新初始化解码器。本项目 manifest 已声明 `configChanges`
 * 含 `orientation|screenSize`，所以强制横屏也不会重建 Activity。
 *
 * 还原策略是**快照**而不是猜默认值：进页面时把窗口原状拍下来，离开时逐项写回。
 * 用户可能本来就把系统屏幕固定成横屏，猜默认值会把这个设置改坏。
 */
@Composable
internal fun PlayerWindowEffect(
    playback: PlayerPlaybackState,
    fullscreen: Boolean,
    /**
     * `true` 表示画面不归 Media3 管（WebView 形态），此时 Media3 的 `isPlaying` 恒为 false，
     * 不能拿它当常亮依据，否则看着看着就息屏。
     */
    webMode: Boolean = false,
    onExitFullscreen: () -> Unit,
) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    val window = activity?.window
    val controller = remember(window, view) {
        window?.let { WindowCompat.getInsetsController(it, view) }
    }

    val origin = remember(window, controller, activity) {
        WindowOrigin(
            lightStatusBars = controller?.isAppearanceLightStatusBars ?: false,
            orientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            keepScreenOn = view.keepScreenOn,
            cutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window?.attributes?.layoutInDisplayCutoutMode
                    ?: WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            },
        )
    }

    // 页面级：进入即切成「深色画面」的窗口外观，离开时还原。
    DisposableEffect(window, controller, activity) {
        // 画面是黑底，日间主题默认的深色状态栏图标在上面看不见。
        controller?.isAppearanceLightStatusBars = false
        // 横屏全屏时画面要铺到刘海两侧，否则会留一条黑边。
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        onDispose {
            controller?.isAppearanceLightStatusBars = origin.lightStatusBars
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = origin.orientation
            view.keepScreenOn = origin.keepScreenOn
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = origin.cutoutMode
                }
            }
        }
    }

    LaunchedEffect(activity, window, view, fullscreen, origin) {
        if (activity == null || window == null) return@LaunchedEffect
        val insets = WindowCompat.getInsetsController(window, view)
        if (fullscreen) {
            // 手势条下滑可以临时唤出系统栏，几秒后自动再收起。
            insets.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = activity.fullscreenOrientation()
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = origin.orientation
        }
    }

    // 常亮只跟「是否在播放」：暂停时该暗就暗，别把一集看完的手机一直点亮着。
    // 用 snapshotFlow 读 isPlaying，避免把每帧可能变化的播放状态读进组合。
    // WebView 形态下播放由站点播放器掌握，Media3 不在播，只能整体常亮——
    // 用户在看片，页面在后台跑 JS，这时候息屏比白白亮着更糟。
    LaunchedEffect(view, playback, webMode) {
        if (webMode) {
            view.keepScreenOn = true
            return@LaunchedEffect
        }
        snapshotFlow { playback.isPlaying }
            .distinctUntilChanged()
            .collect { playing -> view.keepScreenOn = playing }
    }

    // 全屏时返回先退全屏再退页面；只在全屏期间注册，不抢页面级的返回处理。
    BackHandler(enabled = fullscreen, onBack = onExitFullscreen)
}

/**
 * 全屏时的屏幕方向。
 *
 * - 手机：跟随传感器横屏。manifest 已声明 `configChanges`，转屏不会重建 Activity；
 * - 大屏 / 折叠展开 / 分屏：**不动方向**，只把画面铺满窗口 ——
 *   平板上强制横屏是反模式，多窗口下系统也会直接忽略这个请求。
 */
private fun Activity.fullscreenOrientation(): Int {
    val largeScreen =
        resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP
    return if (largeScreen || isInMultiWindowMode) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

/** 进入播放页时的窗口原状。 */
private data class WindowOrigin(
    val lightStatusBars: Boolean,
    val orientation: Int,
    val keepScreenOn: Boolean,
    val cutoutMode: Int,
)
