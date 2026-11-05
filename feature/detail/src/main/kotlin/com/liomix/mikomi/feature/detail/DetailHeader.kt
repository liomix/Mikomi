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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.ui.component.base.ScrollingText
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.component.business.heroTarget
import java.util.Locale

/** 详情页封面圆角（飞行时会与列表卡的 8dp 之间插值）。 */
private val DETAIL_COVER_RADIUS = 14.dp

/**
 * 头部背景的模糊半径。
 *
 * `Modifier.blur` 的半径按密度换算成像素：40dp 会把封面的纹理、文字彻底化开成柔和的色底，
 * 只留大块色相；再往上加边缘会开始发雾、发脏，观感反而变差。
 * 头部不滚动、不缩放，模糊结果会被缓存成一层 RenderEffect，
 * 半径只影响一次性的光栅化成本，不进入每帧预算（见 [DetailBackdrop] 注释）。
 */
private val DETAIL_BACKDROP_BLUR = 40.dp

/**
 * 背景兜底底色。
 *
 * 头部正文固定是白字，所以底必须永远是深的：日间页面底色是 #F1F1F3，封面失败或尚未解码时
 * 若直接透出页面底色，白字会看不见。这里用一层中性深灰兜底，两种主题下都够暗。
 */
private val DETAIL_BACKDROP_FALLBACK = Color(0xFF26262A)

/**
 * 橡皮筋接缝的渐隐带高度。
 *
 * 屏幕顶部那条「封面延伸」和真正的头部画面之间，行号会往回跳一次 —— 模糊后大部分封面看不出来，
 * 强对比封面上却是一条横线。让延伸层的最后这几 dp 化到全透明，错位就摊成一条柔和过渡。
 * 再宽就开始吃掉封面本身的上沿细节，3dp 是「看不出线、也看不出带」的量。
 */
internal val DETAIL_SEAM_FADE = 3.dp

/**
 * 头部内部两个节点的坐标（普通对象，不是 State）。
 *
 * 测量回调只用来算一个静态距离，没必要把结果存成组合状态再触发重组 ——
 * 每次布局都改一遍状态，页面转场时就是每帧一次全量重组。
 */
private class HeaderCoords {
    var header: LayoutCoordinates? = null
    var info: LayoutCoordinates? = null
}

/**
 * 详情页头部：模糊封面背景 + 标题 + 左封面 / 右信息（上映时间、排行、评分）。
 *
 * 对齐 Dart 版 `AnimeHeader`：背景图高斯模糊铺满，标题单行省略，
 * 左侧封面圆角，右侧竖排信息（label 14sp / value 20sp bold，与 Dart 版一致）。
 *
 * 头部自身不做位移动画：折叠时由外层裁掉下半截，标题始终钉在顶部。
 *
 * [onInfoBottomMeasured] 把右信息列（评分排在最下面）的底边报给调用方 —— 头部能收到多高
 * 由它决定，见 `HeaderCollapse.updateMinVisible`。它在**布局之后**回调，只会在尺寸/位置
 * 真的变了时触发，滚动过程中不参与。
 */
@Composable
internal fun DetailHeader(
    anime: Anime,
    onInfoBottomMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 两个坐标都记下来再相减：页面转场、橡皮筋位移会改变头部根节点在屏幕上的位置，
    // 只有「相对头部自己」的距离才是不随动画变化的常量。
    val coords = remember { HeaderCoords() }
    val reportInfoBottom = {
        val header = coords.header
        val info = coords.info
        if (header != null && info != null && header.isAttached && info.isAttached) {
            onInfoBottomMeasured(
                header.localPositionOf(info, Offset.Zero).y + info.size.height,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                coords.header = it
                reportInfoBottom()
            },
    ) {
        DetailBackdrop(
            model = anime.coverUrl,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                // 状态栏 inset 之后不再留额外上边距：标题紧贴状态栏下沿，
                // 封面与信息列同在一个 Column 里，会一起上移，间距保持不变。
                .padding(
                    start = MikomiSpacing.Room,
                    end = MikomiSpacing.Room,
                    bottom = MikomiSpacing.Room,
                ),
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
            Spacer(modifier = Modifier.height(MikomiSpacing.Room))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Room),
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
                        // 它的底边 = 评分（或最下面那块信息）的下沿。
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned {
                                coords.info = it
                                reportInfoBottom()
                            },
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
                        // 骨架也参与测量：详情到达后信息列会变高，底边跟着重新上报。
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned {
                                coords.info = it
                                reportInfoBottom()
                            },
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

/**
 * 屏幕最顶部那条「封面延伸」。
 *
 * 整页橡皮筋把内容往下推时，屏幕顶部会露出 `stretchPx` 那么高的一条缝。
 * 这里用与 [DetailHeader] 背景**完全同源**的一层盖住这条缝：同一张图、同样的完整高度、
 * 同样的模糊半径 —— 两者其实是同一张画的不同裁切，拼起来不会露出页面底色
 * （深色主题下那条底色就是纯黑，非常割裂）。
 *
 * 关键在内部图层仍按头部完整高度渲染，只有同尺寸的模糊结果才真的对得上。
 *
 * 但这张画上面没有多余的像素可画：缝里放的是第 `0..stretchPx` 行，接缝下面重新从第 0 行
 * 开始，行号会往回跳一次。40dp 的模糊已经把这种跳变压得很轻，强对比封面上却仍是一条横线。
 * 所以这一层**盖在头部上面**，最后 [DETAIL_SEAM_FADE] 那一段用 `DstIn` 把 alpha 收到 0，
 * 让位给下面真实的头部画面 —— 错位被摊平在几 dp 里，而不是一刀切。
 *
 * @param headerHeight 头部完整高度，必须与 [DetailHeader] 的折叠高度一致
 * @param stretchPx 整页位移：为 0 时这条缝不存在，整层跳过绘制
 */
@Composable
internal fun HeaderEdgeBackdrop(
    model: Any?,
    headerHeight: Dp,
    stretchPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    val fadePx = with(LocalDensity.current) { DETAIL_SEAM_FADE.toPx() }
    Box(
        modifier = modifier
            // 渐隐要靠 DstIn 遮罩，必须先有自己的图层。
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                val dy = stretchPx()
                // 没越界（或是在往上拽）时这条缝不存在，整层不进绘制。
                if (dy <= 0f) return@drawWithContent
                val bottom = dy + fadePx
                clipRect(bottom = bottom) {
                    this@drawWithContent.drawContent()
                    // 前 dy 原样，最后 fadePx 一路化到全透明；两个端点之外由 CLAMP 接管，
                    // 所以这条遮罩只会影响接缝那几 dp。
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            1f to Color.Transparent,
                            startY = dy,
                            endY = bottom,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        DetailBackdrop(
            model = model,
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight),
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
                tint = MikomiTheme.colors.rating,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = String.format(Locale.ROOT, "%.1f", rating),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )
        }
    }
}
