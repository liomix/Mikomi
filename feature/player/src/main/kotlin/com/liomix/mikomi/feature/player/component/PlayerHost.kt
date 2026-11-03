package com.liomix.mikomi.feature.player.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.liomix.mikomi.core.media.MikomiPlayer
import com.liomix.mikomi.feature.player.PlaybackMode
import com.liomix.mikomi.feature.player.PlayerFailure
import com.liomix.mikomi.feature.player.PlayerPlaybackState

/**
 * 播放器宿主：**整个 App 里唯一组合 [PlayerSurface] 的地方**。
 *
 * 它只做一件事——决定画面占多大、放在哪，不关心画面内部长什么样。
 * 全屏与非全屏的差别仅在于这里给出的约束（`fillMaxSize` vs `宽 × 16:9 且不超过页高 45%`），
 * 组件树的位置、父节点、key 都不变。
 *
 * 这条约束必须守住，原因不是审美：
 * 播放器的画面是绑定在 window 上的 Surface，一旦换 window（新 Activity / Dialog）、
 * 换父节点（Compose 里等价于销毁重建）、或在两个位置各组合一次（`if/else` 各写一份），
 * Surface 就会重建，表现为切全屏时黑一帧，严重时解码器重新初始化。
 * 所以这里刻意保持**单个调用点**，全屏只是"同一块画面长成另一个尺寸"。
 */
@Composable
internal fun PlayerHost(
    fullscreen: Boolean,
    compactHeight: Dp,
    title: String,
    subtitle: String,
    mediaPlayer: MikomiPlayer?,
    playback: PlayerPlaybackState,
    loading: Boolean,
    failure: PlayerFailure?,
    mode: PlaybackMode,
    webUrl: String?,
    webReloadToken: Int,
    webUserAgent: String?,
    sniffUrl: String?,
    sniffUserAgent: String?,
    webPageAvailable: Boolean,
    onRetry: () -> Unit,
    onRequestFullscreen: (Boolean) -> Unit,
    onWebSubRequest: (pageKey: String, url: String, headers: Map<String, String>, cookie: String?, trusted: Boolean) -> Unit,
    onPlayInWebPage: () -> Unit,
    danmakuEnabled: Boolean,
    canPlayPrevious: Boolean,
    canPlayNext: Boolean,
    onBackClick: () -> Unit,
    onPlayerSettingsClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onDanmakuToggle: () -> Unit,
    onDanmakuSettingsClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSurface(
        title = title,
        subtitle = subtitle,
        fullscreen = fullscreen,
        mediaPlayer = mediaPlayer,
        playback = playback,
        loading = loading,
        failure = failure,
        mode = mode,
        webUrl = webUrl,
        webReloadToken = webReloadToken,
        webUserAgent = webUserAgent,
        sniffUrl = sniffUrl,
        sniffUserAgent = sniffUserAgent,
        webPageAvailable = webPageAvailable,
        onRetry = onRetry,
        onRequestFullscreen = onRequestFullscreen,
        onWebSubRequest = onWebSubRequest,
        onPlayInWebPage = onPlayInWebPage,
        danmakuEnabled = danmakuEnabled,
        canPlayPrevious = canPlayPrevious,
        canPlayNext = canPlayNext,
        onBackClick = onBackClick,
        onPlayerSettingsClick = onPlayerSettingsClick,
        onToggleFullscreen = onToggleFullscreen,
        onPreviousEpisode = onPreviousEpisode,
        onNextEpisode = onNextEpisode,
        onDanmakuToggle = onDanmakuToggle,
        onDanmakuSettingsClick = onDanmakuSettingsClick,
        onEpisodesClick = onEpisodesClick,
        // 只改约束，不换位置：非全屏是「一屏宽 × 计算高度」，全屏铺满窗口。
        modifier = if (fullscreen) {
            modifier.fillMaxSize()
        } else {
            modifier
                .fillMaxWidth()
                .height(compactHeight)
        },
    )
}
