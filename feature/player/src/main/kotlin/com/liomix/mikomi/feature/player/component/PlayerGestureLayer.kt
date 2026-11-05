package com.liomix.mikomi.feature.player.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiMedia
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiTextStyles
import com.liomix.mikomi.core.model.PlayerPlaybackSettings
import com.liomix.mikomi.feature.player.PlayerPlaybackState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 手势结束之后 HUD 再停留一会儿，避免数字一闪而过。 */
private const val HUD_LINGER_MILLIS = 600L

/** 亮度手势的下限：0 会让画面全黑，用户连「我在哪」都看不出来。 */
private const val MIN_BRIGHTNESS_FRACTION = 0.01f

/**
 * 亮度低于这个值就换成「小太阳」图标。
 *
 * 读数不再写字之后，图标是唯一的语义线索——默认那颗中等亮度的太阳在 5% 和 50%
 * 时长得一模一样，画面上已经暗到看不清了图标还没变，就成了错误信息。
 */
private const val BRIGHTNESS_LOW_FRACTION = 0.2f

/**
 * HUD 底板：**白色半透明**。
 *
 * 深色底板压在暗画面上等于没有底板（边界消失），压在亮画面上才勉强看得出来；
 * 白色半透明两种画面都稳：暗画面上是一块柔和的亮块，亮画面上靠 0.85 的不透度
 * 仍能拉开层次。半透明而不是实白，是为了不把底下正在看的内容挖掉一块。
 */
private val HudContainer = MikomiMedia.overlayContainer

/** 白底上的内容色。近黑而非纯黑：0.85 的白上纯黑偏硬，读起来像警告。 */
private val HudContent = MikomiMedia.overlayContent

/** HUD 图标：比控制层的图标大一档，一眼能看清，不靠文字补。 */
private val HudIconSize = 32.dp

private val HudCornerRadius = 12.dp

/** HUD 里那根细进度条的宽度。 */
private val HudValueTrackWidth = 84.dp

/** 快进 / 快退读数：`00:00/00:00`，比控制条正文大一号。 */
private val HudTimeStyle = MikomiTextStyles.HudTime

/**
 * HUD 需要让开的安全区：状态栏 / 刘海。
 *
 * 只取顶部与水平方向——底部留白是控制条的，读数不该被它推上去。
 * 这里用 `windowInsetsPadding` 而非裸 padding：内嵌播放器在页面里已被外层
 * `statusBarsPadding` 消费过一次，嵌套的调用看到的是零，不会重复下压。
 */
private val hudSafeArea: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    )

/**
 * 手势读数的统一落点：**水平居中、竖直略高于画面中心**。
 *
 * 为什么所有读数都落在同一个点：
 * - 一屏只有一个落点，滑完不用扫视就知道去哪儿读。位置随手势变化，等于每次都要重新找一遍，
 *   连续调节音量/亮度时最难受。
 * - 偏上而不是正中：握机时拇指在画面下半部作业，正中正好被自己的手压住；
 *   而画面的视觉重心本来就在中心偏上（人脸、字幕多在这一带），眼睛的落点也在那里。
 *
 * 竖直 bias `-0.4` ≈ 内容中心落在屏高 30% 处（正中是 50%），小屏/大屏按比例走，不写死 dp。
 * 再往上就顶到顶栏了——横屏全屏时顶栏占掉上面 70dp，那之外没有空位可放。
 */
private val HudAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.4f)

