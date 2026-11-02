package com.liomix.mikomi.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.feature.player.R
import kotlinx.coroutines.delay

/** 进度条可触控高度（视觉轨道只有 3dp，其余是给手指的余量）。 */
private val SeekBarHeight = 40.dp

/** 播放中控制条自动隐藏延时。 */
private const val CONTROLS_HIDE_DELAY_MILLIS = 3_500L

/** 占位时长 / 进度：真实数据接入 Media3 后删除。 */
private const val PLACEHOLDER_DURATION_MILLIS = 1_499_000L
private const val PLACEHOLDER_POSITION_MILLIS = 4_000L

/**
 * 播放器区域：画面 + 覆盖式控制层。
 *
 * 布局要点：
 * - 画面铺满整块区域并**顶到状态栏之下**（`edge-to-edge`），顶栏用状态栏 inset 让位，
 *   所以返回按钮不会被状态栏压住；
 * - 控制层分上下两块，各自带一层渐变遮罩，保证白字在任意画面上都可读；
 * - 点画面任意空白处切换控制层显隐，播放中 3.5s 自动隐藏。
 *
 * 当前没有接入 Media3：`isPlaying` / `position` 是组件内部的占位状态，
 * 进度条可拖动但不会真的驱动播放。接播放层时把这三项与回调换成外部传入即可。
 */
@Composable
internal fun PlayerSurface(
    title: String,
    subtitle: String,
    height: Dp,
    onBackClick: () -> Unit,
    onPlayerSettingsClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var positionMillis by rememberSaveable { mutableLongStateOf(PLACEHOLDER_POSITION_MILLIS) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }

    // 播放中才自动隐藏；拖动进度条期间保持可见，松手后重新计时。
    LaunchedEffect(controlsVisible, isPlaying, scrubbing) {
        if (!controlsVisible || !isPlaying || scrubbing) return@LaunchedEffect
        delay(CONTROLS_HIDE_DELAY_MILLIS)
        controlsVisible = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible }
            },
    ) {
        // 画面占位：真实播放时这里换成 `PlayerView` / `AndroidView`。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF14161B), Color(0xFF05060A)),
                    ),
                ),
        ) {
            Text(
                text = stringResource(R.string.player_surface_placeholder),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.28f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerTopOverlay(
                title = title,
                subtitle = subtitle,
                onBackClick = onBackClick,
                onPlayerSettingsClick = onPlayerSettingsClick,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBottomControls(
                isPlaying = isPlaying,
                positionMillis = positionMillis,
                durationMillis = PLACEHOLDER_DURATION_MILLIS,
                onPlayPauseClick = { isPlaying = !isPlaying },
                onSeek = { fraction ->
                    positionMillis = (PLACEHOLDER_DURATION_MILLIS * fraction).toLong()
                },
                onScrubbingChange = { scrubbing = it },
                onFullscreenClick = onFullscreenClick,
            )
        }
    }
}

/**
 * 顶栏：返回 + 标题 + 当前集 + 右侧更多（播放器设置），背景是向下淡出的黑色遮罩。
 *
 * 标题列用 `weight(1f)` 而不是 `fillMaxWidth`：右上角的更多按钮要独占位置，
 * 长标题只在自己那一段里省略，不会把图标挤出屏幕。
 */
@Composable
private fun PlayerTopOverlay(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    onPlayerSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.player_back),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onPlayerSettingsClick) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.player_settings),
                tint = Color.White,
            )
        }
    }
}

/** 底栏：播放键 + 当前时间 + 进度条 + 总时长 + 全屏。 */
@Composable
private fun PlayerBottomControls(
    isPlaying: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    onFullscreenClick: () -> Unit,
) {
    val timeStyle = MaterialTheme.typography.labelMedium.copy(
        color = Color.White,
        // 等宽数字：秒数变化时文字宽度不跳。
        fontFeatureSettings = "tnum",
    )
    val progress = if (durationMillis > 0) {
        (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                ),
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPlayPauseClick) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.player_pause else R.string.player_play,
                ),
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(text = formatTime(positionMillis), style = timeStyle)
        PlayerSeekBar(
            progress = progress,
            onSeek = onSeek,
            onScrubbingChange = onScrubbingChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(text = formatTime(durationMillis), style = timeStyle)
        IconButton(onClick = onFullscreenClick) {
            Icon(
                imageVector = Icons.Rounded.Fullscreen,
                contentDescription = stringResource(R.string.player_fullscreen),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * 细轨道进度条。
 *
 * 不用 Material3 `Slider`：它自带 48dp 高度、厚轨道与独立 thumb 样式，
 * 在视频画面上比这里的细线重得多。绘制用 [Canvas]，手势用一次 `awaitEachGesture`
 * 同时覆盖「点击定位」与「按住拖动」——分成两个 pointerInput 会互相抢 down 事件。
 */
@Composable
private fun PlayerSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val seek = { x: Float ->
        if (widthPx > 0) onSeek((x / widthPx).coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier
            .height(SeekBarHeight)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(widthPx) {
                if (widthPx <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onScrubbingChange(true)
                    seek(down.position.x)
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        seek(change.position.x)
                        change.consume()
                    }
                    onScrubbingChange(false)
                }
            },
    ) {
        val centerY = size.height / 2f
        val trackHeight = 3.dp.toPx()
        val corner = CornerRadius(trackHeight / 2f)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.26f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = corner,
        )
        val playedWidth = size.width * progress.coerceIn(0f, 1f)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(playedWidth, trackHeight),
            cornerRadius = corner,
        )
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = Offset(playedWidth, centerY),
        )
    }
}

/** 毫秒 → `mm:ss`（超过一小时按 `h:mm:ss`）。 */
private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.pad2()}:${seconds.pad2()}"
    } else {
        "${minutes.pad2()}:${seconds.pad2()}"
    }
}

private fun Long.pad2(): String = if (this < 10) "0$this" else toString()
