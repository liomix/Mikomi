package com.liomix.mikomi.feature.settings.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 主题设置 Route（有状态）：收集配色与夜间模式，变更写入偏好。 */
@Composable
fun ThemeRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val colorSchemeId by viewModel.colorSchemeId.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val systemInDarkTheme = isSystemInDarkTheme()
    // system 模式下开关状态跟随系统，一旦拨动即固化为 light / dark。
    val isDarkMode = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemInDarkTheme
    }

    ThemeScreen(
        colorSchemeId = colorSchemeId,
        isDarkMode = isDarkMode,
        onSelectColorScheme = viewModel::selectColorScheme,
        onToggleDarkMode = viewModel::setDarkMode,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
