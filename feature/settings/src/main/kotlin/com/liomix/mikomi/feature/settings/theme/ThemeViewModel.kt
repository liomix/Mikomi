package com.liomix.mikomi.feature.settings.theme

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.designsystem.theme.ThemePalette
import com.liomix.mikomi.data.user.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 主题设置 ViewModel：读取/保存配色预设。 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
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

    private companion object {
        const val TAG = "ThemeViewModel"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