/**
 * 播放器手势层。
 *
 * 一个 `pointerInput` 节点处理全部手势，**不拆成多个检测器**：`detectTapGestures` 与
 * `detectDragGestures` 各自要抢 down、各自要判触摸阈值，叠在一起时要么互相吞事件，
 * 要么同一帧既算单击又算拖动。这里只有一条状态机，一次触摸从头到尾只走一条分支。
 *
 * 手势语义（全屏与内嵌播放器一致）：
 * - 单击：显隐控制层（延迟一个双击判定窗口再执行，否则双击的第一下会先把控制层抖出来）；
 * - 双击：播放 / 暂停；
 * - 长按：轻微震动 + 临时加速到设置里的档位，松手（或开始滑动）立刻恢复；
 * - 左右滑：快进 / 快退，整屏宽度对应设置里的秒数，松手才真的 seek；
 * - 左半屏上下滑：音量；右半屏上下滑：亮度。滑动整屏高度走完整个范围。
 *
 * 所有读数都落在 [HudAlignment] 这一个点上（水平居中、略高于中心），
 * 换手势不换位置——位置固定，用户第二次就不用找。
 *
 * 为什么左右滑要「松手才 seek」：这类流多半是 HLS，每 seek 一次都要重新拉分片 +
 * 起解码；拖动过程中连续 seek 会一路卡给它看。拖动期间 HUD 上的目标位置是实时的，
 * 用户看得见自己在往哪走。
 */
@Composable
internal fun PlayerGestureLayer(
    playback: PlayerPlaybackState,
    settings: PlayerPlaybackSettings,
    brightness: PlayerBrightnessController,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val audio = remember(context) {
        runCatching { context.getSystemService(AudioManager::class.java) }.getOrNull()
    }
    val scope = rememberCoroutineScope()

    var hud by remember { mutableStateOf<PlayerGestureHud?>(null) }
    // 停留计时只留一个：拖动时每帧都会刷新 HUD，每帧起一个协程会白扔几十个 Job。
    var hideJob by remember { mutableStateOf<Job?>(null) }

    fun flash(next: PlayerGestureHud) {
        hideJob?.cancel()
        hud = next
    }

    fun settle() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(HUD_LINGER_MILLIS)
            hud = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(settings) {
                        detectPlayerGestures(
                            playback = playback,
                            settings = settings,
                            audio = audio,
                            brightness = brightness,
                            scope = scope,
                            performHaptic = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onTap = onTap,
                            onFlash = ::flash,
                            onSettle = ::settle,
                        )
                    }
                },
            ),
    ) {
        val current = hud
        if (current != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(hudSafeArea),
                contentAlignment = HudAlignment,
            ) {
                PlayerGestureHudView(
                    hud = current,
                    durationMillis = playback.durationMillis,
                )
            }
        }
    }
}

/**
 * 手势状态机本体。
 *
 * 长按计时用 `scope.launch { delay() }` 而不是在指针事件里做超时判断：
 * 手指不动的时候根本没有指针事件，任何「等事件 + 判超时」的写法都会一直等到抬手。
 */
