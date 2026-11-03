package com.liomix.mikomi.feature.player.component

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.liomix.mikomi.core.media.MikomiPlayer
import com.liomix.mikomi.feature.player.PlaybackMode
import com.liomix.mikomi.feature.player.PlayerFailure
import com.liomix.mikomi.feature.player.PlayerFailureReason
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
 * 画面是 `Media3` 的 `PlayerView`（[PlayerVideo]）；加载与失败是压在上面的一层状态，
 * **不替换**画面那一层——换掉等于把 Surface 销毁重建，每次出错重试都黑一下。
 */
@Composable
internal fun PlayerSurface(
    title: String,
    subtitle: String,
    fullscreen: Boolean,
    mediaPlayer: MikomiPlayer?,
    playback: PlayerPlaybackState,
    loading: Boolean,
    failure: PlayerFailure?,
    mode: PlaybackMode,
    webUrl: String?,
    webReloadToken: Int,
    webUserAgent: String?,
    /** 不可见解析正在加载的播放页；非空即「解析中」，画面被遮罩全盖住。 */
    sniffUrl: String?,
    sniffUserAgent: String?,
    /** 失败时是否给出「用网页播放」这个出口。 */
    webPageAvailable: Boolean,
    onRetry: () -> Unit,
    onRequestFullscreen: (Boolean) -> Unit,
    onWebSubRequest: (pageKey: String, url: String, headers: Map<String, String>, cookie: String?, trusted: Boolean) -> Unit,
    onPlayInWebPage: () -> Unit,
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

    // 顶栏在两套布局里都要用：原生/解析时它浮在画面上，网页播放时它是独立的一行。
    // 提成 lambda 是因为参数太长，两处各写一遍容易改漏一处。
    val topBar: @Composable (Modifier) -> Unit = { barModifier ->
        PlayerTopOverlay(
            title = title,
            subtitle = subtitle,
            enabled = controlsVisible,
            insets = topInsets,
            fullscreen = fullscreen,
            webMode = mode == PlaybackMode.WEB,
            onBackClick = onBackClick,
            onEpisodesClick = onEpisodesClick,
            onToggleFullscreen = onToggleFullscreen,
            onPlayerSettingsClick = onPlayerSettingsClick,
            modifier = barModifier,
        )
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            // 网页形态下把整块画面的点击让给站点播放器：它有自己的播放键与进度条，
            // 我们再用「点画面显隐」去抢手势，用户就点不动它了。控制层在该形态下常显。
            .then(
                if (mode == PlaybackMode.NATIVE && sniffUrl == null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { controlsVisible = !controlsVisible }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        if (mode == PlaybackMode.WEB && webUrl != null) {
            // 网页播放：顶栏与网页**分区**，不是叠层。
            //
            // 叠层在这里根本行不通：WebView 是真实的 Android View，永远绘制在 Compose 内容
            // 之上，任何 Compose 控制层都盖不住它——表现就是顶栏看着在、点不到，
            // 用户进了网页就退不出来。放进 Column 当兄弟节点之后，顶栏位于 WebView 的
            // 布局区域**之外**，天然不受它影响。
            Column(modifier = Modifier.fillMaxSize()) {
                topBar(Modifier.fillMaxWidth())
                PlayerWebView(
                    url = webUrl,
                    reloadToken = webReloadToken,
                    userAgent = webUserAgent,
                    muted = false,
                    hidden = false,
                    onRequestFullscreen = onRequestFullscreen,
                    onSubRequest = onWebSubRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        } else {
            // 原生 / 解析：画面满屏，控制层浮在上面。
            //
            // `PlayerVideo` 常驻，**不要**把它和下面的 WebView 做成互斥分支。那样嗅探成功的
            // 那一刻才创建 `PlayerView`，还要等它 attach Surface、解码出第一帧——
            // 中间是一块纯黑，用户看到的就是「解析中 → 黑一下 → 开始播」。
            PlayerVideo(
                mediaPlayer = mediaPlayer,
                videoScale = videoScale,
                modifier = Modifier.fillMaxSize(),
            )

            // 解析用 WebView：真的存在、真的加载、站点播放器真的起播，但**一个像素都不画**。
            //
            // `hidden` 不是可选的：它是真实的 Android View，绘制在所有 Compose 内容之上，
            // 不藏起来的话上面那层 SniffCover 根本盖不住它——页面加载完就会从遮罩底下
            // 透出来，这正是「解析时闪一下播放页」的成因。
            if (sniffUrl != null) {
                PlayerWebView(
                    url = sniffUrl,
                    reloadToken = webReloadToken,
                    userAgent = sniffUserAgent,
                    // 盖着就必须静音：否则黑底转圈里已经响起了片头曲。
                    muted = true,
                    hidden = true,
                    // 解析期间**不接**站点的全屏请求：那个 view 挂上来就会顶到遮罩前面，
                    // 用户莫名其妙进了一个陌生播放器的全屏。真正的全屏等切原生再说。
                    onRequestFullscreen = {},
                    onSubRequest = onWebSubRequest,
                    modifier = Modifier.fillMaxSize(),
                )
                // 遮罩一直盖到第一帧真的画出来为止（见 ViewModel 的 `releaseSniffCover`），
                // 所以它不会自己消失，也不会漏出一刻网页。
                SniffCover(modifier = Modifier.fillMaxSize())
            }

            PlayerStatusOverlay(
                loading = loading,
                failure = failure,
                controlsVisible = controlsVisible,
                onRetry = onRetry,
                playback = playback,
                webPageAvailable = webPageAvailable,
                onPlayInWebPage = onPlayInWebPage,
                // 缓冲是 Media3 的概念：网页形态下 Media3 已停播，它不会再回调，
                // 转圈会永远留在画面上盖着站点播放器。解析期间同理。
                nativeMode = mode == PlaybackMode.NATIVE && sniffUrl == null,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = controlsAlpha.value },
            ) {
                topBar(Modifier.align(Alignment.TopCenter))

                // 底栏只给原生形态。
                // 网页形态下**不留底栏**：站点播放器的控制条就贴在这个位置，
                // 我们压一条上去，它的进度条和音量键就点不动了（用户会觉得"这网站播放器坏了"）。
                // 归我们管的「选集 / 全屏」挪到顶栏，那一行本来就是我们的。
                // 解析期间也不给：还没有画面可控制，摆一排按钮只会让人以为点了没反应。
                if (mode == PlaybackMode.NATIVE && sniffUrl == null) {
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
    }
}

/**
 * 画面本体：`PlayerView` 里装 ExoPlayer。
 *
 * 三件事必须在这里做：
 * - `useController = false`——控制层是我们自己的，PlayerView 自带的控制器会盖住底栏，
 *   还会抢走「点画面显隐」的手势；
 * - `resizeMode` 跟着 [PlayerVideoScale] 走（填充方式就是它）；
 * - `onRelease` 里把 player 置空——PlayerView 持有 Surface，不置空会跟着 Activity 泄漏。
 *
 * 注意不要给这个 AndroidView 加 `key`，也不要在别处再组合一份：
 * Surface 一旦重建，观感就是切全屏/出错重试时黑一帧。
 */
@Composable
private fun PlayerVideo(
    mediaPlayer: MikomiPlayer?,
    videoScale: PlayerVideoScale,
    modifier: Modifier = Modifier,
) {
    if (mediaPlayer == null) {
        // 播放器还没建好（极短的一瞬）：先铺与画面同色的底，避免白闪。
        Box(modifier.background(PlaceholderFrame))
        return
    }
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                player = mediaPlayer.exoPlayer
            }
        },
        update = { view ->
            view.resizeMode = videoScale.resizeMode
            if (view.player !== mediaPlayer.exoPlayer) view.player = mediaPlayer.exoPlayer
        },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

/**
 * 不可见解析的遮罩。
 *
 * 存在的理由：站点播放器**不是我们的界面**。底下那个 WebView 必须真实加载并起播
 * （嗅探就靠它发出媒体请求），但一进来就看见一个陌生网站的播放器，用户的第一反应是
 * 「这不是我要的播放器」。所以整块盖住，只留一句「正在解析播放地址」。
 *
 * 这一层**不会自己消失**：拦到流 → 整个 WebView 一起被换掉；超时 → 换成失败与重试。
 * 早先的做法是「等 6 秒自动撤掉露出网页」，那正是「播放前弹网页」的来源，已经删了。
 *
 * 必须吃掉点击：不吃的话用户会隔着遮罩点到下面的播放器，画面黑着却有声音，更莫名其妙。
 */
@Composable
private fun SniffCover(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.player_web_sniffing),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
    }
}

/**
 * 画面上的状态层：正在出流 / 播不出来。
 *
 * 只在有话说的时候出现，平时是个空 Box——它盖在画面上，但自己不拦截触摸，
 * 所以「点一下显隐控制层」照常生效。
 */
@Composable
private fun PlayerStatusOverlay(
    loading: Boolean,
    failure: PlayerFailure?,
    controlsVisible: Boolean,
    onRetry: () -> Unit,
    playback: PlayerPlaybackState,
    webPageAvailable: Boolean,
    onPlayInWebPage: () -> Unit,
    nativeMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            failure != null -> Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(failure.reason.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                failure.detail?.let { detail ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.player_retry))
                    }
                    // 「用网页播放」是**退出**主路径的选择，画成次按钮：不抢重试的位置，
                    // 但必须在失败这一屏给出来——否则那类「要人点一下才起播」的站就完全没路走了。
                    if (webPageAvailable) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onPlayInWebPage) {
                            Text(stringResource(R.string.player_web_play), color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.player_resolving),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }

            // 缓冲转圈跟着控制层一起淡入淡出：控制层收起时画面干净，不留一个孤零零的圆环。
            else -> BufferingIndicator(playback = playback, visible = controlsVisible && nativeMode)
        }
    }
}

