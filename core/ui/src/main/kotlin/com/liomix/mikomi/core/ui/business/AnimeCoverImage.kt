package com.liomix.mikomi.core.ui.business

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import com.liomix.mikomi.core.designsystem.Alpha
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.SkeletonBox
import com.liomix.mikomi.core.ui.base.skeletonBaseColor
import com.liomix.mikomi.core.ui.image.AsyncImage
import androidx.compose.ui.unit.Dp

/** 加载失败后的最大自动重试次数；超过后停留占位色块，避免无限循环请求。 */
private const val MAX_RETRY = 3

/** 封面圆角：列表卡用 8dp，详情页封面用 14dp；飞行时两者之间插值。 */
private val COVER_CORNER_RADIUS = Radius.Md

/** 海报的默认比例（竖版 2:3）。横条卡传 `null`，由调用方自己给尺寸。 */
private const val COVER_ASPECT_RATIO = 2f / 3f

/**
 * 番剧封面图（网络图统一走 Coil）。
 *
 * - **加载中默认就是骨架同款扫光块**（[shimmerWhileLoading]）：首屏骨架消失、卡片进场
 *   的那一刻，封面已经是同一种材质，等解码完直接顶上来，不会出现「骨架 → 一块死灰 →
 *   封面」的断层。这条默认开着是有代价评估的 —— 曾经它是 false，于是除首页网格卡之外的
 *   所有卡片（时间表行、搜索结果、巡礼搜索、排行榜）加载中都是一块**静止**的骨架色，
 *   在旁边的扫光背景里读起来就是「这块是空的」。要静态色块（例如导出截图）才显式关。
 * - 失败时停在同一块底色上，配合 crossfade 过渡，避免懒列表滚动回看时闪白；
 * - 失败后有限次自动重试：快速滚动时请求可能被取消或被图片 CDN 限流，
 *   裸 [AsyncImage] 不回调也不重试，格子会永久空白。重试通过 [key] 重建
 *   组合实现（相同 model 的请求不会自动重发）。
 * - [heroKey] 非空时参与「封面飞行」（Hero）：列表封面飞向详情页封面，
 *   两侧必须传同一个 key（番剧 id）。
 * - [crossfade] 懒列表里务必传 false：Coil 的淡入会给每张图叠一层 alpha 混合，
 *   快速滚动时一屏 10 张同时过渡，是明确的每帧绘制开销；静止页面可保留。
 */
@Composable
fun AnimeCoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    heroKey: String? = null,
    crossfade: Boolean = true,
    shimmerWhileLoading: Boolean = true,
    aspectRatio: Float? = COVER_ASPECT_RATIO,
    cropAlignment: Alignment = Alignment.Center,
    cornerRadius: Dp = COVER_CORNER_RADIUS,
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
    var retryHash by remember(url) { mutableIntStateOf(0) }

    // ColorPainter 必须 remember：每次重组 new 实例会让 AsyncImage 误判占位图换了，
    // 网格滚动时等于「每帧 × 可见格子数」的额外对象分配与比对。
    val placeholderPainter = remember(placeholderColor) { ColorPainter(placeholderColor) }
    val sized = if (aspectRatio == null) modifier else modifier.aspectRatio(aspectRatio)


    // 用 url 做 key：单元格数据被换掉时（刷新/分页）重建图片节点，
    // 避免 Coil 仍持有上一部番的 painter（会出现「封面是 A、点进去是 B」）。
    key(url, retryHash) {
        // 占位是否可以让位给图片。**必须在图片真的画得出来之后**才翻转：
        // 如果按「数据到了」就撤占位，解码那几帧会露出底下的纯色块 —— 也就是
        // 「骨架消失后变灰」的来源。挂在 Success 上则撤占位与图片绘制落在同一帧。
        var settled by remember { mutableStateOf(url.isNullOrEmpty()) }

        // 「这张图没有」的终态：数据里没给封面、或重试用尽。它必须和「还在加载」长得不一样 ——
        // 两者都是一块底色时，用户看到的只是「这里空着」，分不清是在等还是已经废了。
        var failed by remember { mutableStateOf(url.isNullOrEmpty()) }

        // 与骨架完全同色的底：图片画不出来时（url 为空、请求被取消、重试用尽）
        // 格子也仍然是一块占位，而不是透出页面底色的空洞。
        val baseColor = skeletonBaseColor()

        Box(
            modifier = sized
                .clip(RoundedCornerShape(cornerRadius))
                // 垫底必须在图片之下、且恒定存在：`placeholder` 为 null（有扫光时）的
                // 那几帧里，这里是唯一的不透明来源。
                .background(baseColor)
                .heroSource(
                    key = heroKey,
                    url = url,
                    description = contentDescription,
                    radius = cornerRadius,
                ),
        ) {
            // 画在图片**底下**：图片一落地就把它完全盖住，不依赖 settled 提前一帧生效。
            // settled 只用来把这条扫光时间线（按帧驱动的协程）从组合里摘掉，
            // 否则加载完成后每张卡仍会每帧白跑一次 shader 平移。
            if (shimmerWhileLoading && !settled && !failed) {
                // 外层 Box 已按 8dp 圆角裁剪，这里用直角块即可，省一次圆角遮罩。
                SkeletonBox(
                    modifier = Modifier.matchParentSize(),
                    shape = RectangleShape,
                )
            }
            // 请求由 core:ui 统一构建：memoryCacheKey / crossfade 在这里定，UA 由 ImageLoader 统一注入。
            AsyncImage(
                url = url,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = cropAlignment,
                crossfade = crossfade,
                // 有扫光占位时不要再叠一层纯色：两层同尺寸的不透明块只会多一次绘制。
                placeholder = if (shimmerWhileLoading) null else placeholderPainter,
                error = placeholderPainter,
                // 请求重新开始（节点被复用、内存缓存失效）就重新挂回占位：
                // 否则 settled 会停在上一次成功时的 true，加载期间露出一块没有扫光的死块。
                onLoading = {
                    if (shimmerWhileLoading) settled = false
                    // 重试会重新走一次加载，标记跟着撤掉。
                    failed = false
                },
                onSuccess = {
                    settled = true
                    failed = false
                },
                onError = {
                    when {
                        // url 为空时不会再有成功回调：直接进终态。
                        url.isNullOrEmpty() -> {
                            settled = true
                            failed = true
                        }

                        retryHash < MAX_RETRY -> retryHash++
                        // 重试用尽：撤掉扫光，画出「无图」标记。
                        else -> {
                            settled = true
                            failed = true
                        }
                    }
                },
            )

            // 「无图」标记画在最上层：一块没有任何标记的底色块读起来就是「这里空着」。
            // 装饰性图标（读屏已经有标题），所以 contentDescription 为 null。
            if (failed) {
                Icon(
                    icon = Icons.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.DisabledContent),
                    size = Sizes.IconMd,
                )
            }
        }
    }
}