private suspend fun PointerInputScope.detectPlayerGestures(
    playback: PlayerPlaybackState,
    settings: PlayerPlaybackSettings,
    audio: AudioManager?,
    brightness: PlayerBrightnessController,
    scope: CoroutineScope,
    performHaptic: () -> Unit,
    onTap: () -> Unit,
    onFlash: (PlayerGestureHud) -> Unit,
    onSettle: () -> Unit,
) {
    // 双击判定要跨手势存活：第一下的抬手与第二下的按下是两次 awaitEachGesture。
    var lastTapUpMillis = 0L
    var pendingTapJob: Job? = null

    val sweepMillis = settings.seekSweepSeconds * 1_000L
    val maxVolume = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        // 防御除零：还没测量完就收到了按下（极短的一瞬）。
        if (width <= 0f || height <= 0f) return@awaitEachGesture

        val startPosition = playback.positionMillis
        val volumeSide = down.position.x < width / 2f
        var mode = GestureMode.Undecided
        var longPressActive = false
        var released = false
        var seekTarget = 0L
        var verticalBase = 0f
        var appliedPercent = -1

        val longPressJob = scope.launch {
            delay(viewConfiguration.longPressTimeoutMillis)
            // 已经松手或已经在滑动：这一按不是长按。
            if (mode != GestureMode.Undecided || released) return@launch
            longPressActive = true
            performHaptic()
            playback.beginTemporarySpeed(settings.longPressSpeed)
            onFlash(PlayerGestureHud.Speed(settings.longPressSpeed))
        }

        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    released = true
                    break
                }
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y

                if (mode == GestureMode.Undecided) {
                    if (max(abs(dx), abs(dy)) <= viewConfiguration.touchSlop) continue
                    mode = when {
                        abs(dx) > abs(dy) -> GestureMode.Seek
                        volumeSide -> GestureMode.Volume
                        else -> GestureMode.Brightness
                    }
                    // 长按中开始滑动：先把加速收回来，不然会一边 2 倍速一边找位置。
                    if (longPressActive) {
                        longPressActive = false
                        playback.endTemporarySpeed()
                    }
                    when {
                        mode == GestureMode.Seek && playback.durationMillis <= 0L -> {
                            // 时长还没拿到，左右滑没有意义；也别把它当成单击。
                            mode = GestureMode.Ignore
                        }
                        mode == GestureMode.Volume -> {
                            verticalBase = if (maxVolume <= 0 || audio == null) {
                                0f
                            } else {
                                audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                            }
                        }
                        mode == GestureMode.Brightness -> verticalBase = brightness.fraction
                    }
                }

                when (mode) {
                    GestureMode.Seek -> {
                        val delta = (dx / width * sweepMillis).toLong()
                        seekTarget = (startPosition + delta).coerceIn(0L, playback.durationMillis)
                        onFlash(PlayerGestureHud.Seek(seekTarget))
                    }

                    GestureMode.Volume -> {
                        if (maxVolume > 0 && audio != null) {
                            val fraction = (verticalBase - dy / height).coerceIn(0f, 1f)
                            val volume = (fraction * maxVolume).roundToInt()
                            // 只有整档变化时才真的去设：setStreamVolume 会拉起系统音量条，
                            // 每帧都调一次等于一路在闪。
                            if (volume != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                            }
                            onFlash(PlayerGestureHud.Volume(fraction))
                        }
                    }

                    GestureMode.Brightness -> {
                        // 下限留 1%：调到 0 会让画面全黑，用户连「我在哪」都看不出来。
                        val fraction = (verticalBase - dy / height).coerceIn(MIN_BRIGHTNESS_FRACTION, 1f)
                        val percent = (fraction * 100f).roundToInt()
                        if (percent != appliedPercent) {
                            appliedPercent = percent
                            brightness.apply(fraction)
                        }
                        onFlash(PlayerGestureHud.Brightness(fraction))
                    }

                    GestureMode.Undecided, GestureMode.Ignore -> Unit
                }

                if (mode != GestureMode.Undecided) change.consume()
            }
        } finally {
            longPressJob.cancel()
            if (longPressActive) playback.endTemporarySpeed()
        }

        when {
            mode == GestureMode.Seek -> {
                playback.seekTo(seekTarget)
                onSettle()
            }

            mode == GestureMode.Volume || mode == GestureMode.Brightness -> onSettle()

            // 长按抬手：加速在 finally 里已经收回了，读数也得一起收。
            // 少了这一支，倍速图标会一直挂在画面上不动——它没有被 onSettle 排进「消失」的队列，
            // 只能等下一次手势把它替换掉。
            longPressActive -> onSettle()

            // 没位移、也不是长按 —— 单击 / 双击。
            // 长按抬手不能算单击：用户按住看了两秒加速，松手不该顺手把控制层抖出来。
            mode == GestureMode.Undecided && released -> {
                val now = SystemClock.uptimeMillis()
                val isDoubleTap = now - lastTapUpMillis <= viewConfiguration.doubleTapTimeoutMillis
                lastTapUpMillis = if (isDoubleTap) 0L else now
                if (isDoubleTap) {
                    pendingTapJob?.cancel()
                    val willPlay = !playback.isPlaying
                    playback.togglePlayPause()
                    onFlash(PlayerGestureHud.Toggle(willPlay))
                    onSettle()
                } else {
                    // 单击延后一个双击窗口再执行：不等的话双击的第一下会先把控制层显示出来，
                    // 第二下再把它藏回去，屏幕中间会闪两下。
                    pendingTapJob?.cancel()
                    pendingTapJob = scope.launch {
                        delay(viewConfiguration.doubleTapTimeoutMillis)
                        onTap()
                    }
                }
            }
        }
    }
}

