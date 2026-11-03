package com.liomix.mikomi.feature.settings.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.PlayerPlaybackSettings
import com.liomix.mikomi.core.model.toPlayerPlaybackSettings
import com.liomix.mikomi.data.user.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 播放设置：默认倍速、长按倍速、左右滑动幅度。
 *
 * 三项都只是「写进偏好」，没有本地副本：DataStore 是唯一真相，写完之后
 * 这条 flow 会自己回流一次，选中态不需要在这里手动维护——手动维护就一定会出现
 * 「点了没变」或「写失败但界面已经是新值」两种偏差之一。
 */
@HiltViewModel
class PlaybackSettingsViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    val settings: StateFlow<PlayerPlaybackSettings> = userDataRepository.observeUserPreferences()
        .map { it.toPlayerPlaybackSettings() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerPlaybackSettings(),
        )

    fun selectDefaultSpeed(speed: Float) {
        update { userDataRepository.setPlayerDefaultSpeed(speed) }
    }

    fun selectLongPressSpeed(speed: Float) {
        update { userDataRepository.setPlayerLongPressSpeed(speed) }
    }

    fun selectSeekSweepSeconds(seconds: Int) {
        update { userDataRepository.setPlayerSeekSweepSeconds(seconds) }
    }

    /** 写失败不该让页面崩：这些设置写不进去，最坏是这次改动没生效。 */
    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Write playback setting failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "PlaybackSettingsVM"
    }
}
