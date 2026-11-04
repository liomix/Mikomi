package com.liomix.mikomi.feature.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.ScrollingText
import com.liomix.mikomi.core.ui.component.SkeletonBox
import com.liomix.mikomi.core.ui.component.heroTarget
import java.util.Locale

/** 详情页封面圆角（飞行时会与列表卡的 8dp 之间插值）。 */
private val DETAIL_COVER_RADIUS = 14.dp

/**
 * 头部背景的模糊半径。
 *
 * `Modifier.blur` 的半径按密度换算成像素：30dp 已足以把封面的纹理、文字化开成柔和的色底，
 * 再往上加（48dp 起）四角会开始发雾、边缘显脏，观感反而变差。
 * 头部不滚动、不缩放，模糊结果会被缓存成一层 RenderEffect，
 * 半径只影响一次性的光栅化成本，不进入每帧预算（见 [DetailBackdrop] 注释）。
 */
private val DETAIL_BACKDROP_BLUR = 30.dp

/**
 * 背景兜底底色。
 *
 * 头部正文固定是白字，所以底必须永远是深的：日间页面底色是 #F1F1F3，封面失败或尚未解码时
 * 若直接透出页面底色，白字会看不见。这里用一层中性深灰兜底，两种主题下都够暗。
 */
private val DETAIL_BACKDROP_FALLBACK = Color(0xFF26262A)

/**
 * 详情页头部：模糊封面背景 + 标题 + 左封面 / 右信息（上映时间、排行、评分）。
 *
 * 对齐 Dart 版 `AnimeHeader`：背景图高斯模糊铺满，标题单行省略，
 * 左侧封面圆角，右侧竖排信息（label 14sp / value 20sp bold，与 Dart 版一致）。
 *
 * 头部自身不做位移动画：折叠时由外层裁掉下半截，标题始终钉在顶部。
 * [infoAlpha] 是海报 + 信息列的淡出系数，随折叠变化 —— 它以 lambda 传入，
 * 只在 `graphicsLayer` 的绘制阶段读取，因此滚动时不会重组这张头部。
 */
@Composable
internal fun DetailHeader(
    anime: Anime,
    infoAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        DetailBackdrop(
            model = anime.coverUrl,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        ) {
            // 标题：单行，超宽时无限横向滚动（与 Dart 版 ScrollingText 一致）。
            ScrollingText(
                text = anime.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 折叠后期的淡出：读在绘制阶段，滚动时不产生重组。
                    .graphicsLayer { alpha = infoAlpha() },
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // 左侧封面（飞行终点：与列表卡封面同一个 key，点进来时封面飞到这里）。
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(DETAIL_COVER_RADIUS))
                        .heroTarget(
                            key = anime.id,
                            url = anime.coverUrl,
                            description = anime.title,
                            radius = DETAIL_COVER_RADIUS,
                        ),
                )
                // 右侧信息（详情未加载时骨架屏）。
                val detailLoaded = anime.airDate != null || anime.ratingCount > 0
                if (detailLoaded) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        anime.airDate?.let { airDate ->
                            HeaderInfoBlock(
                                label = stringResource(R.string.detail_air_date),
                                value = airDate,
                            )
                        }
                        if (anime.rank > 0) {
                            HeaderInfoBlock(
                                label = stringResource(R.string.detail_rank),
                                value = "#${anime.rank}",
                            )
                        }
                        if (anime.rating != null) {
                            RatingBlock(
                                rating = anime.rating!!,
                                ratingCount = anime.ratingCount,
                            )
                        }
                    }
                } else {
                    HeaderInfoSkeleton(
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 头部背景：封面整图模糊后铺满，不再叠渐变遮罩 —— 压暗会把封面本身的颜色也一起吃掉，
 * 只留模糊后的原色调更好看；需要压暗时由调用方自己加层。
 *
 * 这里刻意不做成 core:ui 的通用组件 —— 它只服务这个头部，参数（半径、兜底色）
 * 都是为「白字压在封面上」这一件事定的，抽出去只会让调用方多背一堆默认值。
 *
 * 性能：`Modifier.blur` 在 Android 12+ 走 `RenderEffect`，内容会被光栅化进独立图层，
 * 半径越大这一层的模糊成本越高；但头部不滚动、不缩放、不透明度不变，图层失效只发生在
 * 图片换掉的那一帧，平时合成即可，不进入每帧预算。因此请求不带 crossfade ——
 * 淡入会在这 ~100ms 里逐帧重跑一次大面积模糊，那是唯一会把成本带进帧里的写法，
 * 而头部本来就随整页转场一起出现，硬切看不出来。
 *
 * 低于 Android 12 时 `Modifier.blur` 不生效（原图直出），此时只有 [DETAIL_BACKDROP_FALLBACK]
 * 兜底，白字能否看清取决于封面本身亮度。
 *
 * 失败态不额外画占位：`error` 由 Coil 交回空 painter，露出的就是兜底底色。
 */
@Composable
private fun DetailBackdrop(
    model: Any?,
    modifier: Modifier = Modifier,
) {
    val painter = rememberBackdropPainter(model)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DETAIL_BACKDROP_FALLBACK),
        )
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(
                    radius = DETAIL_BACKDROP_BLUR,
                    edgeTreatment = BlurredEdgeTreatment.Rectangle,
                ),
        )
    }
}

/** 构建背景请求：显式 memoryCacheKey，与全站封面共用同一份缓存条目。 */
@Composable
private fun rememberBackdropPainter(model: Any?): AsyncImagePainter {
    val context = LocalContext.current
    // ImageRequest 不是 data class，每次重组新建都等于换一个 model，Coil 会重跑请求与解码。
    val request = remember(model, context) {
        ImageRequest.Builder(context)
            .data(model)
            .memoryCacheKey(model as? String)
            .build()
    }
    return rememberAsyncImagePainter(model = request)
}

/** 右侧信息骨架屏：与信息块同构，详情加载前占位。 */
@Composable
private fun HeaderInfoSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SkeletonBox(
                    modifier = Modifier.size(width = 56.dp, height = 14.dp),
                    shape = RoundedCornerShape(7.dp),
                )
                SkeletonBox(
                    modifier = Modifier.size(width = 72.dp, height = 20.dp),
                    shape = RoundedCornerShape(10.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderInfoBlock(
    label: String?,
    value: String,
) {
    Column {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RatingBlock(
    rating: Double,
    ratingCount: Int,
) {
    Column {
        Text(
            text = stringResource(R.string.detail_rating_count, ratingCount),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = String.format(Locale.ROOT, "%.1f", rating),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )
        }
    }
}
