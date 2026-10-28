package com.liomix.mikomi.feature.settings.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 网络设置页入口：把 ViewModel 的状态与事件接到 UI 上。 */
@Composable
fun NetworkRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NetworkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NetworkScreen(
        uiState = uiState,
        onSelectMode = viewModel::selectMode,
        onMirrorUrlChange = viewModel::updateMirrorUrl,
        onTestConnection = viewModel::testConnection,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
