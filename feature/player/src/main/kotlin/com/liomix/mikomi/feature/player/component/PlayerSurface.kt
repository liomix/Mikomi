package com.liomix.mikomi.feature.player.component

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiMedia
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.spec.MikomiPlayerSpec
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiStroke
import com.liomix.mikomi.core.designsystem.theme.MikomiTextStyles
import com.liomix.mikomi.core.media.MikomiPlayer
import com.liomix.mikomi.core.model.PlayerPlaybackSettings
import com.liomix.mikomi.core.ui.component.base.MikomiIcon
import com.liomix.mikomi.feature.player.PlaybackMode
import com.liomix.mikomi.feature.player.PlayerFailure
import com.liomix.mikomi.feature.player.PlayerFailureReason
import com.liomix.mikomi.feature.player.PlayerPlaybackState
import com.liomix.mikomi.feature.player.PlayerVideoScale
import com.liomix.mikomi.feature.player.R
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * 控制条的尺寸规格（进度条热区、按钮、间距、最小宽度）都在 `MikomiPlayerSpec` 里。
 *
 * 它们比页面元素更密：一行要塞下上下集 / 播放 / 弹幕 / 倍速 / 填充 / 集数 / 全屏七个动作，
 * 只能靠 40dp 按钮加 6dp 间隙换宽度。页面上的按钮仍然是 48dp，两者不通用 ——
 * 这正是规格该按组件分家、而间距才该按角色共用的原因。
 */
private val ControlShape = RoundedCornerShape(MikomiRadius.Md)


/** 按钮底：视频画面上不用主题 surface（夜间会被压成黑色，跟画面糊在一起）。 */
private val ControlContainer = MikomiMedia.surface

/**
 * 主操作（播放/暂停）的底：比同排按钮亮一档。
 *
 * 不用主题 primary 做实心块：这一行是压在画面上的单色控制条，插一个彩色按钮会抢掉
 * 画面本身；「亮一档」既有层级又不会跳出这套白字遮罩。主色只留给**状态**（弹幕开启）。
 */
private val ProminentControlContainer = MikomiMedia.surfaceProminent

/** 不可用按钮的透明度。控制层整体隐藏时父级 alpha 已经是 0，这里的 0.38 不会露出来。 */
private const val DISABLED_ALPHA = 0.38f

/** 播放中控制层自动隐藏延时。 */
private const val CONTROLS_HIDE_DELAY_MILLIS = 3_500L

private val SpeedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