/** 起播/卡顿时的缓冲提示。单独一个 composable：只有它读 buffering，不牵连整层。 */
@Composable
private fun BufferingIndicator(playback: PlayerPlaybackState, visible: Boolean) {
    if (!visible || !playback.isBuffering) return
    CircularProgressIndicator(
        modifier = Modifier.size(26.dp),
        strokeWidth = 2.dp,
        color = Color.White.copy(alpha = 0.9f),
    )
}

/** 播不出来的原因 → 一句人话。播放页语境：告诉用户下一步能做什么。 */
@StringRes
private fun PlayerFailureReason.messageRes(): Int = when (this) {
    PlayerFailureReason.NoSource -> R.string.player_error_no_source
    PlayerFailureReason.NoTitle -> R.string.player_error_no_title
    PlayerFailureReason.NoMatch -> R.string.player_error_no_match
    PlayerFailureReason.RuleBroken -> R.string.player_error_rule_broken
    PlayerFailureReason.Network -> R.string.player_error_network
    PlayerFailureReason.NoEpisodes -> R.string.player_error_no_episodes
    PlayerFailureReason.NoStream -> R.string.player_error_no_stream
    PlayerFailureReason.NeedWebView -> R.string.player_error_need_webview
    PlayerFailureReason.Playback -> R.string.player_error_playback
}

