package com.liomix.mikomi.feature.settings.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liomix.mikomi.core.ui.component.MikomiToastData
import kotlinx.coroutines.delay

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
    val transitionStyleId by viewModel.transitionStyleId.collectAsStateWithLifecycle()
    val pageTransitionStyleId by viewModel.pageTransitionStyleId.collectAsStateWithLifecycle()
    val systemInDarkTheme = isSystemInDarkTheme()
    // system 模式下开关状态跟随系统，一旦拨动即固化为 light / dark。
    val isDarkMode = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemInDarkTheme
    }

    // 提示弹窗：文案在 UI 层解析；图标由 ViewModel 给出（切换后的无底色图标）。
    val context = LocalContext.current
    var toast by remember { mutableStateOf<MikomiToastData?>(null) }
    // 序号变化即重新计时：连续两次相同提示也能完整显示一次。
    var toastSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.toasts.collect { event ->
            toast = MikomiToastData(
                message = context.getString(event.messageRes),
                iconRes = event.iconRes,
            )
            toastSeq++
        }
    }
    LaunchedEffect(toastSeq) {
        if (toastSeq > 0) {
            delay(TOAST_VISIBLE_MILLIS)
            toast = null
        }
    }

    ThemeScreen(
        colorSchemeId = colorSchemeId,
        isDarkMode = isDarkMode,
        appIconId = appIconId,
        transitionStyleId = transitionStyleId,
        pageTransitionStyleId = pageTransitionStyleId,
        toast = toast,
        onSelectColorScheme = viewModel::selectColorScheme,
        onToggleDarkMode = viewModel::setDarkMode,
        onSelectAppIcon = viewModel::selectAppIcon,
        onSelectTransitionStyle = viewModel::selectTransitionStyle,
        onSelectPageTransitionStyle = viewModel::selectPageTransitionStyle,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

/** 提示弹窗停留时长（够读完一句提示，又不至于遮挡页面）。 */
private const val TOAST_VISIBLE_MILLIS = 2_600L
