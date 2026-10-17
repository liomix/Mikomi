package com.liomix.mikomi.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

/**
 * 单行文本，超宽时自动横向无限滚动（marquee），未超宽时静态显示。
 *
 * 实现要点：
 * - 外层 [Box] 固定容器宽度并 [clipToBounds] 裁剪，内层 [Text] 用
 *   [wrapContentWidth] `unbounded = true` 完整测量，再经 [graphicsLayer] 平移；
 * - 动画仅在文本溢出时启动（[LaunchedEffect] + [Animatable]），未溢出零动画开销，
 *   避免懒列表大量 item 每帧空转导致滑动卡顿。
 */
@Composable
fun ScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val textMeasurer = rememberTextMeasurer()
    val textWidth = remember(text, style) {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
            softWrap = false,
        ).size.width
    }

    var containerWidth by remember { mutableIntStateOf(0) }
    val needsScroll = containerWidth > 0 && textWidth > containerWidth
    val maxOffset = (textWidth - containerWidth).coerceAtLeast(0).toFloat()

    val offset = remember { Animatable(0f) }

    LaunchedEffect(needsScroll, maxOffset) {
        if (!needsScroll) {
            offset.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            delay(2_000)
            offset.animateTo(-maxOffset, tween(durationMillis = 3_000, easing = LinearEasing))
            delay(500)
            offset.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width },
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .wrapContentWidth(unbounded = true)
                .graphicsLayer { translationX = offset.value },
        )
    }
}
