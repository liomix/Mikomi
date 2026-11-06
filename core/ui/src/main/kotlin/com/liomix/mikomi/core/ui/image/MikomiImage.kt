package com.liomix.mikomi.core.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * 部分图片 CDN（AniRpb 的 meituan 图床）会拒绝非浏览器 User-Agent。
 * 统一放在这里，避免每个调用点各自拼一份、漏掉一处就出现「个别封面永远加载失败」。
 */
private val BROWSER_HEADERS = NetworkHeaders.Builder().apply {
    set("User-Agent", "Mozilla/5.0")
}.build()

/**
 * 全站网络图的统一请求。
 *
 * 三件事必须一致，否则同一张图会在不同页面各缓存一份、或者表现不一致：
 *
 * - **`memoryCacheKey` 固定为 url**：不带重试计数、不带页面来源。用户在列表看到封面、
 *   点进详情页时直接命中同一份缓存，不会重新解码。
 * - **`crossfade` 由调用方显式决定**：懒列表里必须关（一屏十张同时淡入是明确的每帧开销），
 *   静止页面可以开。
 * - **浏览器 UA 由调用方按图源决定**：只有 AniRpb 这类图床需要。
 *
 * 返回值用 [remember] 缓存：`ImageRequest` 不是 data class，每次重组新建一个新实例，
 * Coil 会当成「换了一个 model」重跑一遍比较与请求。
 */
@Composable
fun rememberCoverRequest(
    url: String?,
    crossfade: Boolean = true,
    browserUserAgent: Boolean = false,
): ImageRequest {
    val context = LocalContext.current
    return remember(url, context, crossfade, browserUserAgent) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(crossfade)
            .memoryCacheKey(url)
            .apply {
                if (browserUserAgent) httpHeaders(BROWSER_HEADERS)
            }
            .build()
    }
}

/**
 * 网络图组件。
 *
 * 存在的意义不是「包一层 AsyncImage」，而是**把 coil 类型挡在 core:ui 之内**：
 * feature 只写 url、尺寸和内容描述，不接触 `ImageRequest` / `AsyncImagePainter`。
 * 换图片库、加统一占位、改缓存策略时，改动范围停在 core:ui，不下发到 10 个 feature。
 *
 * 回调刻意抹掉了 coil 的 `State` 参数：调用方关心的是「成功了没有」，
 * 不是「成功时 painter 里是什么」—— 后者一旦暴露，调用点就会开始依赖库的内部状态。
 */
@Composable
fun MikomiAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    crossfade: Boolean = false,
    browserUserAgent: Boolean = false,
    placeholder: Painter? = null,
    error: Painter? = null,
    onLoading: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
) {
    // 回调要 remember：每次重组 new 一个 lambda 会让 AsyncImage 认为监听换人了，
    // 一屏十张图就是每帧十个新对象 + 十次参数比对。
    val loading: ((AsyncImagePainter.State.Loading) -> Unit)? = remember(onLoading) {
        onLoading?.let { cb -> { _: AsyncImagePainter.State.Loading -> cb() } }
    }
    val success: ((AsyncImagePainter.State.Success) -> Unit)? = remember(onSuccess) {
        onSuccess?.let { cb -> { _: AsyncImagePainter.State.Success -> cb() } }
    }
    val failure: ((AsyncImagePainter.State.Error) -> Unit)? = remember(onError) {
        onError?.let { cb -> { _: AsyncImagePainter.State.Error -> cb() } }
    }

    AsyncImage(
        model = rememberCoverRequest(
            url = url,
            crossfade = crossfade,
            browserUserAgent = browserUserAgent,
        ),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        placeholder = placeholder,
        error = error,
        onLoading = loading,
        onSuccess = success,
        onError = failure,
    )
}

/**
 * 需要自己画（模糊、遮罩、`drawWithContent`）时用它取 painter，而不是拿 [MikomiAsyncImage]。
 *
 * 返回上界是 Compose 的 [Painter] 而不是 coil 的 `AsyncImagePainter`：
 * 调用方只需要「能画的一张图」，不需要知道它是怎么加载的。
 */
@Composable
fun rememberMikomiImagePainter(
    url: String?,
    crossfade: Boolean = false,
    browserUserAgent: Boolean = false,
): Painter = rememberAsyncImagePainter(
    model = rememberCoverRequest(
        url = url,
        crossfade = crossfade,
        browserUserAgent = browserUserAgent,
    ),
)
