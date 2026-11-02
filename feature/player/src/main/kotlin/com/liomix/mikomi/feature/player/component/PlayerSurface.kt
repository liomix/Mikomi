package com.liomix.mikomi.feature.player.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.feature.player.PlayerPlaybackState
import com.liomix.mikomi.feature.player.PlayerVideoScale
import com.liomix.mikomi.feature.player.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

/** 进度条可触控高度（视觉轨道只有 3dp，其余是给手指的余量）。 */
private val SeekBarHeight = 40.dp

/** 控制行按钮尺寸：**可见方块就是触控区**，与导航条的圆钮同一套算法。 */
private val ControlSize = 40.dp

/** 同组按钮间距（上一集/播放/下一集）。 */
private val ControlGap = 6.dp

/** 跨组间距（播放组 ↔ 弹幕组）：比组内大，靠疏密把「哪些是一伙的」讲清楚。 */
private val GroupGap = 12.dp

private val ControlShape = RoundedCornerShape(10.dp)

private val ControlIconSize = 20.dp

/**
 * 九个按钮同排时的宽度下限（实测值）：
 * 上下集 + 播放 132 · 弹幕组 86 · 速度胶囊 ~50 · 填充/集数/全屏三枚 132 · 间距与内边距 ~30。
 * 窄于它就把弹幕/填充/集数收起来，留给非全屏那一套，否则最后一个按钮会被挤出屏幕。
 */
private val FullActionsMinWidth = 430.dp

/** 按钮底：视频画面上不用主题 surface（夜间会被压成黑色，跟画面糊在一起）。 */
private val ControlContainer = Color.White.copy(alpha = 0.14f)

/**
 * 主操作（播放/暂停）的底：比同排按钮亮一档。
 *
 * 不用主题 primary 做实心块：这一行是压在画面上的单色控制条，插一个彩色按钮会抢掉
 * 画面本身；「亮一档」既有层级又不会跳出这套白字遮罩。主色只留给**状态**（弹幕开启）。
 */
private val ProminentControlContainer = Color.White.copy(alpha = 0.28f)

/** 不可用按钮的透明度。控制层整体隐藏时父级 alpha 已经是 0，这里的 0.38 不会露出来。 */
private const val DISABLED_ALPHA = 0.38f

/** 播放中控制层自动隐藏延时。 */
private const val CONTROLS_HIDE_DELAY_MILLIS = 3_500L

private val SpeedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

// 遮罩与占位画面都是常量：写成顶层值，组合时一次都不再分配。
// 之前是在组合里现算 `Brush.verticalGradient(...)`，虽然只是小对象，但每帧重组都在扔垃圾。
private val TopScrim = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent),
)
private val BottomScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
)
private val PlaceholderFrame = Brush.linearGradient(
    colors = listOf(Color(0xFF14161B), Color(0xFF05060A)),
)

/**
 * 播放器画面 + 覆盖式控制层。
 *
 * 尺寸由调用方（[PlayerHost]）给的约束决定，本组件不自己算高度：
 * 全屏与非全屏是同一份组合，只是外面给的约束不同——这是「不换 window」的落地方式。
 *
 * 性能上有五件事是刻意这么写的：
 * 1. **控制层显隐用 alpha，不用 `AnimatedVisibility`**：后者隐藏时销毁整棵控制层，
 *    每 3.5s 自动隐藏/呼出都要重建一次（一次掉帧 + 丢状态）。这里控制层常驻，
 *    alpha 只在 `graphicsLayer` 的 lambda 里读——动画期间零重组、零绘制外开销。
 *    隐藏时按钮统一 `enabled = false`，不然看不见的按钮还会抢走「点画面显隐」的手势。
 * 2. **播放进度在绘制阶段读**：底栏不读 `positionMillis`，只有进度条的 onDraw 读，
 *    所以播放时底栏根本不重组，拖动进度条也只失效绘制。
 * 3. **时间文字压到「秒」再读**：`derivedStateOf` 让 `Text` 一秒最多重组一次，
 *    而不是每 200ms 重新排版一次文本。
 * 4. **不写宽度 state**：进度条宽度直接取 `PointerInputScope.size`，
 *    省掉 `onSizeChanged` 回写状态带来的测量后重组。
 * 5. **速度只让胶囊自己重组**：`playback.speed` 在 [SpeedChip] 内部读，
 *    改速度不会牵动整条控制行。
 *
 * 还没接 Media3：画面是渐变占位，播放状态来自 [PlayerPlaybackState] 的占位值。
 */