/** 一次触摸最终走的是哪条分支。 */
private enum class GestureMode {
    Undecided,
    Seek,
    Volume,
    Brightness,

    /** 横向滑动但没有时长（还没 prepare 完）：什么都不做，也不要在后面误判成单击。 */
    Ignore,
}

/** HUD 上要显示的东西。 */
internal sealed interface PlayerGestureHud {
    /**
     * 拖动进度时的读数，只带目标位置。
     *
     * 不再带「相对当前位置的增量」：那一版要在读数里写 `+00:15`，而这个数字每帧都变，
     * 得先读再和别的东西比对才知道快进了多少。`当前/总长` 是同一件事的更直接说法——
     * 眼睛落在表盘上的位置就知道走到哪了。
     */
    data class Seek(val targetMillis: Long) : PlayerGestureHud

    data class Volume(val fraction: Float) : PlayerGestureHud

    data class Brightness(val fraction: Float) : PlayerGestureHud

    data class Speed(val speed: Float) : PlayerGestureHud

    data class Toggle(val playing: Boolean) : PlayerGestureHud
}

/**
 * HUD 的一块：**图标 +（音量 / 亮度）细进度条**，左右滑那一块换成 `00:00/00:00` 时间。
 *
 * 为什么不写「音量 45%」这类文字：这些读数一次观看要弹出几十次，文字得先读再理解，
 * 比图标慢半拍；字数还随语言和数值变，底板宽度跟着跳，一屏中间那块总是抖。
 * 唯一的例外是进度：拖动时用户真正关心的是「拖到哪儿了」，一个能直接对照进度的
 * 时间码比任何图标都准，所以它单独写成 `当前/总长`。
 *
 * 底板**不留内边距**：块的大小就是内容的大小，圆角直接贴着内容。
 * 加了内边距的白块在画面中间会显得又厚又沉，而这些读数只该占一小块。
 * 唯一的例外是时间码左右那 10dp——圆角白底若紧贴字形，首尾数字会被圆角削掉；
 * 图标不怕这个，图形四周本来就是空的。
 */
@Composable
private fun PlayerGestureHudView(
    hud: PlayerGestureHud,
    durationMillis: Long,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector? = when (hud) {
        // 进度用时间码，不用图标。
        is PlayerGestureHud.Seek -> null
        is PlayerGestureHud.Volume ->
            if (hud.fraction <= 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp
        is PlayerGestureHud.Brightness ->
            if (hud.fraction <= BRIGHTNESS_LOW_FRACTION) {
                Icons.Rounded.BrightnessLow
            } else {
                Icons.Rounded.BrightnessMedium
            }
        is PlayerGestureHud.Speed -> Icons.Rounded.Speed
        is PlayerGestureHud.Toggle -> if (hud.playing) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
    }
    val fraction: Float? = when (hud) {
        is PlayerGestureHud.Volume -> hud.fraction
        is PlayerGestureHud.Brightness -> hud.fraction
        else -> null
    }

    Column(
        modifier = modifier.background(HudContainer, RoundedCornerShape(HudCornerRadius)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (hud is PlayerGestureHud.Seek) {
            Text(
                text = "${formatTime(hud.targetMillis / 1_000)}/${formatTime(durationMillis / 1_000)}",
                // 时间码左右各留一点：圆角白底如果贴着字形，首尾数字会被圆角削掉。
                // 图标块不需要——图形本身四周是空的，圆角只削到它的空白角。
                modifier = Modifier.padding(horizontal = 10.dp),
                // `tnum`：等宽数字。拖动时数字每帧都在变，比例数字会让整块左右抖。
                // 字阶取设计系统的 HUD 档（22sp Bold），这里只把数字换成等宽 `tnum`——
                // 拖动时数字每帧都在变，比例数字会让整块左右抖。
                style = HudTimeStyle.copy(fontFeatureSettings = "tnum"),
                color = HudContent,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HudContent,
                modifier = Modifier.size(HudIconSize),
            )
        }
        if (fraction != null) {
            Spacer(modifier = Modifier.height(10.dp))
            ValueTrack(fraction = fraction)
        }
    }
}

/** HUD 里的细进度条：白底上就得反着来——底色淡黑，已用量近黑。 */
@Composable
private fun ValueTrack(fraction: Float) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = MikomiMotion.Duration.Instant),
        label = "playerGestureValue",
    )
    Canvas(
        modifier = Modifier
            .width(HudValueTrackWidth)
            .height(3.dp),
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(
            color = HudContent.copy(alpha = 0.18f),
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = HudContent,
            topLeft = Offset.Zero,
            size = Size(size.width * animated, size.height),
            cornerRadius = radius,
        )
    }
}