/** 填充方式 → Media3 的缩放模式。三档一一对应，不做别的推断。 */
private val PlayerVideoScale.resizeMode: Int
    get() = when (this) {
        PlayerVideoScale.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        PlayerVideoScale.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        PlayerVideoScale.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

/**
 * 顶栏：返回 + 标题 + 当前集 + 右侧动作。
 *
 * 标题列用 `weight(1f)` 而不是 `fillMaxWidth`：右侧动作要独占位置，
 * 长标题只在自己那一段里省略，不会把图标挤出屏幕。
 *
 * 右侧动作按形态分两套：
 * - 原生：只留「更多」（播放器设置），其余动作在底栏；
 * - WebView：换成「选集 + 全屏」——这两个是**归我们管**的，而播放/进度/音量全部归站点播放器，
 *   把那套塞进底栏会正好压在它的控制条上。
 */
@Composable
private fun PlayerTopOverlay(
    title: String,
    subtitle: String,
    enabled: Boolean,
    insets: WindowInsets,
    fullscreen: Boolean,
    webMode: Boolean,
    onBackClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
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
        if (webMode) {
            IconButton(onClick = onEpisodesClick, enabled = enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = stringResource(R.string.player_episode_picker),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onToggleFullscreen, enabled = enabled) {
                Icon(
                    imageVector = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = stringResource(
                        if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen,
                    ),
                    tint = Color.White,
                )
            }
        } else {
            IconButton(onClick = onPlayerSettingsClick, enabled = enabled) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.player_settings),
                    tint = Color.White,
                )
            }
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
