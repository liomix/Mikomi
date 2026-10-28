package com.liomix.mikomi.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

/** 清晰区向右淡出的范围：从这里开始渐变，到 [SharpFadeEnd] 完全交给模糊层。 */
private const val SharpFadeStart = 0.16f
private const val SharpFadeEnd = 0.52f

/**
 * 高斯模糊背景图：整图模糊后铺满容器（详情页头部背景等场景）。
 *
 * [Modifier.blur] 在 Android 12+ 走 RenderEffect，是真正的高斯模糊；更低版本
 * 会退化为不模糊的原图，因此调用方仍需在图上叠加压暗 / 渐变，保证文字对比度。
 *
 * 图片尚未加载成功时先画 [placeholder]，避免容器底色在无图时露出空洞。
 *
 * @param model Coil 支持的图片模型（URL、File、Uri 等）。
 * @param blurRadius 高斯模糊半径，越大越糊。
 * @param placeholder 图片未就绪时的占位内容，铺满整个容器；默认不画。
 */
@Composable
fun BlurredBackdrop(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    blurRadius: Dp = 24.dp,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholder: @Composable () -> Unit = {},
) {
    val painter = rememberBackdropPainter(model)
    val state by painter.state.collectAsState()

    Box(modifier = modifier) {
        // 图片未就绪：先铺占位内容，避免露出容器底色。
        if (state !is AsyncImagePainter.State.Success) {
            Box(modifier = Modifier.fillMaxSize()) {
                placeholder()
            }
        }
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            alignment = alignment,
            modifier = Modifier
                .fillMaxSize()
                .blur(
                    radius = blurRadius,
                    edgeTreatment = BlurredEdgeTreatment.Rectangle,
                ),
        )
    }
}

/**
 * 左清晰、右模糊的背景：底部整图模糊，上层叠一张用水平 alpha 遮罩淡出的清晰图。
 *
 * 用于「横条卡片」这类把竖图裁成宽幅的场景：右侧的模糊层保证卡片末端始终有
 * 封面颜色延展过来（不会出现没东西可糊的黑块），左侧的清晰层保留可辨认的画面。
 * 两层共用同一个 Painter，图片只解码一次。
 *
 * @param blurRadius 模糊层的高斯半径。
 * @param alignment 裁切锚点。竖图裁成宽条时，把锚点抬高（
 *   [androidx.compose.ui.BiasAlignment] 负 y 值）能保住人物头部，
 *   而不是切到腰身或空白处。
 * @param placeholder 图片未就绪时的占位内容，铺满整个容器；默认不画。
 */
@Composable
fun ProgressiveBlurBackdrop(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    blurRadius: Dp = 16.dp,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholder: @Composable () -> Unit = {},
) {
    val painter = rememberBackdropPainter(model)
    val state by painter.state.collectAsState()

    Box(modifier = modifier) {
        if (state !is AsyncImagePainter.State.Success) {
            Box(modifier = Modifier.fillMaxSize()) {
                placeholder()
            }
        }

        // 底层：整图模糊铺满，保证右端始终有颜色。
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = contentScale,
            alignment = alignment,
            modifier = Modifier
                .fillMaxSize()
                .blur(
                    radius = blurRadius,
                    edgeTreatment = BlurredEdgeTreatment.Rectangle,
                ),
        )

        // 上层：清晰原图，只在左侧可见，向右淡出与模糊层衔接。
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            alignment = alignment,
            modifier = Modifier
                .fillMaxSize()
                .horizontalAlphaMask(),
        )
    }
}

/**
 * 水平 alpha 遮罩：左侧保留内容，右侧淡出为透明（露出下层的模糊图）。
 *
 * 必须用 [CompositingStrategy.Offscreen] 隔离到独立图层，否则 [BlendMode.DstIn]
 * 会把同一图层里已画好的其它内容一起擦掉。
 */
private fun Modifier.horizontalAlphaMask(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black,
                SharpFadeStart to Color.Black,
                SharpFadeEnd to Color.Transparent,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** 构建带 crossfade 的请求，多层叠图时复用同一个 Painter，避免重复解码。 */
@Composable
private fun rememberBackdropPainter(model: Any?): AsyncImagePainter {
    val context = LocalContext.current
    val request = remember(model, context) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .memoryCacheKey(model as? String)
            .build()
    }
    return rememberAsyncImagePainter(model = request)
}
