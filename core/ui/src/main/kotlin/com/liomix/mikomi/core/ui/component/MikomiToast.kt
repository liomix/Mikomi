package com.liomix.mikomi.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 提示弹窗内容。
 *
 * @param message 提示文案（调用方用 `stringResource` 解析好的字符串）。
 * @param iconRes 可选图标，建议传**无底色**（透明背景）的图片，这样在深色
 *   弹窗背景上不会出现白色底块。
 */
data class MikomiToastData(
    val message: String,
    @DrawableRes val iconRes: Int? = null,
)

/**
 * 应用内提示弹窗：底部居中的圆角浮层，淡入 + 轻微上滑出现，淡出 + 下滑消失。
 *
 * 传 `null` 表示隐藏；退场动画期间会保留最后一次内容，避免文字瞬间消失。
 * 显示时长由调用方控制（通常用 `LaunchedEffect` 延时把数据置空）。
 *
 * 用法：
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     Content()
 *     MikomiToast(
 *         data = toast,
 *         modifier = Modifier
 *             .align(Alignment.BottomCenter)
 *             .windowInsetsPadding(WindowInsets.navigationBars)
 *             .padding(horizontal = 24.dp, bottom = 28.dp),
 *     )
 * }
 * ```
 */
@Composable
fun MikomiToast(
    data: MikomiToastData?,
    modifier: Modifier = Modifier,
) {
    // 保留最后一次非空内容：exit 动画期间 data 已经变成 null，但仍要显示旧文案。
    var latest by remember { mutableStateOf(data) }
    LaunchedEffect(data) {
        if (data != null) latest = data
    }

    AnimatedVisibility(
        visible = data != null,
        enter = fadeIn(tween(TOAST_ENTER_MILLIS)) +
            slideInVertically(tween(TOAST_ENTER_MILLIS)) { height -> height / 2 },
        exit = fadeOut(tween(TOAST_EXIT_MILLIS)) +
            slideOutVertically(tween(TOAST_EXIT_MILLIS)) { height -> height / 2 },
        modifier = modifier,
    ) {
        val content = latest ?: return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(TOAST_CORNER),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = TOAST_MAX_WIDTH)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (content.iconRes != null) {
                    Image(
                        painter = painterResource(content.iconRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(TOAST_ICON_SIZE),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = content.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 弹窗图标尺寸（透明底图，不留额外背景）。 */
private val TOAST_ICON_SIZE = 28.dp

/** 弹窗圆角。 */
private val TOAST_CORNER = 20.dp

/** 弹窗最大宽度，避免长文案铺满整屏。 */
private val TOAST_MAX_WIDTH = 360.dp

private const val TOAST_ENTER_MILLIS = 200

private const val TOAST_EXIT_MILLIS = 160
