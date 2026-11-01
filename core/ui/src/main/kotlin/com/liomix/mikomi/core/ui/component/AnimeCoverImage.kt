package com.liomix.mikomi.core.ui.component

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade

/** 加载失败后的最大自动重试次数；超过后停留占位色块，避免无限循环请求。 */
private const val MAX_RETRY = 3

/** 封面圆角：列表卡用 8dp，详情页封面用 14dp；飞行时两者之间插值。 */
private val COVER_CORNER_RADIUS = 8.dp

/** 部分图片 CDN（AniRpb 的 meituan 图床）会拒绝非浏览器 User-Agent。 */
private val BROWSER_HEADERS = NetworkHeaders.Builder().apply {
    set("User-Agent", "Mozilla/5.0")
}.build()

/**
 * 番剧封面图（网络图统一走 Coil）。
 *
 * - 加载中/失败显示主题占位色块，配合 crossfade 过渡，避免懒列表滚动回看时闪白；
 * - 失败后有限次自动重试：快速滚动时请求可能被取消或被图片 CDN 限流，
 *   裸 [AsyncImage] 不回调也不重试，格子会永久空白。重试通过 [key] 重建
 *   组合实现（相同 model 的请求不会自动重发）。
 * - [heroKey] 非空时参与「封面飞行」（Hero）：列表封面飞向详情页封面，
 *   两侧必须传同一个 key（番剧 id）。
 * - [browserUserAgent] 为 AniRpb 等对 User-Agent 有要求的图片源附加浏览器 UA；
 *   测试台的 ImageLoader 使用同款 `Mozilla/5.0`，可避免部分封面被 CDN 拒绝。
 * - [crossfade] 懒列表里务必传 false：Coil 的淡入会给每张图叠一层 alpha 混合，
 *   快速滚动时一屏 10 张同时过渡，是明确的每帧绘制开销；静止页面可保留。
 */
@Composable
fun AnimeCoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    heroKey: String? = null,
    browserUserAgent: Boolean = false,
    crossfade: Boolean = true,
) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
    var retryHash by remember(url) { mutableIntStateOf(0) }

    // ColorPainter 必须 remember：每次重组 new 实例会让 AsyncImage 误判占位图换了，
    // 网格滚动时等于「每帧 × 可见格子数」的额外对象分配与比对。
    val placeholderPainter = remember(placeholderColor) { ColorPainter(placeholderColor) }

    // memoryCacheKey 固定为 url：重试不影响缓存命中，成功过的图不再走网络。
    val request = remember(url, context, browserUserAgent, crossfade) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(crossfade)
            .memoryCacheKey(url)
            .apply {
                if (browserUserAgent) httpHeaders(BROWSER_HEADERS)
            }
            .build()
    }

    // 用 url 做 key：单元格数据被换掉时（刷新/分页）重建图片节点，
    // 避免 Coil 仍持有上一部番的 painter（会出现「封面是 A、点进去是 B」）。
    key(url, retryHash) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(COVER_CORNER_RADIUS))
                .heroSource(
                    key = heroKey,
                    url = url,
                    description = contentDescription,
                    radius = COVER_CORNER_RADIUS,
                ),
            contentScale = ContentScale.Crop,
            placeholder = placeholderPainter,
            error = placeholderPainter,
            onError = {
                if (retryHash < MAX_RETRY) retryHash++
            },
        )
    }
}