/**
 * 窗口亮度控制器。
 *
 * 亮度改的是**窗口属性**（`screenBrightness`），不是系统亮度：改系统亮度要
 * `WRITE_SETTINGS` 权限，而且会顺手改掉用户手机全局的设置——看个番把整台手机的亮度
 * 调暗了，那是越权。
 *
 * 「什么时候保持、什么时候还原」的口径：
 * - **音量**：就是系统媒体音量，手势改完就留在那儿。它与音量键是同一个值，
 *   过一会儿又自己变回去，会让用户以为滑动没生效。
 * - **亮度**：在播放页期间保持（换集、切全屏都不还原，否则每次换集都闪一下）；
 *   离开播放页时连同窗口属性一起还原——它本来就是这次观看的临时覆盖。
 */
internal class PlayerBrightnessController(private val activity: Activity?) {

    private val window: Window? = activity?.window
    private val initialOverride: Float = window?.attributes?.screenBrightness ?: OVERRIDE_NONE

    /** 当前亮度（0..1）：窗口自己设过就用它，否则拿系统亮度当起点。 */
    val fraction: Float = initialFraction()

    private fun initialFraction(): Float {
        if (initialOverride >= 0f) return initialOverride
        val owner = activity ?: return DEFAULT_FRACTION
        return runCatching {
            Settings.System.getInt(
                owner.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                MAX_SYSTEM_BRIGHTNESS / 2,
            ) / MAX_SYSTEM_BRIGHTNESS.toFloat()
        }.getOrDefault(DEFAULT_FRACTION)
    }

    fun apply(value: Float) {
        val target = value.coerceIn(MIN_BRIGHTNESS_FRACTION, 1f)
        val current = window ?: return
        current.attributes = current.attributes.apply { screenBrightness = target }
    }

    /** 离开播放页：把窗口亮度写回进入前的样子（`-1` 表示交回系统）。 */
    fun restore() {
        val current = window ?: return
        current.attributes = current.attributes.apply { screenBrightness = initialOverride }
    }

    private companion object {
        /** `BRIGHTNESS_OVERRIDE_NONE`：不覆盖，跟随系统。 */
        const val OVERRIDE_NONE = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        const val MAX_SYSTEM_BRIGHTNESS = 255

        /** 读不到系统亮度时的起点：中间值，不至于一上手就是全黑或全亮。 */
        const val DEFAULT_FRACTION = 0.5f
    }
}

/**
 * 取一个播放器亮度控制器，并在离开页面时还原。
 *
 * `remember(activity)`：Activity 换了就是另一个窗口，旧的属性不再有意义。
 */
@Composable
internal fun rememberPlayerBrightness(): PlayerBrightnessController {
    val activity = LocalContext.current.findActivity()
    val controller = remember(activity) { PlayerBrightnessController(activity) }
    DisposableEffect(controller) {
        onDispose { controller.restore() }
    }
    return controller
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
