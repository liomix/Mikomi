package com.liomix.mikomi.feature.settings.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.feature.settings.R
import kotlin.math.hypot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 日 / 夜切换控制器：由 [DarkModeRevealHost] 通过 CompositionLocal 提供，
 * [BAndWSwitch] 直接取用，调用方无需把回调层层透传。
 */
@Stable
class DarkModeToggleController internal constructor(
    /** 触发一次带圆形揭示动画的模式切换。 */
    internal val toggle: () -> Unit,
    /** 上报触发点（图标中心）在根窗口中的位置，作为揭示原点。 */
    internal val updateOrigin: (Offset) -> Unit,
)

internal val LocalDarkModeToggleController =
    staticCompositionLocalOf<DarkModeToggleController?> { null }

/**
 * 日 / 夜切换宿主：包住页面内容，负责圆形揭示动画的全部逻辑。
 *
 * - 日 → 夜：深色界面从图标处向外扩散填满；
 * - 夜 → 日：浅色界面从图标处揭开，旧深色被推散。
 *
 * 遮罩为硬边镂空（整屏旧底色挖掉一个正圆），圆内始终是真实界面，
 * 不会出现整屏盖黑、卡片文字瞬间消失的观感。
 */
@Composable
fun DarkModeRevealHost(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentIsDark by rememberUpdatedState(isDarkMode)
    val background = MaterialTheme.colorScheme.background

    val reveal = remember { Animatable(0f) }
    var overlayVisible by remember { mutableStateOf(false) }
    // 遮罩色 = 切换前主题背景色：把旧界面擦成同色底，而不是盖一层黑。
    var overlayColor by remember { mutableStateOf(background) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var hostTopLeft by remember { mutableStateOf(Offset.Zero) }
    val iconCenter = remember { mutableStateOf(Offset.Zero) }

    val toggle: () -> Unit = {
        // 动画进行中忽略重复点击。
        if (!overlayVisible && !reveal.isRunning) {
            val targetDark = !currentIsDark
            overlayColor = background
            scope.launch {
                // 先用旧底色铺满（与当前界面同色，无感），主题切换被遮罩挡住，
                // 再由圆心向外揭开新界面。
                reveal.snapTo(0f)
                overlayVisible = true
                onToggleDarkMode(targetDark)
                awaitThemeChange(target = targetDark, current = { currentIsDark })
                reveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = REVEAL_DURATION_MILLIS,
                        easing = RevealEasing,
                    ),
                )
                overlayVisible = false
            }
        }
    }

    val latestToggle by rememberUpdatedState(toggle)
    val controller = remember {
        DarkModeToggleController(
            toggle = { latestToggle() },
            updateOrigin = { iconCenter.value = it },
        )
    }

    CompositionLocalProvider(LocalDarkModeToggleController provides controller) {
        Box(
            modifier = modifier
                .onGloballyPositioned { hostTopLeft = it.boundsInRoot().topLeft }
                .onSizeChanged { containerSize = it },
        ) {
            content()

            if (overlayVisible) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithContent {
                            val origin = iconCenter.value - hostTopLeft
                            val maxRadius = maxRevealRadius(containerSize, origin)
                            if (maxRadius <= 0f) return@drawWithContent

                            val radius = maxRadius * reveal.value
                            if (radius <= 0.5f) {
                                // 半径归零：整屏仍是旧界面底色（与切换前完全一致）。
                                drawRect(color = overlayColor)
                                return@drawWithContent
                            }
                            // 硬边镂空：整屏旧底色挖掉一个正圆（EvenOdd），不做羽化。
                            val holePath = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(Offset.Zero, size))
                                addOval(Rect(center = origin, radius = radius))
                            }
                            drawPath(path = holePath, color = overlayColor)
                        },
                )
            }
        }
    }
}

/**
 * 黑 / 白（日 / 夜）模式切换行。
 *
 * 放在 [DarkModeRevealHost] 内时整行点击即触发带揭示动画的切换；
 * 图标随状态旋转 180° 并交叉淡入切换月亮 / 太阳，同时把中心点上报给宿主作为揭示原点。
 *
 * @param onClick 未包在 [DarkModeRevealHost] 内时的兜底点击（可选）。
 */
@Composable
fun BAndWSwitch(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val controller = LocalDarkModeToggleController.current
    val iconRotation by animateFloatAsState(
        targetValue = if (isDarkMode) 180f else 0f,
        animationSpec = tween(durationMillis = ICON_ROTATION_MILLIS),
        label = "nightIconRotation",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                controller?.toggle?.invoke() ?: onClick?.invoke()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { rotationZ = iconRotation }
                .onGloballyPositioned { coordinates ->
                    controller?.updateOrigin(coordinates.boundsInRoot().center)
                },
        ) {
            AnimatedContent(
                targetState = isDarkMode,
                transitionSpec = {
                    fadeIn(tween(ICON_FADE_MILLIS)) togetherWith fadeOut(tween(ICON_FADE_MILLIS))
                },
                label = "nightIcon",
            ) { dark ->
                Icon(
                    imageVector = if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(
                if (isDarkMode) R.string.theme_day_mode else R.string.theme_night_mode,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 等主题真正切换（DataStore 写入是异步的），避免揭示到一半露出旧主题。 */
private suspend fun awaitThemeChange(
    target: Boolean,
    current: () -> Boolean,
) {
    withTimeoutOrNull(THEME_CHANGE_TIMEOUT_MILLIS) {
        snapshotFlow(current).first { it == target }
    }
}

/** 从原点扩散到最远角所需的半径。 */
private fun maxRevealRadius(size: IntSize, origin: Offset): Float {
    if (size.width == 0 || size.height == 0) return 0f
    val dx = maxOf(origin.x, size.width - origin.x)
    val dy = maxOf(origin.y, size.height - origin.y)
    return hypot(dx, dy)
}

/** 图标旋转时长。 */
private const val ICON_ROTATION_MILLIS = 420

/** 图标交叉淡入时长。 */
private const val ICON_FADE_MILLIS = 220

/** 揭示时长。 */
private const val REVEAL_DURATION_MILLIS = 1000

/** 揭示缓动：M3 强调减速曲线，前段舒展、尾段极缓，观感更顺滑。 */
private val RevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** 等待主题切换的超时（超出后继续，避免停在遮罩上）。 */
private const val THEME_CHANGE_TIMEOUT_MILLIS = 400L
