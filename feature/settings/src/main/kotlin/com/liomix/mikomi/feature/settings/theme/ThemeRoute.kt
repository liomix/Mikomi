package com.liomix.mikomi.feature.settings.theme

import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 主题设置 Route（有状态）：收集配色 / 夜间模式 / 桌面图标，变更写入偏好。 */
@Composable
fun ThemeRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val colorSchemeId by viewModel.colorSchemeId.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appIconId by viewModel.appIconId.collectAsStateWithLifecycle()
    val systemInDarkTheme = isSystemInDarkTheme()
    // system 模式下开关状态跟随系统，一旦拨动即固化为 light / dark。
    val isDarkMode = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemInDarkTheme
    }

    // 图标切换由系统组件状态决定，结果用 Toast 告知（桌面刷新有延迟）。
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { messageRes ->
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    ThemeScreen(
        colorSchemeId = colorSchemeId,
        isDarkMode = isDarkMode,
        appIconId = appIconId,
        onSelectColorScheme = viewModel::selectColorScheme,
        onToggleDarkMode = viewModel::setDarkMode,
        onSelectAppIcon = viewModel::selectAppIcon,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
