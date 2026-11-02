package com.liomix.mikomi.feature.settings.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 插件源设置页入口：把 ViewModel 的状态与事件接到 UI 上。 */
@Composable
fun PluginRoute(
    onBackClick: () -> Unit,
    onOpenStore: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PluginScreen(
        uiState = uiState,
        onToggle = viewModel::onToggle,
        onMoveUp = viewModel::onMoveUp,
        onMoveDown = viewModel::onMoveDown,
        onRemove = viewModel::onRemove,
        onMirrorSelected = viewModel::onMirrorSelected,
        onRepoUrlChanged = viewModel::onRepoUrlChanged,
        onOpenStore = onOpenStore,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
