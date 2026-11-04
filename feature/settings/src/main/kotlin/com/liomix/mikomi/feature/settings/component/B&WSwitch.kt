package com.liomix.mikomi.feature.settings.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.liomix.mikomi.core.designsystem.theme.LocalNightModeToggleController
import com.liomix.mikomi.feature.settings.R

/**
 * 日 / 夜切换行（复刻 Telegram 聊天设置页的「Night Mode」一行）。
 *
 * 布局取自 Telegram `TextCell`：行高 50dp、图标 28dp 且左边距 21dp、文字从 58dp 起、整行蓝色。
 * 图标直接播放 Telegram 的 `sun_outline.json`（36 帧 @60fps，600ms）：
 * 月牙 + 两颗星 → 形变成圆盘 → 8 条射线淡入并转正，进度线性推进即可，
 * 帧间缓动由 Lottie 关键帧自带的贝塞尔控制点负责，与 Telegram 逐帧一致。
 *
 * 揭示动画不在本组件内：点击后把图标中心位置上报给
 * [LocalNightModeToggleController] 的宿主（App 根部的 `NightModeTransitionHost`），
 * 由它抓窗口截图并调用系统的圆形揭示 —— 动画跑在 RenderThread 上，不占主线程。
 */
@Composable
fun BAndWSwitch(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val controller = LocalNightModeToggleController.current
    // 宿主的夜间状态是「点击即刻生效」的；上游（DataStore）会慢一两帧，仅在无宿主时退回用它。
    val darkMode = controller?.isDarkMode?.value ?: isDarkMode

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.sun_outline))

    // 图标表示**目标**模式，与右侧文案同一口径：
    // 夜间 →「切换到日间模式」→ 太阳；日间 →「切换到夜间模式」→ 月亮。
    // Lottie 首帧是月牙（夜），末帧是太阳（日），所以夜间用末帧、日间用首帧。
    // 判断依据：文案是「点下去会变成哪一档」，图标若画当前档就和文案对着说，用户看到的就是「反了」。
    // 时长直接取合成时长（600ms），保证与 Telegram 的播放速度一致。
    val durationMillis = remember(composition) {
        composition?.duration?.toInt()?.takeIf { it > 0 } ?: DEFAULT_ICON_MORPH_MILLIS
    }
    val frame by animateFloatAsState(
        targetValue = if (darkMode) PROGRESS_SUN else PROGRESS_MOON,
        animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
        label = "nightModeLottieProgress",
    )

    // Telegram 在揭示结束后用 350ms 把图标色从旧主题色混到新主题色。
    val tint by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = ICON_TINT_MILLIS),
        label = "nightModeIconTint",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ToggleRowHeight)
            .clickable(enabled = controller != null) { controller?.toggle?.invoke() }
            .padding(start = ToggleRowStartPadding, end = ToggleRowEndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { frame },
            modifier = Modifier
                .size(IconSize)
                // json 里的描边是纯白，必须自己上色：离屏后盖一层 tint（SrcIn 只保留 alpha）。
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(color = tint, blendMode = BlendMode.SrcIn)
                }
                .onGloballyPositioned { coordinates ->
                    // 揭示圆的原点：图标中心（Compose 根坐标），宿主再换算成窗口坐标。
                    controller?.updateOrigin(coordinates.boundsInRoot().center)
                },
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.width(IconToTextGap))
        Text(
            text = stringResource(
                if (darkMode) R.string.theme_day_mode else R.string.theme_night_mode,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}

/** 切换行的图标直径（Telegram 的 `dp(28)`）。 */
private val IconSize = 28.dp

/** 图标左边距（Telegram `imageLeft = 21`）。 */
private val ToggleRowStartPadding = 21.dp

/** 行尾留白。 */
private val ToggleRowEndPadding = 20.dp

/** 行高（Telegram `TextCell.heightDp = 50`）。 */
private val ToggleRowHeight = 50.dp

/** 图标与文字的间距：21 + 28 + 9 = 58，对齐 Telegram 的 `offsetFromImage`。 */
private val IconToTextGap = 9.dp

/**
 * 合成加载完成前的兜底形变时长。
 *
 * `sun_outline.json` 是 36 帧 @60fps = 600ms，这里只是 composition 还没解析好时的占位值。
 */
private const val DEFAULT_ICON_MORPH_MILLIS = 600

/** 图标颜色的过渡时长（Telegram 揭示结束后 350ms 混色）。 */
private const val ICON_TINT_MILLIS = 350

/**
 * 月牙形状在合成里的位置：首帧。
 *
 * 注意这里的「月牙」指**形状**，不指「现在是夜间」——日间模式下点下去会切到夜间，图标就是月牙。
 * 名字比数字重要：这个文件历史上的「反了」全都源于分不清 0 和 1 对应哪种形状。
 */
private const val PROGRESS_MOON = 0f

/** 太阳形状在合成里的位置：末帧（盘面 + 8 条射线转正）。 */
private const val PROGRESS_SUN = 1f
