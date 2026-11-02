package com.liomix.mikomi.feature.settings.plugin.store

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 插件商店页入口：把 ViewModel 的状态与事件接到 UI 上。 */
@Composable
fun PluginStoreRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginStoreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PluginStoreScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onSortChange = viewModel::onSortChange,
        onRefresh = viewModel::onRefresh,
        onInstall = viewModel::onInstall,
        onMessageShown = viewModel::onMessageShown,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
