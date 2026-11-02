package com.liomix.mikomi.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 播放页 Route（有状态）。
 *
 * 参数绑定走 `LaunchedEffect(animeId, episodeId)`：本项目的 `NavDisplay` 没有
 * per-entry 的 ViewModelStore，`hiltViewModel()` 拿到的是 Activity 级实例，
 * 直接在组合里调 `bind` 会在每次重组时重复执行。
 */
@Composable
fun PlayerRoute(
    animeId: String,
    episodeId: String,
    onBackClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    LaunchedEffect(animeId, episodeId) {
        viewModel.bind(animeId, episodeId)
    }

    // 播放器顶到状态栏下方，画面是深色的：日间主题下系统默认的深色状态栏图标会看不见。
    DarkStatusBarIcons()

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerScreen(
        state = state,
        onBackClick = onBackClick,
        onTabSelected = viewModel::onTabSelected,
        onOrderToggle = viewModel::onOrderToggle,
        onEpisodeClick = viewModel::onEpisodeSelected,
        onDanmakuToggle = viewModel::onDanmakuToggle,
        // TODO: 弹幕设置面板（透明度/字号/速度/显示区域）与播放器设置面板落地后接上。
        onDanmakuSettingsClick = {},
        onSourceClick = viewModel::onSourceClick,
        onLineSelected = viewModel::onLineSelected,
        onSourceSheetDismiss = viewModel::onSourceSheetDismiss,
        onRetrySource = viewModel::onRetrySource,
        onRetryLine = viewModel::onRetryLine,
        onRetryAllSources = viewModel::onRetryAllSources,
        onPlayerSettingsClick = {},
        onFullscreenClick = onFullscreenClick,
        modifier = modifier,
    )
}

/** 进入播放页时把状态栏图标切成浅色，离开时恢复原样。 */
@Composable
private fun DarkStatusBarIcons() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()

    DisposableEffect(view, activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller == null) {
            onDispose { }
        } else {
            val wasLight = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false
            onDispose { controller.isAppearanceLightStatusBars = wasLight }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
