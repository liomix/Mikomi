package com.liomix.mikomi.feature.settings.proxy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Bangumi 代理设置页入口：把 ViewModel 的状态与事件接到 UI 上。 */
@Composable
fun ProxyRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProxyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProxyScreen(
        uiState = uiState,
        onSelectMode = viewModel::selectMode,
        onMirrorUrlChange = viewModel::updateMirrorUrl,
        onTestConnection = viewModel::testConnection,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
