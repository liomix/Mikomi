package com.liomix.mikomi.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liomix.mikomi.feature.player.component.PlayerWindowEffect

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
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    LaunchedEffect(animeId, episodeId) {
        viewModel.bind(animeId, episodeId)
    }

    // 全屏是**页面自己的视图状态**，不进 PlayerUiState：
    // 它只影响布局约束与窗口，与业务无关；放进去会让选集网格跟着重组。
    // `rememberSaveable` 让进程被回收后重建时仍停在全屏。
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    // 播放状态单独持有：接 Media3 后由 Player.Listener 回写，UI 层不动。
    val playback = remember { PlayerPlaybackState.placeholder() }

    // 窗口副作用（系统栏 / 方向 / 刘海 / 常亮 / 返回）集中在一处，
    // 布局里不出现任何 Window 代码。
    PlayerWindowEffect(
        playback = playback,
        fullscreen = fullscreen,
        onExitFullscreen = { fullscreen = false },
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerScreen(
        state = state,
        playback = playback,
        fullscreen = fullscreen,
        // 全屏时左上角返回先退全屏：和系统返回键同一个语义，用户不会一点就直接退出播放页。
        onBackClick = {
            if (fullscreen) fullscreen = false else onBackClick()
        },
        onToggleFullscreen = { fullscreen = !fullscreen },
        onTabSelected = viewModel::onTabSelected,
        onOrderToggle = viewModel::onOrderToggle,
        onEpisodeClick = viewModel::onEpisodeSelected,
        onPreviousEpisode = viewModel::onPreviousEpisode,
        onNextEpisode = viewModel::onNextEpisode,
        onDanmakuToggle = viewModel::onDanmakuToggle,
        // TODO: 弹幕设置面板（透明度/字号/速度/显示区域）与播放器设置面板落地后接上。
        onDanmakuSettingsClick = {},
        // 剧集选择：全屏下内容区没组合，先退出全屏再切到选集 Tab——
        // 否则用户点了之后画面还在全屏，看不到自己被切走了。
        onEpisodesClick = {
            fullscreen = false
            viewModel.onTabSelected(PlayerTab.EPISODES)
        },
        onSourceClick = viewModel::onSourceClick,
        onLineSelected = viewModel::onLineSelected,
        onSourceSheetDismiss = viewModel::onSourceSheetDismiss,
        onRetrySource = viewModel::onRetrySource,
        onRetryLine = viewModel::onRetryLine,
        onRetryAllSources = viewModel::onRetryAllSources,
        onPlayerSettingsClick = {},
        modifier = modifier,
    )
}
