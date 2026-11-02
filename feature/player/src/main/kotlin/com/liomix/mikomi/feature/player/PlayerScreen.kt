package com.liomix.mikomi.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.min
import com.liomix.mikomi.feature.player.component.PlaybackSourceSheet
import com.liomix.mikomi.feature.player.component.PlayerNavigationBar
import com.liomix.mikomi.feature.player.component.PlayerSurface
import com.liomix.mikomi.feature.player.component.CommentSection
import com.liomix.mikomi.feature.player.component.EpisodeSection

/** 播放器最大占比：横屏/大屏时不把整页吃掉，保证选集区始终可见。 */
private const val PLAYER_MAX_HEIGHT_RATIO = 0.45f

/**
 * 播放页（无状态）。
 *
 * 自上而下三段，互不牵连：
 * 1. 播放器：按 16:9 计算高度，并压到页面高度的 45% 以内（平板/横屏不至于只剩播放器）；
 * 2. Tab 栏 + 右侧操作区：固定；
 * 3. 内容区：选集网格 / 评论，占满剩余高度并各自滚动。
 *
 * 页面不自己铺底色、不处理状态栏：底色来自导航宿主，播放器自己顶到状态栏下方
 * （顶栏内部用状态栏 inset 让位，返回键不会压在状态栏里）。
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onBackClick: () -> Unit,
    onTabSelected: (PlayerTab) -> Unit,
    onOrderToggle: () -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
    onDanmakuToggle: () -> Unit,
    onDanmakuSettingsClick: () -> Unit,
    onSourceClick: () -> Unit,
    onLineSelected: (sourceId: String, lineId: String) -> Unit,
    onSourceSheetDismiss: () -> Unit,
    onRetrySource: (sourceId: String) -> Unit,
    onRetryLine: (sourceId: String, lineId: String) -> Unit,
    onRetryAllSources: () -> Unit,
    onPlayerSettingsClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedEpisodes = remember(state.episodes, state.order) { state.sortedEpisodes() }
    // 源列表不排序：Tab 顺序跟着数据源给的偏好顺序，状态只体现在 Tab 上的状态点。
    val sources = state.sources
    // 网格滚动位置提到页面层：切到评论再切回来不会回到顶部。
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

        Column(modifier = Modifier.fillMaxSize()) {
            PlayerSurface(
                title = state.animeTitle,
                subtitle = episodeLabel,
                height = playerHeight,
                onBackClick = onBackClick,
                onPlayerSettingsClick = onPlayerSettingsClick,
                onFullscreenClick = onFullscreenClick,
            )

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
                        gridState = episodeGridState,
                        onOrderToggle = onOrderToggle,
                        onEpisodeClick = onEpisodeClick,
                    )

                    PlayerTab.COMMENTS -> CommentSection()
                }
            }
        }

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
