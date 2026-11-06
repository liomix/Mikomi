package com.liomix.mikomi.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liomix.mikomi.feature.player.component.PlayerWindowEffect

/**
 * 播放页 Route（有状态）。
 *
 * 参数绑定走 `LaunchedEffect(animeId, episodeId)`：`bind()` 会重新探测线路、重置选集，
 * 它是「进入这一页」的动作而不是「每次重组都做一遍」的动作。
 *
 * ViewModel 现在是 **per-entry** 的（`MikomiNavHost` 挂了
 * `rememberViewModelStoreNavEntryDecorator`）：返回时 entry 销毁，VM 跟着 `onCleared()`，
 * 不同番的播放器实例互不共享。此前是 Activity 级实例，两个页面只能靠 `bind()` 手工改绑。
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

    // 退出页面立刻静音：entry 的 ViewModel 要等退场动画跑完才 `onCleared()`，
    // 那一两百毫秒里声音还在响。这里先手动暂停，`onCleared()` 里的 release 是后手。
    DisposableEffect(viewModel) {
        onDispose { viewModel.onLeavePage() }
    }

    // 全屏是**页面自己的视图状态**，不进 PlayerUiState：
    // 它只影响布局约束与窗口，与业务无关；放进去会让选集网格跟着重组。
    // `rememberSaveable` 让进程被回收后重建时仍停在全屏。
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    // 播放状态由 ViewModel 持有：Media3 的 listener 往它里面写，UI 只读。
    // 放在 Route 里 remember 是不行的——换集后 listener 还在写旧对象，进度就冻住了。
    val playback = viewModel.playback

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 窗口副作用（系统栏 / 方向 / 刘海 / 常亮 / 返回）集中在一处，
    // 布局里不出现任何 Window 代码。
    // webMode 影响常亮策略：WebView 形态下 Media3 的 isPlaying 恒 false，不能作依据。
    PlayerWindowEffect(
        playback = playback,
        fullscreen = fullscreen,
        webMode = state.mode == PlaybackMode.WEB,
        onExitFullscreen = { fullscreen = false },
    )

    PlayerScreen(
        state = state,
        mediaPlayer = viewModel.player,
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
        // 重试 = 重新出流：这类站点的播放地址寿命很短，对同一个地址再 prepare 一次只会再失败。
        onRetryPlayback = viewModel::retryPlayback,
        // 站点播放器自己点了全屏：跟着切（全屏是同一个 window 换约束，不新开页面）。
        onRequestFullscreen = { want -> if (fullscreen != want) fullscreen = want },
        // WebView 在网络层拦到 `.m3u8` / `Range` 媒体请求，或者页面自己报了地址：接过来交给原生播放器。
        onWebSubRequest = viewModel::onWebSubRequest,
        // 失败后的出口：用户明确要求时才把播放页交出去，绝不由我们自己把网页推给他。
        onPlayInWebPage = viewModel::onPlayInWebPage,
        modifier = modifier,
    )
}
