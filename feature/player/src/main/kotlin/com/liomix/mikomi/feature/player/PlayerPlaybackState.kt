package com.liomix.mikomi.feature.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 播放层状态：进度、时长、缓冲、播放中。
 *
 * 单独成一个 holder、**不进 [PlayerUiState]**：这些值播放时每帧都在变，
 * 混进页面状态会让整页（含选集网格）跟着重组。
 *
 * 读取路径刻意分成三条，代价各不相同：
 * - 进度条 / 缓冲条在**绘制阶段**读（`Canvas` 的 onDraw），只失效绘制；
 * - 时间文字先 `derivedStateOf` 压成「秒」，一秒最多重组一次那个 `Text`；
 * - 播放/暂停图标单独一个 composable 读 [isPlaying]，切换时不牵连底栏其它部分。
 *
 * 接 Media3 时由 Player.Listener 回写这几个字段即可，UI 一行不用改。
 */
@Stable
class PlayerPlaybackState(
    positionMillis: Long = 0L,
    durationMillis: Long = 0L,
) {
    var isPlaying by mutableStateOf(false)

    var positionMillis by mutableLongStateOf(positionMillis)

    var durationMillis by mutableLongStateOf(durationMillis)

    /** 已缓冲到的位置；没有缓冲信息的数据源保持等于播放进度。 */
    var bufferedMillis by mutableLongStateOf(positionMillis)

    /**
     * 播放速度，1f 为正常。
     *
     * 放在播放层而不是页面状态里：它最终要落到 `Media3` 的 `PlaybackParameters.speed`，
     * 与进度、时长同一条回写路径；且只有底栏那一个胶囊读它，改速度不会惊动选集网格。
     */
    var speed by mutableFloatStateOf(1f)
        private set

    /** 0..1。时长为 0（还没 prepare 完）时返回 0，避免除零。 */
    val progressFraction: Float
        get() = if (durationMillis > 0L) {
            (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        } else {
            0f
        }

    /** 拖动进度条时调用：写入的是最终位置，不经过任何中间层。 */
    fun seekToFraction(fraction: Float) {
        if (durationMillis <= 0L) return
        positionMillis = (durationMillis * fraction.coerceIn(0f, 1f)).toLong()
    }

    fun togglePlayPause() {
        isPlaying = !isPlaying
    }

    /** 夹在 [MIN_SPEED]..[MAX_SPEED]：界面只给固定档位，这里是防越界的最后一道。 */
    fun setPlaybackSpeed(value: Float) {
        speed = value.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    companion object {
        /** 接 Media3 之前的占位数据：让 UI 有进度可画、有时间可读。 */
        fun placeholder(): PlayerPlaybackState = PlayerPlaybackState(
            positionMillis = PLACEHOLDER_POSITION_MILLIS,
            durationMillis = PLACEHOLDER_DURATION_MILLIS,
        )

        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f

        private const val PLACEHOLDER_POSITION_MILLIS = 4_000L
        private const val PLACEHOLDER_DURATION_MILLIS = 1_499_000L
    }
}