@Composable
internal fun PlayerSurface(
    title: String,
    subtitle: String,
    fullscreen: Boolean,
    playback: PlayerPlaybackState,
    danmakuEnabled: Boolean,
    canPlayPrevious: Boolean,
    canPlayNext: Boolean,
    onBackClick: () -> Unit,
    onPlayerSettingsClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onDanmakuToggle: () -> Unit,
    onDanmakuSettingsClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    // 两个下拉菜单的展开状态放在这里而不是各自的胶囊内部：
    // 它们是「控制条整体」的状态，且要参与下面的自动隐藏判断——放进胶囊会随组合位置变化丢失，
    // 一旦丢失就再没人回调「关闭」，控制层将永远不再自动隐藏。
    var speedMenuOpen by remember { mutableStateOf(false) }
    var scaleMenuOpen by remember { mutableStateOf(false) }
    // 用户正在跟控制层较劲（拖进度条、开着菜单）：此时不能自动隐藏。
    val interactionPinned = scrubbing || speedMenuOpen || scaleMenuOpen

    // 填充方式是纯视图状态：只影响画面怎么铺，不参与业务。
    var videoScale by rememberSaveable { mutableStateOf(PlayerVideoScale.AUTO) }

    // 只返回 State，读取延迟到绘制阶段（见文件头第 1 条）。
    val controlsAlpha = animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (controlsVisible) 150 else 220),
        label = "playerControlsAlpha",
    )

    // 播放中 3.5s 自动隐藏。isPlaying 走 snapshotFlow 读：
    // 直接读进组合的话，每次播放/暂停都会让整块画面重组。
    LaunchedEffect(controlsVisible, interactionPinned, playback) {
        if (!controlsVisible || interactionPinned) return@LaunchedEffect
        snapshotFlow { playback.isPlaying }
            .distinctUntilChanged()
            .collect { playing ->
                if (playing) {
                    delay(CONTROLS_HIDE_DELAY_MILLIS)
                    controlsVisible = false
                }
            }
    }

    // 顶栏让位：状态栏 + 刘海（横屏全屏时刘海在左右，所以水平方向也要让）。
    val topInsets = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    )
    // 底栏只在全屏时让位系统手势条：非全屏时播放器在页面顶部，离底部很远。
    val bottomInsets = WindowInsets.safeDrawing.only(
        if (fullscreen) {
            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
        } else {
            WindowInsetsSides.Horizontal
        },
    )

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible }
            },
    ) {
        // 画面占位：接 Media3 时这一步换成 `AndroidView { PlayerView }` 并映射 videoScale，
        // 其余布局不动。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PlaceholderFrame),
        ) {
            Text(
                text = stringResource(R.string.player_surface_placeholder),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.28f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = controlsAlpha.value },
        ) {
            PlayerTopOverlay(
                title = title,
                subtitle = subtitle,
                enabled = controlsVisible,
                insets = topInsets,
                onBackClick = onBackClick,
                onPlayerSettingsClick = onPlayerSettingsClick,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            PlayerBottomControls(
                playback = playback,
                fullscreen = fullscreen,
                enabled = controlsVisible,
                danmakuEnabled = danmakuEnabled,
                videoScale = videoScale,
                speedMenuOpen = speedMenuOpen,
                scaleMenuOpen = scaleMenuOpen,
                canPlayPrevious = canPlayPrevious,
                canPlayNext = canPlayNext,
                insets = bottomInsets,
                onScrubbingChange = { scrubbing = it },
                onSpeedMenuChange = { speedMenuOpen = it },
                onScaleMenuChange = { scaleMenuOpen = it },
                onPreviousEpisode = onPreviousEpisode,
                onNextEpisode = onNextEpisode,
                onDanmakuToggle = onDanmakuToggle,
                onDanmakuSettingsClick = onDanmakuSettingsClick,
                onVideoScaleChange = { videoScale = it },
                onEpisodesClick = onEpisodesClick,
                onToggleFullscreen = onToggleFullscreen,
                modifier = Modifier.align(Alignment.BottomCenter),
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
    enabled: Boolean,
    insets: WindowInsets,
    onBackClick: () -> Unit,
    onPlayerSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TopScrim)
            .windowInsetsPadding(insets)
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick, enabled = enabled) {
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
        IconButton(onClick = onPlayerSettingsClick, enabled = enabled) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.player_settings),
                tint = Color.White,
            )
        }
    }
}

