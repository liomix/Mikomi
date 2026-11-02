package com.liomix.mikomi.feature.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 播放指令通道：由播放页 ViewModel 实现，转发给 Media3。
 *
 * 为什么还要这一层：UI 只认识「这个 holder」，不该直接摸 `ExoPlayer`（那是播放实现），
 * 而 holder 又必须能在没有播放器时安全存在（首次进页面还没出流）。中间隔一个接口，
 * 这两件事就都成立了。
 */
interface PlayerCommandSink {
    fun togglePlayPause()

    fun seekTo(positionMillis: Long)

    fun setPlaybackSpeed(speed: Float)

    /** 出流/解码失败后重来一次（重新出流，不是 `prepare` 同一份坏地址）。 */
    fun retryPlayback()
}

/**
 * 播放层状态：进度、时长、缓冲、播放中。
 *
 * 单独成一个 holder、**不进 [PlayerUiState]**：这些值播放时每帧都在变，
 * 混进页面状态会让整页（含选集网格）跟着重组。
 *
 * 数据是单向的：Media3 的 `Player.Listener` 往这里写（[isPlaying] / [durationMillis] 等），
 * UI 只读；UI 的命令走 [sink] 反向出去。所以**不要**在 UI 里直接改 [isPlaying]——
 * 那会和播放器真实状态打架，表现为「图标是暂停、画面还在动」。
 *
 * 读取路径刻意分成三条，代价各不相同：
 * - 进度条 / 缓冲条在**绘制阶段**读（`Canvas` 的 onDraw），只失效绘制；
 * - 时间文字先 `derivedStateOf` 压成「秒」，一秒最多重组一次那个 `Text`；
 * - 播放/暂停图标单独一个 composable 读 [isPlaying]，切换时不牵连底栏其它部分。
 */
@Stable
class PlayerPlaybackState {

    var isPlaying by mutableStateOf(false)
        private set

    /** 正在缓冲（含起播前的那一段）。画面上给一个小转圈，避免用户以为卡死。 */
    var isBuffering by mutableStateOf(false)
        private set

    var positionMillis by mutableLongStateOf(0L)
        private set

    var durationMillis by mutableLongStateOf(0L)
        private set

    /** 已缓冲到的位置；没有缓冲信息的数据源保持等于播放进度。 */
    var bufferedMillis by mutableLongStateOf(0L)
        private set

    /**
     * 播放速度，1f 为正常。
     *
     * 放在播放层而不是页面状态里：它最终要落到 `PlaybackParameters.speed`，
     * 与进度、时长同一条回写路径；且只有底栏那一个胶囊读它，改速度不会惊动选集网格。
     */
    var speed by mutableFloatStateOf(1f)
        private set

    /** 命令出口；播放器就绪前为 null。为 null 时按钮点了不做事（不假装已经在播）。 */
    var sink: PlayerCommandSink? = null

    /** 0..1。时长为 0（还没 prepare 完）时返回 0，避免除零。 */
    val progressFraction: Float
        get() = if (durationMillis > 0L) {
            (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        } else {
            0f
        }

    /** 拖动进度条时调用：写入的是最终位置，乐观更新一次，避免松手瞬间回弹到旧位置。 */
    fun seekToFraction(fraction: Float) {
        if (durationMillis <= 0L) return
        val target = (durationMillis * fraction.coerceIn(0f, 1f)).toLong()
        positionMillis = target
        sink?.seekTo(target)
    }

    fun togglePlayPause() {
        sink?.togglePlayPause()
    }

    /** 夹在 [MIN_SPEED]..[MAX_SPEED]：界面只给固定档位，这里是防越界的最后一道。 */
    fun setPlaybackSpeed(value: Float) {
        val clamped = value.coerceIn(MIN_SPEED, MAX_SPEED)
        speed = clamped
        sink?.setPlaybackSpeed(clamped)
    }

    /** 换集/换源：进度与时长都是上一条流的，必须清掉。 */
    fun resetForNewMedia() {
        positionMillis = 0L
        durationMillis = 0L
        bufferedMillis = 0L
        isBuffering = true
    }

    // —— 以下由播放层（ViewModel 的 Player.Listener / 进度轮询）回写 ——

    internal fun onIsPlayingChanged(playing: Boolean) {
        isPlaying = playing
    }

    internal fun onBufferingChanged(buffering: Boolean) {
        isBuffering = buffering
    }

    internal fun onProgress(position: Long, buffered: Long) {
        positionMillis = position
        bufferedMillis = buffered
    }

    internal fun onDurationChanged(duration: Long) {
        durationMillis = duration
    }

    internal fun onSpeedChanged(value: Float) {
        speed = value.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
    }
}
