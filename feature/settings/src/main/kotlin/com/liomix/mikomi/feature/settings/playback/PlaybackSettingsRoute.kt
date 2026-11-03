package com.liomix.mikomi.feature.settings.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 播放设置页入口：把偏好流接到 UI 上。 */
@Composable
fun PlaybackSettingsRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaybackSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    PlaybackSettingsScreen(
        settings = settings,
        onSelectDefaultSpeed = viewModel::selectDefaultSpeed,
        onSelectLongPressSpeed = viewModel::selectLongPressSpeed,
        onSelectSeekSweepSeconds = viewModel::selectSeekSweepSeconds,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
