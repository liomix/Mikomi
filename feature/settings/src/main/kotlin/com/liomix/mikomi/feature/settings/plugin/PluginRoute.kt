package com.liomix.mikomi.feature.settings.plugin

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liomix.mikomi.feature.settings.R

/**
 * 插件源设置页入口：把 ViewModel 的状态与事件接到 UI 上。
 *
 * 分享是这里唯一需要 Context 的动作——ViewModel 不该碰 Context，所以流程拆成两半：
 * `onShare` 只把规则原文放进 `pendingShare`，本页读到后拉起系统分享面板，再把状态清空。
 */
@Composable
fun PluginRoute(
    onBackClick: () -> Unit,
    onOpenStore: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.settings_plugin_share_chooser)

    LaunchedEffect(uiState.pendingShare) {
        val share = uiState.pendingShare ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            // 主题给规则名，正文给整份 JSON：对方存成 `<name>.json` 就能直接用。
            putExtra(Intent.EXTRA_SUBJECT, share.name)
            putExtra(Intent.EXTRA_TEXT, share.text)
        }
        try {
            context.startActivity(Intent.createChooser(send, chooserTitle))
        } catch (e: Exception) {
            // 没有可分享的应用时别把页面崩掉：拿不到面板就只是这次分享没成。
            Log.w(TAG, "Launch share chooser failed", e)
        }
        viewModel.onShareHandled()
    }

    PluginScreen(
        uiState = uiState,
        onToggle = viewModel::onToggle,
        onMoveUp = viewModel::onMoveUp,
        onMoveDown = viewModel::onMoveDown,
        onRemove = viewModel::onRemove,
        onEditOpen = viewModel::onEditOpen,
        onEditorTextChange = viewModel::onEditorTextChange,
        onEditorFormat = viewModel::onEditorFormat,
        onEditorSave = viewModel::onEditorSave,
        onEditorDismiss = viewModel::onEditorDismiss,
        onTestOpen = viewModel::onTestOpen,
        onTestRun = viewModel::onTestRun,
        onTestDismiss = viewModel::onTestDismiss,
        onShare = viewModel::onShare,
        onMirrorSelected = viewModel::onMirrorSelected,
        onRepoUrlChanged = viewModel::onRepoUrlChanged,
        onOpenStore = onOpenStore,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

private const val TAG = "PluginRoute"
