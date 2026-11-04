package com.liomix.mikomi.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
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
 * - 加载中/失败显示占位色块，配合 crossfade 过渡，避免懒列表滚动回看时闪白；
 * - [shimmerWhileLoading] 为 true 时，加载中用**骨架屏同款扫光块**代替纯色块：
 *   网格首屏/分页骨架消失的那一刻，卡片封面已经是同一种材质，等图片解码完直接顶上来，
 *   不会出现「骨架 → 一块死灰 → 封面」的断层。见下方 `settled` 的说明。
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
    shimmerWhileLoading: Boolean = false,
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
        // 占位是否可以让位给图片。**必须在图片真的画得出来之后**才翻转：
        // 如果按「数据到了」就撤占位，解码那几帧会露出底下的纯色块 —— 也就是
        // 「骨架消失后变灰」的来源。挂在 Success 上则撤占位与图片绘制落在同一帧。
        var settled by remember { mutableStateOf(url.isNullOrEmpty()) }

        // 与骨架完全同色的底：图片画不出来时（url 为空、请求被取消、重试用尽）
        // 格子也仍然是一块占位，而不是透出页面底色的空洞。
        val baseColor = skeletonBaseColor()

        Box(
            modifier = modifier
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(COVER_CORNER_RADIUS))
                // 垫底必须在图片之下、且恒定存在：`placeholder` 为 null（有扫光时）的
                // 那几帧里，这里是唯一的不透明来源。
                .background(baseColor)
                .heroSource(
                    key = heroKey,
                    url = url,
                    description = contentDescription,
                    radius = COVER_CORNER_RADIUS,
                ),
        ) {
            // 画在图片**底下**：图片一落地就把它完全盖住，不依赖 settled 提前一帧生效。
            // settled 只用来把这条扫光时间线（按帧驱动的协程）从组合里摘掉，
            // 否则加载完成后每张卡仍会每帧白跑一次 shader 平移。
            if (shimmerWhileLoading && !settled) {
                // 外层 Box 已按 8dp 圆角裁剪，这里用直角块即可，省一次圆角遮罩。
                SkeletonBox(
                    modifier = Modifier.matchParentSize(),
                    shape = RectangleShape,
                )
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                // 有扫光占位时不要再叠一层纯色：两层同尺寸的不透明块只会多一次绘制。
                placeholder = if (shimmerWhileLoading) null else placeholderPainter,
                error = placeholderPainter,
                // 请求重新开始（节点被复用、内存缓存失效）就重新挂回占位：
                // 否则 settled 会停在上一次成功时的 true，加载期间露出一块没有扫光的死块。
                onLoading = { if (shimmerWhileLoading) settled = false },
                onSuccess = { settled = true },
                onError = {
                    when {
                        // url 为空时不会再有成功回调，直接停在占位色块上。
                        url.isNullOrEmpty() -> settled = true
                        retryHash < MAX_RETRY -> retryHash++
                        // 重试用尽：撤掉扫光，留一块静态灰底表示「这张图挂了」。
                        else -> settled = true
                    }
                },
            )
        }
    }
}