/**
 * 底栏：**小屏一行，全屏两行**。
 *
 * 两者的差别不是审美，是可用空间与去重：
 * - 小屏：播放器嵌在页面里，只有 16:9 那么高，且正下方就是导航条（弹幕/换源）与选集网格，
 *   所以只留「播放 + 时间 + 进度 + 时长 + 全屏」，排成一行；
 * - 全屏：导航条与选集网格都不在组合树里，九个动作只能由底栏给，排成第二行。
 *
 * 两种形态共用同一套按钮组件与同一份播放状态，切换全屏时只是这一块重新组合。
 */
@Composable
private fun PlayerBottomControls(
    playback: PlayerPlaybackState,
    fullscreen: Boolean,
    enabled: Boolean,
    danmakuEnabled: Boolean,
    videoScale: PlayerVideoScale,
    speedMenuOpen: Boolean,
    scaleMenuOpen: Boolean,
    canPlayPrevious: Boolean,
    canPlayNext: Boolean,
    insets: WindowInsets,
    onScrubbingChange: (Boolean) -> Unit,
    onSpeedMenuChange: (Boolean) -> Unit,
    onScaleMenuChange: (Boolean) -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onDanmakuToggle: () -> Unit,
    onDanmakuSettingsClick: () -> Unit,
    onVideoScaleChange: (PlayerVideoScale) -> Unit,
    onEpisodesClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeStyle = MaterialTheme.typography.labelMedium.copy(
        color = Color.White,
        // 等宽数字：秒数变化时文字宽度不跳。
        fontFeatureSettings = "tnum",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BottomScrim)
            .windowInsetsPadding(insets),
    ) {
        if (fullscreen) {
            FullscreenControls(
                playback = playback,
                enabled = enabled,
                danmakuEnabled = danmakuEnabled,
                videoScale = videoScale,
                speedMenuOpen = speedMenuOpen,
                scaleMenuOpen = scaleMenuOpen,
                canPlayPrevious = canPlayPrevious,
                canPlayNext = canPlayNext,
                timeStyle = timeStyle,
                onScrubbingChange = onScrubbingChange,
                onSpeedMenuChange = onSpeedMenuChange,
                onScaleMenuChange = onScaleMenuChange,
                onPreviousEpisode = onPreviousEpisode,
                onNextEpisode = onNextEpisode,
                onDanmakuToggle = onDanmakuToggle,
                onDanmakuSettingsClick = onDanmakuSettingsClick,
                onVideoScaleChange = onVideoScaleChange,
                onEpisodesClick = onEpisodesClick,
                onToggleFullscreen = onToggleFullscreen,
            )
        } else {
            CompactControls(
                playback = playback,
                enabled = enabled,
                onScrubbingChange = onScrubbingChange,
                onToggleFullscreen = onToggleFullscreen,
            )
        }
    }
}

/**
 * 小屏（页面内嵌播放器）的控制条：**一行**。
 *
 * `[▶] 00:32 ────●────── 25:40 [⛶]`
 *
 * 小屏刻意只给这些：播放器只有 16:9 那么高（页高的 45% 以内），多一行按钮会吃掉近一半画面；
 * 而上下集、弹幕、换源在正下方的导航条与选集网格里都已经有了，重复一遍反而挤。
 * 需要完整控制就进全屏——那里才铺两行。
 */
