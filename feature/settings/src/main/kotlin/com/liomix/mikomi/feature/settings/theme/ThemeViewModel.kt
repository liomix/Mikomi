package com.liomix.mikomi.feature.settings.theme

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.designsystem.theme.ThemePalette
import com.liomix.mikomi.data.user.UserDataRepository
import com.liomix.mikomi.feature.settings.R
import com.liomix.mikomi.feature.settings.appicon.AppIconManager
import com.liomix.mikomi.feature.settings.appicon.AppIconStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 主题页的一次性提示。
 *
 * @param messageRes 提示文案资源。
 * @param iconRes 可选的无底色图标，通常是切换后的应用图标。
 */
data class ThemeToast(
    @StringRes val messageRes: Int,
    @DrawableRes val iconRes: Int? = null,
)

/** 主题设置 ViewModel：配色、夜间模式、桌面图标。 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    /** 当前生效的配色 id（跟随 DataStore 实时变化）。 */
    val colorSchemeId: StateFlow<String> = userDataRepository.observeUserPreferences()
        .map { it.colorScheme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemePalette.Default.id,
        )

    /** 主题模式：`system` / `light` / `dark`。 */
    val themeMode: StateFlow<String> = userDataRepository.observeUserPreferences()
        .map { it.theme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = THEME_SYSTEM,
        )

    /** 当前桌面图标样式 id。 */
    val appIconId: StateFlow<String> = userDataRepository.observeUserPreferences()
        .map { it.appIcon }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppIconStyle.Default.id,
        )

    /** 一次性提示，由 UI 层用 [com.liomix.mikomi.core.ui.component.MikomiToast] 呈现。 */
    private val _toasts = MutableSharedFlow<ThemeToast>(extraBufferCapacity = 1)
    val toasts: SharedFlow<ThemeToast> = _toasts.asSharedFlow()

    fun selectColorScheme(id: String) {
        viewModelScope.launch {
            try {
                userDataRepository.setColorScheme(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Set color scheme failed, id=$id", e)
            }
        }
    }

    /** 切换夜间模式：开 → 深色，关 → 浅色（不再跟随系统）。 */
    fun setDarkMode(enabled: Boolean) {
        val mode = if (enabled) THEME_DARK else THEME_LIGHT
        viewModelScope.launch {
            try {
                userDataRepository.setTheme(mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Set theme mode failed, mode=$mode", e)
            }
        }
    }

    /**
     * 切换桌面图标：先让系统启用对应 alias，成功后再落库。
     *
     * 失败时保留原选择，并通过 [messages] 提示用户。
     */
    fun selectAppIcon(id: String) {
        val style = AppIconStyle.fromId(id)
        viewModelScope.launch {
            try {
                val applied = AppIconManager.apply(context, style)
                if (applied) {
                    userDataRepository.setAppIcon(style.id)
                    // 图标用「刚切换成的样式」：DataStore 是异步落库的，
                    // 等 appIconId 回流会让弹窗短暂显示旧图标。
                    _toasts.tryEmit(
                        ThemeToast(R.string.settings_app_icon_changed, style.glyphRes),
                    )
                } else {
                    _toasts.tryEmit(
                        ThemeToast(R.string.settings_app_icon_failed, style.glyphRes),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Select app icon failed, id=$id", e)
                _toasts.tryEmit(ThemeToast(R.string.settings_app_icon_failed, style.glyphRes))
            }
        }
    }

    private companion object {
        const val TAG = "ThemeViewModel"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
