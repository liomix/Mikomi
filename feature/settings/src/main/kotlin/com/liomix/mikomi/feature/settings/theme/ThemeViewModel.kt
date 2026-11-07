package com.liomix.mikomi.feature.settings.theme

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.designsystem.theme.ThemePalette
import com.liomix.mikomi.core.model.PageTransitionStyle
import com.liomix.mikomi.core.model.TransitionStyle
import com.liomix.mikomi.data.user.UserDataRepository
import com.liomix.mikomi.feature.settings.R
import com.liomix.mikomi.feature.settings.theme.appicon.AppIconManager
import com.liomix.mikomi.feature.settings.theme.appicon.AppIconStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
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

/**
 * 主题页状态。
 *
 * 四个字段全来自同一份 [com.liomix.mikomi.core.model.UserPreferences]，
 * 所以由一条流一次性给出，而不是让 UI 层分别收集四条流（那会出现「配色已新、图标还旧」的中间帧）。
 */
data class ThemeUiState(
    val colorSchemeId: String = ThemePalette.Default.id,
    val theme: String = "system",
    val appIconId: String = AppIconStyle.Default.id,
    val transitionStyleId: String = TransitionStyle.Translate.id,
    val pageTransitionId: String = PageTransitionStyle.Push.id,
)

/** 主题设置 ViewModel：配色、夜间模式、桌面图标、封面动效。 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    /**
     * 主题页的全部可调项。
     *
     * 四条偏好共用一条上游流、一次 `stateIn`：原先每条各自 `map + stateIn`，
     * 等于把同一份 DataStore 订阅了四遍（四份缓存、四个启动协程、四次重组入口），
     * 而它们本来就是同一个 `UserPreferences` 的四个字段。
     */
    val uiState: StateFlow<ThemeUiState> = userDataRepository.observeUserPreferences()
        .map { prefs ->
            ThemeUiState(
                colorSchemeId = prefs.colorScheme,
                theme = prefs.theme,
                appIconId = prefs.appIcon,
                transitionStyleId = prefs.transitionStyle,
                pageTransitionId = prefs.pageTransition,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeUiState(),
        )

    /**
     * 一次性提示，由 UI 层用 [com.liomix.mikomi.core.ui.component.base.MikomiToast] 呈现。
     *
     * 用 `Channel(CONFLATED)` 而不是 `MutableSharedFlow`：SharedFlow 是「广播给当前订阅者」，
     * `replay = 0` 时**没有订阅者就直接丢弃**（`tryEmit` 还会返回 true，静默丢）。
     * 切桌面图标要等系统弹确认框，用户切完往往已经退回上一页 —— ThemeRoute 的 collector
     * 那时已经不在了，提示就这么没了，表现出来是「点了没反应」。
     *
     * Channel 反过来：没人收就先存着（容量 1），订阅建立后补投一次。
     * `CONFLATED` 而不是 `BUFFERED`：连点三次只该看到最后一条提示，不是排三条队依次弹。
     */
    private val _toasts = Channel<ThemeToast>(Channel.CONFLATED)
    val toasts: Flow<ThemeToast> = _toasts.receiveAsFlow()

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

    /** 选择封面动效。 */
    fun selectTransitionStyle(id: String) {
        viewModelScope.launch {
            try {
                userDataRepository.setTransitionStyle(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Set transition style failed, id=$id", e)
            }
        }
    }

    /** 选择页面动效。 */
    fun selectPageTransition(id: String) {
        viewModelScope.launch {
            try {
                userDataRepository.setPageTransition(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Set page transition failed, id=$id", e)
            }
        }
    }

    /**
     * 切换桌面图标：先让系统启用对应 alias，成功后再落库。
     *
     * 失败时保留原选择，并通过 [toasts] 提示用户。
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
                    _toasts.trySend(
                        ThemeToast(R.string.settings_app_icon_changed, style.glyphRes),
                    )
                } else {
                    _toasts.trySend(
                        ThemeToast(R.string.settings_app_icon_failed, style.glyphRes),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Select app icon failed, id=$id", e)
                _toasts.trySend(ThemeToast(R.string.settings_app_icon_failed, style.glyphRes))
            }
        }
    }

    private companion object {
        const val TAG = "ThemeViewModel"
    }
}
