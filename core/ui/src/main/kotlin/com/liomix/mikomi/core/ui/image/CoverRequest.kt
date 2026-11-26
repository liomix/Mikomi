package com.liomix.mikomi.core.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage as CoilAsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.liomix.mikomi.core.ui.base.skeletonBaseColor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 全站网络图的统一请求。
 *
 * 三件事必须一致，否则同一张图会在不同页面各缓存一份、或者表现不一致：
 *
 * - **`memoryCacheKey` 固定为 url**：不带重试计数、不带页面来源。用户在列表看到封面、
 *   点进详情页时直接命中同一份缓存，不会重新解码。
 * - **`crossfade` 由调用方显式决定**：懒列表里必须关（一屏十张同时淡入是明确的每帧开销），
 *   静止页面可以开。
 * - **UA / 超时 / 缓存上限由 `ImageLoader` 统一给**：这里不再留「是否带浏览器 UA」
 *   这种按调用点决定的开关 —— 漏传一次的后果是某些图床永久 403，且没有任何报错。
 *
 * 返回值用 `remember` 缓存：`ImageRequest` 不是 data class，每次重组新建一个新实例，
 * Coil 会当成「换了一个 model」重跑一遍比较与请求。
 */
@Composable
fun rememberCoverRequest(
    url: String?,
    crossfade: Boolean = true,
): ImageRequest {
    val context = LocalContext.current
    return remember(url, context, crossfade) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(crossfade)
            .memoryCacheKey(url)
            .build()
    }
}

/**
 * 网络图的默认占位与失败底色：骨架色。
 *
 * 以前 `placeholder` / `error` 两个参数存在，但全仓没有一处传过 —— 等于「有口无心」。
 * 结果是一张封面在首帧、失败、被取消三种情况下都是**透明**的，露出来的是父容器的
 * 背景色；深色主题下就是一块比周围更黑的洞。
 *
 * 用骨架色而不是固定灰，是因为加载中和加载不出来在视觉上都该是「还没有内容」，
 * 和 [com.liomix.mikomi.core.ui.base.SkeletonBox] 用同一种底色，
 * 列表里滚过去才不会有「先灰一下再黑一下」的跳变。
 */

/** 拿 painter 而不是组件：需要自己画（占位/进度）时用。 */
@Composable
fun rememberImagePainter(
    url: String?,
    crossfade: Boolean = false,
): Painter = rememberAsyncImagePainter(
    model = rememberCoverRequest(
        url = url,
        crossfade = crossfade,
    ),
)

/**
 * 列表封面预取：把这一屏（或这一页）还没滚到的封面先落到磁盘缓存。
 *
 * 为什么不在 [AsyncImage] 里自动做：图片加载是「按可见性触发」的，一个 item 组合
 * 出来才开始下载，滚动时新出现的卡片永远是「先占位、再淡入」。Telegram 的做法是
 * 在列表数据到手的瞬间就把接下来的几十张图排进队列，滚动时命中磁盘缓存，用户看到的是
 * 图直接出现。
 *
 * 两个刻意的选择：
 *
 * - **`memoryCachePolicy = DISABLED`**：这里不知道图片最终显示多大，把解码结果塞进内存
 *   缓存只会占着一份尺寸不对的位图。预取的目标是**网络往返**，落盘就够了，真正显示时
 *   由 `AsyncImage` 按实际尺寸解码，磁盘命中。
 * - **`runCatching` 静默失败**：预取是「顺手帮忙」，失败了不该抛到组合层，
 *   也不该和真正显示时的失败混在一起报错。
 *
 * @param urls 待预取的地址，重复项与空值会被过滤；列表顺序即发起顺序。
 * @param enabled 传 `false` 可关闭（例如用户开了省流、或列表还在骨架态）。
 */

/**
 * 预取：**只落磁盘，不占内存**。
 *
 * 写进内存会让正在显示的图被挤出去，滚动时反而更容易闪。
 * 另外它没有布局约束，`Size` 必须显式传 —— 否则按原图解码，等于把「不占内存」这条
 * 规则从内部破坏掉。
 *
 * 用 [LaunchedEffect] 而不是 `remember`：请求是副作用，重组不该重发。
 */@Composable
fun CoverPrefetch(
    urls: List<String?>,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val keys = remember(urls) {
        urls.filterNotNull().filter { it.isNotBlank() }.distinct()
    }
    LaunchedEffect(keys, enabled) {
        if (!enabled || keys.isEmpty()) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        // 并发发起、不等待：coil 自己的 dispatcher 会限流，这里再串行反而拖慢首屏。
        coroutineScope {
            keys.forEach { url ->
                launch {
                    runCatching {
                        loader.execute(
                            ImageRequest.Builder(context)
                                .data(url)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .build(),
                        )
                    }
                }
            }
        }
    }
}
