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
 * 全站网络图组件。
 *
 * 与 `coil3.compose.AsyncImage` 的区别只有三处，都是「不统一就一定出问题」的地方：
 * 占位色用设计系统的骨架色（而不是 Coil 的灰色默认值）、`contentDescription` 必填、
 * 请求统一由 [rememberCoverRequest] 产出（缓存 key 与 UA 一致）。
 */
@Composable
fun AsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    crossfade: Boolean = false,
    placeholder: Painter? = rememberPlaceholder(),
    error: Painter? = rememberPlaceholder(),
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

    CoilAsyncImage(
        model = rememberCoverRequest(
            url = url,
            crossfade = crossfade,
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
 * 需要自己画（模糊、遮罩、`drawWithContent`）时用它取 painter，而不是拿 [AsyncImage]。
 *
 * 返回上界是 Compose 的 [Painter] 而不是 coil 的 `AsyncImagePainter`：
 * 调用方只需要「能画的一张图」，不需要知道它是怎么加载的。
 */

/** 占位色：与骨架屏同色，图片没到时不会闪一块白。 */
@Composable
fun rememberPlaceholder(): Painter {
    val color = skeletonBaseColor()
    return remember(color) { ColorPainter(color) }
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