@Composable
private fun CompactControls(
    playback: PlayerPlaybackState,
    enabled: Boolean,
    onScrubbingChange: (Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    // 小屏这一行是「一眼看清播到哪」，时间用 labelLarge 而不是全屏那行的 labelMedium：
    // 播放器在页面里只有一小块，字号跟着缩就很难读。
    val timeStyle = MaterialTheme.typography.labelLarge.copy(
        color = Color.White,
        fontFeatureSettings = "tnum",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayPauseButton(
            playback = playback,
            enabled = enabled,
            // 小屏不放底块：这一行只有播放键一个主动作，实心块反而把画面切碎。
            containerColor = Color.Transparent,
            iconSize = 30.dp,
        )
        TimeText(playback = playback, isDuration = false, style = timeStyle)
        PlayerSeekBar(
            playback = playback,
            enabled = enabled,
            onScrubbingChange = onScrubbingChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        TimeText(playback = playback, isDuration = true, style = timeStyle)
        ControlIconButton(
            imageVector = Icons.Rounded.Fullscreen,
            contentDescription = stringResource(R.string.player_fullscreen),
            enabled = enabled,
            onClick = onToggleFullscreen,
            containerColor = Color.Transparent,
        )
    }
}

/**
 * 全屏控制条：**两行**。
 *
 * ```
 * 00:32 ──────●────────────── 25:40
 * [⏮] [▶] [⏭]  [弹] [弹⚙]       1.0x [⛶] [☰] [⤢]
 * ```
 *
 * 第一行只放时间与进度：`positionMillis` 由进度条在绘制阶段读，这一行播放时不重组。
 *
 * 九个按钮全出，是因为全屏时动作栏（导航条）与选集网格都不在组合树里——
 * 弹幕、填充方式、剧集入口只能由这一行提供。
 */
@Composable
private fun FullscreenControls(
    playback: PlayerPlaybackState,
    enabled: Boolean,
    danmakuEnabled: Boolean,
    videoScale: PlayerVideoScale,
    speedMenuOpen: Boolean,
    scaleMenuOpen: Boolean,
    canPlayPrevious: Boolean,
    canPlayNext: Boolean,
    timeStyle: TextStyle,
    onScrubbingChange: (Boolean) -> Unit,
    onSpeedMenuChange: (Boolean) -> Unit,
    onScaleMenuChange: (Boolean) -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onDanmakuToggle: () -> Unit,
    onDanmakuSettingsClick: () -> Unit,
    onVideoScaleChange: (PlayerVideoScale) -> Unit,
    onEpisodesClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .padding(bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeText(playback = playback, isDuration = false, style = timeStyle)
            PlayerSeekBar(
                playback = playback,
                enabled = enabled,
                onScrubbingChange = onScrubbingChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
            TimeText(playback = playback, isDuration = true, style = timeStyle)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        ) {
            // 宽度不够时收起中间几枚：分屏/小窗里全屏宽度可能只有 360dp，
            // 硬排会把「退出全屏」挤到屏幕外（用户就只剩手势可退）。
            val showAllActions = maxWidth >= FullActionsMinWidth

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlIconButton(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.player_previous_episode),
                    enabled = enabled && canPlayPrevious,
                    onClick = onPreviousEpisode,
                )
                ControlGapSpacer()
                PlayPauseButton(playback = playback, enabled = enabled)
                ControlGapSpacer()
                ControlIconButton(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.player_next_episode),
                    enabled = enabled && canPlayNext,
                    onClick = onNextEpisode,
                )

                if (showAllActions) {
                    Spacer(modifier = Modifier.width(GroupGap))
                    ControlIconButton(
                        painter = painterResource(
                            if (danmakuEnabled) R.drawable.ic_danmaku_on else R.drawable.ic_danmaku_off,
                        ),
                        contentDescription = stringResource(R.string.player_danmaku),
                        active = danmakuEnabled,
                        enabled = enabled,
                        onClick = onDanmakuToggle,
                    )
                    ControlGapSpacer()
                    ControlIconButton(
                        painter = painterResource(R.drawable.ic_danmaku_setting),
                        contentDescription = stringResource(R.string.player_danmaku_settings),
                        enabled = enabled,
                        onClick = onDanmakuSettingsClick,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                SpeedChip(
                    playback = playback,
                    enabled = enabled,
                    expanded = speedMenuOpen,
                    onExpandedChange = onSpeedMenuChange,
                )

                if (showAllActions) {
                    ControlGapSpacer()
                    VideoScaleMenu(
                        current = videoScale,
                        enabled = enabled,
                        expanded = scaleMenuOpen,
                        onSelect = onVideoScaleChange,
                        onExpandedChange = onScaleMenuChange,
                    )
                    ControlGapSpacer()
                    ControlIconButton(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = stringResource(R.string.player_episode_picker),
                        enabled = enabled,
                        onClick = onEpisodesClick,
                    )
                }

                ControlGapSpacer()
                ControlIconButton(
                    imageVector = Icons.Rounded.FullscreenExit,
                    contentDescription = stringResource(R.string.player_exit_fullscreen),
                    enabled = enabled,
                    onClick = onToggleFullscreen,
                )
            }
        }
    }
}

@Composable
private fun ControlGapSpacer() {
    Spacer(modifier = Modifier.width(ControlGap))
}

/**
 * 控制行按钮的公共外壳：可见方块 = 触控区。
 *
 * 不用 `IconButton`：它的最小热区是 48dp，在 40dp 的可见方块外会多出一圈抢事件的空白，
 * 相邻按钮之间连点两下就会错按。这里 [Box] + `clickable` 让热区与视觉严格一致。
 */
@Composable
private fun ControlSlot(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = ControlContainer,
    shape: Shape = ControlShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ControlIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
    containerColor: Color? = null,
) {
    ControlSlot(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(ControlSize),
        containerColor = containerColor ?: if (active) ActiveControlContainer else ControlContainer,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(ControlIconSize),
        )
    }
}