// 遮罩与占位画面都是常量：写成顶层值，组合时一次都不再分配。
// 之前是在组合里现算 `Brush.verticalGradient(...)`，虽然只是小对象，但每帧重组都在扔垃圾。
private val TopScrim = Brush.verticalGradient(
    colors = listOf(MikomiMedia.scrimSoft, Color.Transparent),
)
private val BottomScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, MikomiMedia.scrim),
)
private val PlaceholderFrame = Brush.linearGradient(
    colors = listOf(MikomiMedia.backdropTop, MikomiMedia.backdropBottom),
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
    /** 手势与倍速偏好（默认倍速 / 长按倍速 / 滑动幅度）。 */
    settings: PlayerPlaybackSettings,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    // 锁屏：把画面上的按钮与手势全部关掉，只留解锁入口。
    // 全屏才有意义——内嵌播放器占的是页面的一角，锁了也没人误触。
    var locked by remember { mutableStateOf(false) }
    // 两个下拉菜单的展开状态放在这里而不是各自的胶囊内部：
    // 它们是「控制条整体」的状态，且要参与下面的自动隐藏判断——放进胶囊会随组合位置变化丢失，
    // 一旦丢失就再没人回调「关闭」，控制层将永远不再自动隐藏。
    var speedMenuOpen by remember { mutableStateOf(false) }
    var scaleMenuOpen by remember { mutableStateOf(false) }
    // 用户正在跟控制层较劲（拖进度条、开着菜单）：此时不能自动隐藏。
    val interactionPinned = scrubbing || speedMenuOpen || scaleMenuOpen

    // 填充方式是纯视图状态：只影响画面怎么铺，不参与业务。
    var videoScale by rememberSaveable { mutableStateOf(PlayerVideoScale.AUTO) }

    // 亮度是窗口属性，不是播放器状态：手势改它、离开页面时还原（见 PlayerBrightnessController）。
    val brightness = rememberPlayerBrightness()

    // 只返回 State，读取延迟到绘制阶段（见文件头第 1 条）。
    // 锁屏时控制层整体退场：alpha 走同一个动画，解锁会自然淡回来。
    val controlsAlpha = animateFloatAsState(
        targetValue = if (controlsVisible && !locked) 1f else 0f,
        animationSpec = tween(durationMillis = if (controlsVisible && !locked) 150 else 220),
        label = "playerControlsAlpha",
    )

    // 退全屏时解锁：回到页面里还锁着，用户会以为播放器坏了（按钮全不见、点也没反应）。
    LaunchedEffect(fullscreen) {
        if (!fullscreen) locked = false
    }

    // 锁上就把控制层收起来：锁屏的意思就是「别让手指碰到任何东西」。
    LaunchedEffect(locked) {
        if (locked) {
            controlsVisible = false
            // 开着的菜单必须关掉，否则它们浮在锁屏之上，等于留了一个后门。
            speedMenuOpen = false
            scaleMenuOpen = false
        }
    }

    // 播放中 3.5s 自动隐藏。isPlaying 走 snapshotFlow 读：
    // 直接读进组合的话，每次播放/暂停都会让整块画面重组。
    LaunchedEffect(controlsVisible, interactionPinned, playback, locked) {
        if (locked || !controlsVisible || interactionPinned) return@LaunchedEffect
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
            .background(MikomiMedia.void),
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

            // 手势层贴着画面、压在所有控制层之下：按钮（顶栏 / 底栏 / 锁）在它上面，
            // 所以点按钮永远优先于「点画面显隐」。网页形态不给：站点播放器有自己的
            // 播放键与进度条，抢它的手势用户就点不动它了。
            PlayerGestureLayer(
                playback = playback,
                settings = settings,
                brightness = brightness,
                // 解析期间画面还没有东西可控制，手势一起停掉，免得误触发音量/亮度；
                // 失败页也停掉：那一屏只有「重试 / 用网页播放」两个按钮该响应。
                enabled = mode == PlaybackMode.NATIVE && sniffUrl == null && !locked && failure == null,
                onTap = { controlsVisible = !controlsVisible },
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

            // 锁屏按钮：画面右侧垂直居中，只在全屏给。
            // 内嵌播放器占的是页面的一角，锁了没有任何意义（下面的选集与导航还在那儿）。
            // 它**压在控制层之上**：锁上之后控制层整体退场，这枚按钮必须自己留在屏幕上，
            // 否则用户没有解锁的入口。
            if (fullscreen && mode == PlaybackMode.NATIVE && sniffUrl == null) {
                PlayerLockButton(
                    locked = locked,
                    visible = controlsVisible || locked,
                    onToggle = {
                        val unlock = locked
                        locked = unlock.not()
                        // 解锁的同时把控制层带回来：用户点锁是为了继续用播放器，
                        // 解锁后还要再点一下画面才看见按钮，会以为没解开。
                        if (unlock) controlsVisible = true
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = MikomiSpacing.Tight),
                )
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
 * 「这不是我要的播放器」。所以整块盖住，只留一句「正在加载」——
 * 用户不需要知道我们在解析地址还是嗅探流，**文案只说结果，不说实现**。
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
            .background(MikomiMedia.void)
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
                strokeWidth = MikomiStroke.Thick,
                color = MikomiMedia.content,
            )
            Spacer(modifier = Modifier.height(MikomiSpacing.Compact))
            Text(
                text = stringResource(R.string.player_web_sniffing),
                style = MaterialTheme.typography.labelMedium,
                color = MikomiMedia.contentSecondary,
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
                modifier = Modifier.padding(horizontal = MikomiSpacing.Xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MikomiIcon(
                    icon = MikomiIcons.Error,
                    contentDescription = null,
                    tint = MikomiMedia.contentSecondary,
                    modifier = Modifier.size(MikomiSizes.IconMd),
                )
                Spacer(modifier = Modifier.height(MikomiSpacing.Sm))
                Text(
                    text = stringResource(failure.reason.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MikomiMedia.content,
                    textAlign = TextAlign.Center,
                )
                // 这里**不显示** `failure.detail`。它装的是播放页地址或 XPath 字段名，
                // 是给开发者看的调试信息：一长串 URL 摆在用户面前既看不懂，又把这一屏撑得很难看。
                // 需要排查的时候去看日志，别让用户替我们读堆栈。
                Spacer(modifier = Modifier.height(MikomiSpacing.Md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 失败态的两个按钮做小一号（32dp / labelMedium）：
                    // 它们是「接着怎么办」的出口，不是这一屏的主角，不该比一句话还抢眼。
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = MikomiPlayerSpec.SmallButtonHorizontalPadding),
                    ) {
                        Text(
                            text = stringResource(R.string.player_retry),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    // 「用网页播放」是**退出**主路径的选择，画成次按钮：不抢重试的位置，
                    // 但必须在失败这一屏给出来——否则那类「要人点一下才起播」的站就完全没路走了。
                    if (webPageAvailable) {
                        Spacer(modifier = Modifier.width(MikomiSpacing.Xs))
                        TextButton(
                            onClick = onPlayInWebPage,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = MikomiSpacing.Md),
                        ) {
                            Text(
                                text = stringResource(R.string.player_web_play),
                                style = MaterialTheme.typography.labelMedium,
                                color = MikomiMedia.contentSecondary,
                            )
                        }
                    }
                }
            }

            loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = MikomiStroke.Thick,
                    color = MikomiMedia.content,
                )
                Spacer(modifier = Modifier.height(MikomiSpacing.Compact))
                Text(
                    text = stringResource(R.string.player_resolving),
                    style = MaterialTheme.typography.labelMedium,
                    color = MikomiMedia.contentSecondary,
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
        strokeWidth = MikomiStroke.Thick,
        color = MikomiMedia.content,
    )
}

/**
 * 锁屏按钮：右侧垂直居中。
 *
 * 锁定后**只有它留在画面上**，其余按钮与手势全部关掉——这就是「把手机递给孩子 / 放兜里
 * 继续听」时想要的效果：怎么碰都不会跳进度、不会改音量、不会退出全屏。
 *
 * 锁定时不隐藏而是压到 55% 透明：完全藏起来用户就找不到解锁的地方了。
 */
@Composable
private fun PlayerLockButton(
    locked: Boolean,
    visible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            !visible -> 0f
            locked -> 0.55f
            else -> 1f
        },
        animationSpec = tween(durationMillis = MikomiMotion.Duration.Short),
        label = "playerLockAlpha",
    )
    ControlSlot(
        onClick = onToggle,
        enabled = visible,
        modifier = modifier
            .size(MikomiPlayerSpec.ControlSize)
            .graphicsLayer { this.alpha = alpha },
        containerColor = if (locked) MikomiMedia.surfaceProminent else ControlContainer,
    ) {
        MikomiIcon(
            icon = if (locked) MikomiIcons.Lock else MikomiIcons.LockOpen,
            contentDescription = stringResource(
                if (locked) R.string.player_unlock else R.string.player_lock,
            ),
            tint = MikomiMedia.content,
            modifier = Modifier.size(MikomiSizes.IconSm),
        )
    }
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
            .padding(start = MikomiSpacing.Xs, end = MikomiSpacing.Xs, top = MikomiSpacing.Xxs, bottom = MikomiSpacing.Room),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick, enabled = enabled) {
            MikomiIcon(
                icon = MikomiIcons.Back,
                contentDescription = stringResource(R.string.player_back),
                tint = MikomiMedia.content,
            )
        }
        Spacer(modifier = Modifier.width(MikomiSpacing.Xs))
        Column(modifier = Modifier.weight(1f)) {
            // 全屏字号对齐 Dart-Mikomi 的全屏控制条：标题 18sp / 副标题 15sp，都 w600 / 0.8。
            // 用主题字号派生（copy 只改 font size 与行高），字族、字距、字重仍跟主题走，
            // 不在页面里另立一套排版常量。
            Text(
                text = title,
                style = if (fullscreen) {
                    MikomiTextStyles.MediaTitle
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.SemiBold,
                color = MikomiMedia.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(MikomiSpacing.Xxs))
                Text(
                    text = subtitle,
                    style = if (fullscreen) {
                        MikomiTextStyles.MediaSubtitle
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    color = MikomiMedia.contentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (webMode) {
            IconButton(onClick = onEpisodesClick, enabled = enabled) {
                MikomiIcon(
                    icon = MikomiIcons.Playlist,
                    contentDescription = stringResource(R.string.player_episode_picker),
                    tint = MikomiMedia.content,
                )
            }
            IconButton(onClick = onToggleFullscreen, enabled = enabled) {
                MikomiIcon(
                    icon = if (fullscreen) MikomiIcons.FullscreenExit else MikomiIcons.Fullscreen,
                    contentDescription = stringResource(
                        if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen,
                    ),
                    tint = MikomiMedia.content,
                )
            }
        } else {
            IconButton(onClick = onPlayerSettingsClick, enabled = enabled) {
                MikomiIcon(
                    icon = MikomiIcons.MoreVert,
                    contentDescription = stringResource(R.string.player_settings),
                    tint = MikomiMedia.content,
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
        color = MikomiMedia.content,
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
        color = MikomiMedia.content,
        fontFeatureSettings = "tnum",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MikomiSpacing.Xs, vertical = MikomiSpacing.Xxs),
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
                .padding(horizontal = MikomiSpacing.Compact),
        )
        TimeText(playback = playback, isDuration = true, style = timeStyle)
        ControlIconButton(
            imageVector = MikomiIcons.Fullscreen,
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
            .padding(horizontal = MikomiSpacing.Tight)
            .padding(bottom = MikomiSpacing.Xs),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MikomiSpacing.Tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeText(playback = playback, isDuration = false, style = timeStyle)
            PlayerSeekBar(
                playback = playback,
                enabled = enabled,
                onScrubbingChange = onScrubbingChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MikomiSpacing.Compact),
            )
            TimeText(playback = playback, isDuration = true, style = timeStyle)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MikomiSpacing.Xxs),
        ) {
            // 宽度不够时收起中间几枚：分屏/小窗里全屏宽度可能只有 360dp，
            // 硬排会把「退出全屏」挤到屏幕外（用户就只剩手势可退）。
            val showAllActions = maxWidth >= MikomiPlayerSpec.FullActionsMinWidth

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlIconButton(
                    imageVector = MikomiIcons.SkipPrevious,
                    contentDescription = stringResource(R.string.player_previous_episode),
                    enabled = enabled && canPlayPrevious,
                    onClick = onPreviousEpisode,
                )
                ControlGapSpacer()
                PlayPauseButton(playback = playback, enabled = enabled)
                ControlGapSpacer()
                ControlIconButton(
                    imageVector = MikomiIcons.SkipNext,
                    contentDescription = stringResource(R.string.player_next_episode),
                    enabled = enabled && canPlayNext,
                    onClick = onNextEpisode,
                )

                if (showAllActions) {
                    Spacer(modifier = Modifier.width(MikomiPlayerSpec.GroupGap))
                    ControlIconButton(
                        imageVector = if (danmakuEnabled) MikomiIcons.DanmakuOn else MikomiIcons.DanmakuOff,
                        contentDescription = stringResource(R.string.player_danmaku),
                        active = danmakuEnabled,
                        enabled = enabled,
                        onClick = onDanmakuToggle,
                    )
                    ControlGapSpacer()
                    ControlIconButton(
                        imageVector = MikomiIcons.DanmakuSetting,
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
                        imageVector = MikomiIcons.Playlist,
                        contentDescription = stringResource(R.string.player_episode_picker),
                        enabled = enabled,
                        onClick = onEpisodesClick,
                    )
                }

                ControlGapSpacer()
                ControlIconButton(
                    imageVector = MikomiIcons.FullscreenExit,
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
    Spacer(modifier = Modifier.width(MikomiPlayerSpec.ControlGap))
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
        modifier = Modifier.size(MikomiPlayerSpec.ControlSize),
        containerColor = containerColor ?: if (active) ActiveControlContainer else ControlContainer,
    ) {
        MikomiIcon(
            icon = imageVector,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary else MikomiMedia.content,
            size = MikomiSizes.IconSm,
        )
    }
}

@Composable
private fun ControlIconButton(
    painter: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    ControlSlot(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(MikomiPlayerSpec.ControlSize),
        containerColor = if (active) ActiveControlContainer else ControlContainer,
    ) {
        MikomiIcon(
            icon = painter,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary else MikomiMedia.content,
            size = MikomiPlayerSpec.ControlIconSize,
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
    iconSize: Dp = MikomiPlayerSpec.ControlIconSize + 4.dp,
) {
    val isPlaying = playback.isPlaying
    ControlSlot(
        onClick = playback::togglePlayPause,
        enabled = enabled,
        modifier = Modifier.size(MikomiPlayerSpec.ControlSize),
        containerColor = containerColor,
    ) {
        MikomiIcon(
            icon = if (isPlaying) MikomiIcons.Pause else MikomiIcons.Play,
            contentDescription = stringResource(
                if (isPlaying) R.string.player_pause else R.string.player_play,
            ),
            tint = MikomiMedia.content,
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
            modifier = Modifier.height(MikomiPlayerSpec.ControlSize),
        ) {
            Text(
                text = stringResource(R.string.player_speed_value, formatSpeed(speed)),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = MikomiMedia.content,
                    // 0.75x ↔ 1.0x 切换时胶囊宽度不跳。
                    fontFeatureSettings = "tnum",
                ),
                modifier = Modifier.padding(horizontal = MikomiSpacing.Compact),
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
                            MikomiIcon(
                                icon = MikomiIcons.Check,
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
            modifier = Modifier.size(MikomiPlayerSpec.ControlSize),
        ) {
            MikomiIcon(
                icon = MikomiIcons.FitScreen,
                contentDescription = stringResource(R.string.player_scale_current, currentLabel),
                tint = MikomiMedia.content,
                modifier = Modifier.size(MikomiSizes.IconSm),
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
                            MikomiIcon(
                                icon = MikomiIcons.Check,
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
            .height(MikomiPlayerSpec.SeekBarHeight)
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
            color = MikomiMedia.track,
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight),
            cornerRadius = corner,
        )
        if (buffered > progress) {
            drawRoundRect(
                color = MikomiMedia.trackBuffered,
                topLeft = Offset(0f, top),
                size = Size(size.width * buffered, trackHeight),
                cornerRadius = corner,
            )
        }
        val playedWidth = size.width * progress
        drawRoundRect(
            color = MikomiMedia.content,
            topLeft = Offset(0f, top),
            size = Size(playedWidth, trackHeight),
            cornerRadius = corner,
        )
        drawCircle(
            color = MikomiMedia.content,
            radius = 5.dp.toPx(),
            center = Offset(playedWidth, centerY),
        )
    }
}

/** 秒 → `mm:ss`（超过一小时按 `h:mm:ss`）。 */
internal fun formatTime(totalSeconds: Long): String {
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
internal fun formatSpeed(speed: Float): String {
    val decimals = if ((speed * 100f).toInt() % 10 == 0) 1 else 2
    return String.format(Locale.ROOT, "%.${decimals}f", speed)
}
