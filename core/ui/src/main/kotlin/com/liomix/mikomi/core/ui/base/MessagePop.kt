package com.liomix.mikomi.core.ui.base

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.liomix.mikomi.core.designsystem.Motion
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.Stroke

/**
 * 提示弹窗内容。
 *
 * @param message 提示文案（调用方用 `stringResource` 解析好的字符串）。
 * @param iconRes 覆盖左侧图标；不传则用应用自己的 launcher 图标（见 [Toast]）。
 */
data class ToastData(
    val message: String,
    @param:DrawableRes val iconRes: Int? = null,
)

/**
 * 应用内提示弹窗：底部居中的玻璃浮层，左边一枚圆形应用图标，右边一行文案。
 *
 * 传 `null` 表示隐藏；退场动画期间会保留最后一次内容，避免文字瞬间消失。
 * 显示时长由调用方控制（通常用 `LaunchedEffect` 延时把数据置空）。
 *
 * ## 左侧为什么是应用图标
 *
 * 提示代表的是「应用在说话」，不是某个页面在说话。一枚圆形 launcher 图标比任何
 * 通用感叹号都更能说明来源，尤其是这类浮层可能出现在任何页面上。
 * 想换成一枚语义图标时传 [ToastData.iconRes]，圆裁剪对它同样生效。
 *
 * ## 关于「毛玻璃」
 *
 * 这里是**玻璃质感**（半透明底 + 一圈极细描边 + 浮起阴影），不是真的背景模糊。
 * 原因不在偷懒：浮层读不到它下面兄弟节点的绘制结果 —— 真要模糊得让宿主把整页内容
 * 录进一个 layer 再取一段（顶栏那套 `BlurredTopBar` 就是这么做的），对一个停留两秒的
 * 提示来说代价不成比例。半透明加描边在浅底和深底上都已经能读成「一块玻璃」。
 *
 * 用法：
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     Content()
 *     Toast(
 *         data = toast,
 *         modifier = Modifier
 *             .align(Alignment.BottomCenter)
 *             .windowInsetsPadding(Insets.Bottom)
 *             .padding(horizontal = Spacing.Xl, bottom = Spacing.Xl),
 *     )
 * }
 * ```
 */
@Composable
fun Toast(
    data: ToastData?,
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
            // 半透明 + 描边 = 玻璃板；完全不透明就回到一张普通卡片。
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = ToastSurfaceAlpha),
            border = BorderStroke(
                Stroke.Thin,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToastBorderAlpha),
            ),
            // 不要投影也不要色调叠层：投影会让它读成「一张卡片浮在页面上」，
            // 而玻璃感靠的是透出来的底色 + 那道细边。
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = TOAST_MAX_WIDTH)
                    .padding(
                        horizontal = Spacing.Lg,
                        vertical = Spacing.Md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToastAppIcon(iconRes = content.iconRes)
                Spacer(modifier = Modifier.width(Spacing.Md))
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

/**
 * 左侧那枚圆形图标。
 *
 * 默认取应用自己的 launcher 图标，传了 [ToastData.iconRes] 就用传的。
 *
 * ## 为什么先转成位图，而不是 `painterResource`
 *
 * launcher 图标是 **adaptive icon**（`<adaptive-icon>` XML，带背景层与前景层）。
 * `painterResource` 走的是 Compose 自己的矢量/位图解析，对 adaptive icon 支持不全，
 * 在部分设备上会直接抛异常 —— 这正是「点 Toast 就闪退」的原因。
 * 用 `PackageManager` 把它取成 `Drawable` 再栅格化，走的是系统那条成熟路径。
 *
 * 任何一步失败都只是**不显示图标**（`runCatching` + `null`），不能让提示本身崩掉：
 * 一块空白比少一条提示轻得多。
 */
@Composable
private fun ToastAppIcon(
    iconRes: Int?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { ToastIconSize.roundToPx() }
    val bitmap = remember(iconRes, sizePx) {
        runCatching {
            val drawable = if (iconRes != null) {
                context.getDrawable(iconRes)
            } else {
                context.applicationInfo.loadIcon(context.packageManager)
            }
            drawable?.toBitmap(sizePx, sizePx)?.asImageBitmap()
        }.getOrNull()
    } ?: return

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(ToastIconSize)
            .clip(CircleShape),
    )
}

/** 左侧图标直径：比正文高一档，但不至于抢掉文案。 */
private val ToastIconSize = 28.dp

/** 弹窗圆角。 */
private val TOAST_CORNER = Radius.Xxl

/** 弹窗最大宽度，避免长文案铺满整屏。 */
private val TOAST_MAX_WIDTH = 360.dp

/** 容器不透明度：0.72 能明显透出背后的色块，文字仍压得住。 */
private const val ToastSurfaceAlpha = 0.72f

/** 描边不透明度：玻璃的那道亮边，压太实就成了实线边框。 */
private const val ToastBorderAlpha = 0.5f

/** 入场时长。退场比它短：关闭动作要显得更利落。 */
private const val TOAST_ENTER_MILLIS = Motion.Duration.Medium

/** 退场时长。 */
private const val TOAST_EXIT_MILLIS = Motion.Duration.Short