@Composable
private fun ControlIconButton(
    painter: Painter,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    ControlSlot(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(ControlSize),
        containerColor = if (active) ActiveControlContainer else ControlContainer,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(ControlIconSize + 2.dp),
        )
    }
}

/** 开启态按钮底：与导航条的 [PlayerNavigationBar] 同一套值（主色 16% + 主色图标）。 */
private val ActiveControlContainer: Color
    @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)

/**
 * 播放/暂停键：控制行里唯一的主操作，默认底色比同排按钮亮一档。
 *
 * 单独成一个 composable 是为了把 `isPlaying` 的读取范围收到这里：
 * 切换播放状态只重组这个按钮，不牵连同一条控制行上的其它按钮。
 *
 * 小屏那行传 `containerColor = Color.Transparent` + 更大的 [iconSize]：
 * 一行里只有它一个动作，不要底块，图标就是按钮。
 */
@Composable
private fun PlayPauseButton(
    playback: PlayerPlaybackState,
    enabled: Boolean,
    containerColor: Color = ProminentControlContainer,
    iconSize: Dp = ControlIconSize + 4.dp,
) {
    val isPlaying = playback.isPlaying
    ControlSlot(
        onClick = playback::togglePlayPause,
        enabled = enabled,
        modifier = Modifier.size(ControlSize),
        containerColor = containerColor,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(
                if (isPlaying) R.string.player_pause else R.string.player_play,
            ),
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * 倍速胶囊：正文就是当前档位，点开才给选项。
 *
 * 速度读在**这个胶囊内部**：改速度只重组这一小块，控制行其它按钮集体跳过。
 * 展开状态由父级持有（见 `PlayerSurface` 里 `speedMenuOpen` 的注释）。
 */
@Composable
private fun SpeedChip(
    playback: PlayerPlaybackState,
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val speed = playback.speed

    Box {
        ControlSlot(
            onClick = { onExpandedChange(true) },
            enabled = enabled,
            modifier = Modifier.height(ControlSize),
        ) {
            Text(
                text = stringResource(R.string.player_speed_value, formatSpeed(speed)),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color.White,
                    // 0.75x ↔ 1.0x 切换时胶囊宽度不跳。
                    fontFeatureSettings = "tnum",
                ),
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            SpeedOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.player_speed_value, formatSpeed(option)))
                    },
                    onClick = {
                        playback.setPlaybackSpeed(option)
                        onExpandedChange(false)
                    },
                    trailingIcon = {
                        if (option == speed) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.player_speed_current),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * 画面填充方式：图标入口 + 三选一菜单。
 *
 * 不做「点一下循环切换」：三档里有两档平时不用，循环切换会让人为了回到默认多点两次，
 * 而且看不出还有哪些档位。
 */
@Composable
private fun VideoScaleMenu(
    current: PlayerVideoScale,
    enabled: Boolean,
    expanded: Boolean,
    onSelect: (PlayerVideoScale) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    val currentLabel = stringResource(current.labelRes)

    Box {
        ControlSlot(
            onClick = { onExpandedChange(true) },
            enabled = enabled,
            modifier = Modifier.size(ControlSize),
        ) {
            Icon(
                imageVector = Icons.Rounded.FitScreen,
                contentDescription = stringResource(R.string.player_scale_current, currentLabel),
                tint = Color.White,
                modifier = Modifier.size(ControlIconSize),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            PlayerVideoScale.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        onSelect(option)
                        onExpandedChange(false)
                    },
                    trailingIcon = {
                        if (option == current) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.player_scale_current, currentLabel),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * 时间文字。
 *
 * 先把毫秒压成「秒」再读：`positionMillis` 每 200ms 变一次，直接读会让这个
 * `Text` 每 200ms 重新排版一次；压成秒之后一秒最多重组一次，字符串也只在秒变化时才格式化。
 */
@Composable
private fun TimeText(
    playback: PlayerPlaybackState,
    isDuration: Boolean,
    style: TextStyle,
) {
    val seconds by remember(playback, isDuration) {
        derivedStateOf {
            val millis = if (isDuration) playback.durationMillis else playback.positionMillis
            millis / 1_000L
        }
    }
    Text(text = formatTime(seconds), style = style)
}

/**
 * 细轨道进度条。
 *
 * 不用 Material3 `Slider`：它自带 48dp 高度、厚轨道与独立 thumb 样式，
 * 在视频画面上比这里的细线重得多。绘制用 [Canvas]，手势用一次 `awaitEachGesture`
 * 同时覆盖「点击定位」与「按住拖动」——分成两个 pointerInput 会互相抢 down 事件。
 *
 * 两个性能细节：
 * - 宽度取 `PointerInputScope.size`，不写 state → 省掉一次「测量完回写状态 → 重组」；
 * - 进度在绘制块里读 → 拖动时只失效绘制，底栏与上方内容都不重组。
 */
@Composable
private fun PlayerSeekBar(
    playback: PlayerPlaybackState,
    enabled: Boolean,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .height(SeekBarHeight)
            .pointerInput(playback, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (size.width <= 0) return@awaitEachGesture
                    onScrubbingChange(true)
                    playback.seekToFraction(down.position.x / size.width)
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        playback.seekToFraction(change.position.x / size.width)
                        change.consume()
                    }
                    onScrubbingChange(false)
                }
            },
    ) {
        val centerY = size.height / 2f
        val trackHeight = 3.dp.toPx()
        val corner = CornerRadius(trackHeight / 2f)
        val top = centerY - trackHeight / 2f

        // 缓存进度：放在这里读，拖动时只失效绘制。
        val progress = playback.progressFraction
        val buffered = if (playback.durationMillis > 0L) {
            (playback.bufferedMillis.toFloat() / playback.durationMillis).coerceIn(0f, 1f)
        } else {
            0f
        }

        // 底色 → 缓冲 → 已播放，三层同高叠画，比三条独立轨道省两次绘制调用。
        drawRoundRect(
            color = Color.White.copy(alpha = 0.26f),
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight),
            cornerRadius = corner,
        )
        if (buffered > progress) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.42f),
                topLeft = Offset(0f, top),
                size = Size(size.width * buffered, trackHeight),
                cornerRadius = corner,
            )
        }
        val playedWidth = size.width * progress
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(0f, top),
            size = Size(playedWidth, trackHeight),
            cornerRadius = corner,
        )
        drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(playedWidth, centerY))
    }
}

/** 秒 → `mm:ss`（超过一小时按 `h:mm:ss`）。 */
private fun formatTime(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3_600
    val minutes = safeSeconds % 3_600 / 60
    val seconds = safeSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.pad2()}:${seconds.pad2()}"
    } else {
        "${minutes.pad2()}:${seconds.pad2()}"
    }
}

private fun Long.pad2(): String = if (this < 10) "0$this" else toString()

/**
 * 速度值 → `1.0` / `0.75`。
 *
 * 小数位跟着数值走：整十的百分位（1.0、1.5、2.0）只留一位，否则「1.0x → 1.25x」时
 * 胶囊宽度会跳。`Locale.ROOT` 保证小数点永远是「.」，不随系统语言变成逗号。
 */
private fun formatSpeed(speed: Float): String {
    val decimals = if ((speed * 100f).toInt() % 10 == 0) 1 else 2
    return String.format(Locale.ROOT, "%.${decimals}f", speed)
}
