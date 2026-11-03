package com.liomix.mikomi.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.min
import com.liomix.mikomi.core.media.MikomiPlayer
import com.liomix.mikomi.feature.player.component.PlaybackSourceSheet
import com.liomix.mikomi.feature.player.component.PlayerHost
import com.liomix.mikomi.feature.player.component.PlayerNavigationBar
import com.liomix.mikomi.feature.player.component.CommentSection
import com.liomix.mikomi.feature.player.component.EpisodeSection

/** 播放器最大占比：横屏/大屏时不把整页吃掉，保证选集区始终可见。 */
private const val PLAYER_MAX_HEIGHT_RATIO = 0.45f

/**
 * 播放页（无状态）。
 *
 * 三段式，自下而上互不牵连：
 * 1. 播放器：按 16:9 计算高度并压到页高的 45% 以内；
 * 2. Tab 栏 + 右侧操作区：固定；
 * 3. 内容区：选集网格 / 评论，占满剩余高度并各自滚动。
 *
 * 播放器用 `Box` 叠在内容之上（而不是作为 `Column` 的第一段），是为了让全屏只改约束：
 * 同一个 [PlayerHost] 调用点，非全屏占「一屏宽 × 计算高度」，全屏 `fillMaxSize()`。
 * 内容区在非全屏时用等高的 `Spacer` 让出这块位置——视觉完全一致，
 * 但组件树里播放器的父节点从头到尾没变过（换父节点 = 销毁重建 = Surface 重建 = 黑一帧）。
 *
 * 全屏时下方内容整体不组合：既省掉选集网格的测量与绘制，也避免它在后台抢焦点/滚动。
 * 网格滚动位置提在页面层（`rememberLazyGridState`），退出全屏后位置不丢。
 *
 * 页面不自己铺底色、不处理系统栏：底色来自导航宿主，播放器自己顶到状态栏下方。
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    mediaPlayer: MikomiPlayer,
    playback: PlayerPlaybackState,
    fullscreen: Boolean,
    onBackClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onTabSelected: (PlayerTab) -> Unit,
    onOrderToggle: () -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
    onDanmakuToggle: () -> Unit,
    onDanmakuSettingsClick: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onEpisodesClick: () -> Unit,
    onSourceClick: () -> Unit,
    onLineSelected: (sourceId: String, lineId: String) -> Unit,
    onSourceSheetDismiss: () -> Unit,
    onRetrySource: (sourceId: String) -> Unit,
    onRetryLine: (sourceId: String, lineId: String) -> Unit,
    onRetryAllSources: () -> Unit,
    onPlayerSettingsClick: () -> Unit,
    onRetryPlayback: () -> Unit,
    onRequestFullscreen: (Boolean) -> Unit,
    onWebSubRequest: (pageKey: String, url: String, headers: Map<String, String>, cookie: String?, trusted: Boolean) -> Unit,
    /** 失败之后的出口：把播放页交给站点播放器（页面就是画面，控制权也交给它）。 */
    onPlayInWebPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedEpisodes = remember(state.episodes, state.order) { state.sortedEpisodes() }
    // 源列表不排序：Tab 顺序跟着数据源给的偏好顺序，状态只体现在 Tab 上的状态点。
    val sources = state.sources
    // 网格滚动位置提到页面层：切到评论再切回来不会回到顶部，进出全屏也不丢。
    val episodeGridState = rememberLazyGridState()
    val currentEpisode = state.currentEpisode
    val episodeTitle = currentEpisode
        ?.let { stringResource(R.string.player_episode_title, it.number) }
        .orEmpty()
    val episodeLabel = listOf(episodeTitle, currentEpisode?.subtitle.orEmpty())
        .filter { it.isNotBlank() }
        .joinToString(" ")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val playerHeight = min(maxWidth * 9f / 16f, maxHeight * PLAYER_MAX_HEIGHT_RATIO)

        if (!fullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 播放器从**状态栏下方**开始。
                    // App 是 edge-to-edge，内容默认铺到屏幕顶端，状态栏的时钟/信号图标
                    // 会压在第一帧画面上；这里让出那一条，全屏时才铺到屏幕边缘。
                    .statusBarsPadding(),
            ) {
                // 给播放器让出等高的一块：全屏与非全屏共用同一个播放器实例。
                Spacer(modifier = Modifier.height(playerHeight))

                PlayerNavigationBar(
                    selectedTab = state.tab,
                    danmakuEnabled = state.danmakuEnabled,
                    sourceLabel = state.sourceLabel,
                    onTabSelected = onTabSelected,
                    onDanmakuToggle = onDanmakuToggle,
                    onDanmakuSettingsClick = onDanmakuSettingsClick,
                    onSourceClick = onSourceClick,
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )

                Crossfade(
                    targetState = state.tab,
                    modifier = Modifier.weight(1f),
                    animationSpec = tween(durationMillis = 180),
                    label = "playerTabContent",
                ) { tab ->
                    when (tab) {
                        PlayerTab.EPISODES -> EpisodeSection(
                            episodes = orderedEpisodes,
                            selectedEpisodeId = currentEpisode?.id,
                            order = state.order,
                            // 交给选集区自己判断「剧集到底到没到」（见 EpisodeSection 里的 skeleton）。
                            loading = state.loading,
                            gridState = episodeGridState,
                            onOrderToggle = onOrderToggle,
                            onEpisodeClick = onEpisodeClick,
                        )

                        PlayerTab.COMMENTS -> CommentSection()
                    }
                }
            }
        }

        PlayerHost(
            fullscreen = fullscreen,
            compactHeight = playerHeight,
            title = state.animeTitle,
            subtitle = episodeLabel,
            mediaPlayer = mediaPlayer,
            playback = playback,
            loading = state.loading,
            failure = state.failure,
            mode = state.mode,
            webUrl = state.webUrl,
            webReloadToken = state.webReloadToken,
            webUserAgent = state.webUserAgent,
            sniffUrl = state.sniffUrl,
            sniffUserAgent = state.sniffUserAgent,
            webPageAvailable = state.webPageAvailable,
            onRetry = onRetryPlayback,
            onRequestFullscreen = onRequestFullscreen,
            onWebSubRequest = onWebSubRequest,
            onPlayInWebPage = onPlayInWebPage,
            danmakuEnabled = state.danmakuEnabled,
            // 上下集的可用性由状态算，播放器只管置灰：首集/末集按钮不该看起来能点。
            canPlayPrevious = state.previousEpisode != null,
            canPlayNext = state.nextEpisode != null,
            onBackClick = onBackClick,
            onPlayerSettingsClick = onPlayerSettingsClick,
            onToggleFullscreen = onToggleFullscreen,
            onPreviousEpisode = onPreviousEpisode,
            onNextEpisode = onNextEpisode,
            onDanmakuToggle = onDanmakuToggle,
            onDanmakuSettingsClick = onDanmakuSettingsClick,
            onEpisodesClick = onEpisodesClick,
            settings = state.playbackSettings,
            // 与上面 Column 里那块等高的 `Spacer` 对齐：两者都必须让出状态栏那一条，
            // 否则播放器会盖住选集区的第一行，或者反过来浮到状态栏底下。
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(if (fullscreen) Modifier else Modifier.statusBarsPadding()),
        )

        if (state.sourceSheetVisible) {
            PlaybackSourceSheet(
                animeTitle = state.animeTitle,
                // 弹窗只报「番名 · 第N集」：换源时用户确认的是「给哪一集换」，
                // 小标题在这里既占宽度又帮不上判断，留给播放器顶栏。
                episodeLabel = episodeTitle,
                sources = sources,
                selectedSourceId = state.selectedSourceId,
                onLineSelected = onLineSelected,
                onRetrySource = onRetrySource,
                onRetryLine = onRetryLine,
                onRetryAll = onRetryAllSources,
                onDismiss = onSourceSheetDismiss,
            )
        }
    }
}
