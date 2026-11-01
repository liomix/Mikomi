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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

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
