package com.liomix.mikomi.feature.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiPillShape
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.base.MikomiIcon
import com.liomix.mikomi.core.ui.component.base.ScrollingText
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.component.business.heroTarget
import com.liomix.mikomi.core.ui.image.MikomiAsyncImage
import com.liomix.mikomi.core.ui.image.rememberMikomiImagePainter
import com.liomix.mikomi.core.ui.window.LocalMikomiWindowSize
import com.liomix.mikomi.core.ui.window.MikomiInsets
import java.util.Locale

/** 详情页封面圆角（飞行时会与列表卡的 8dp 之间插值）。 */
private val DETAIL_COVER_RADIUS = 14.dp

/**
 * 宽屏下封面的固定宽度。
 *
 * 手机上封面是 `weight(1f)`（与右侧信息列各占一半）。到了可读内容列里再对半分，
 * 封面会被 `aspectRatio` 按「行高」反推成一条竖长条，而信息列宽出一倍、右边空一片。
 * 固定在 220dp（约 330dp 高，2:3 不变）之后，封面尺寸与手机上接近，信息列自然变宽。
 */
private val DETAIL_COVER_WIDE_WIDTH = 220.dp

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
                .windowInsetsPadding(MikomiInsets.Chrome)
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
                    color = MikomiTheme.colors.media.content,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(MikomiSpacing.Room))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Room),
            ) {
                // 左侧封面（飞行终点：与列表卡封面同一个 key，点进来时封面飞到这里）。
                //
                // 宽屏用固定宽度而不是 `weight(1f)`：内容列收窄后两等分会让封面被
                // `aspectRatio` 按行高反推成一条竖条，见 DETAIL_COVER_WIDE_WIDTH。
                val coverWidthModifier = if (LocalMikomiWindowSize.current.isExpandedWidth) {
                    Modifier.width(DETAIL_COVER_WIDE_WIDTH)
                } else {
                    Modifier.weight(1f)
                }
                MikomiAsyncImage(
                    url = anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = coverWidthModifier
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
                        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Lg),
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
 * 低于 Android 12 时 `Modifier.blur` 不生效（原图直出），此时只有 `media.backdrop`
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
                .background(MikomiTheme.colors.media.backdrop),
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
 * 背景 painter。
 *
 * 大背景不淡入：它要的是「一进来就铺满」，淡入反而露出底下的页面。
 * 与全站封面共用同一份缓存条目（memoryCacheKey 固定为 url）。
 */
@Composable
private fun rememberBackdropPainter(model: Any?): Painter =
    rememberMikomiImagePainter(url = model as? String, crossfade = false)

/** 右侧信息骨架屏：与信息块同构，详情加载前占位。 */
@Composable
private fun HeaderInfoSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Lg),
    ) {
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Xs)) {
                SkeletonBox(
                    modifier = Modifier.size(width = 56.dp, height = 14.dp),
                    shape = MikomiPillShape,
                )
                SkeletonBox(
                    modifier = Modifier.size(width = 72.dp, height = 20.dp),
                    shape = RoundedCornerShape(MikomiRadius.Md),
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
                // bodyMedium 本身就是 14sp：原来写的是「bodySmall 再抬到 14」，
                // 等于一边用 12sp 的行高一边用 14sp 的字号。这里直接选对槽位。
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MikomiTheme.colors.media.contentSecondary,
                ),
            )
            Spacer(modifier = Modifier.height(MikomiSpacing.Xs))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MikomiTheme.colors.media.content,
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
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MikomiTheme.colors.media.contentSecondary,
            ),
        )
        Spacer(modifier = Modifier.height(MikomiSpacing.Xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MikomiIcon(
                icon = MikomiIcons.Star,
                contentDescription = null,
                tint = MikomiTheme.colors.rating,
                size = MikomiSizes.IconMd,
            )
            Spacer(modifier = Modifier.size(MikomiSpacing.Xs))
            Text(
                text = String.format(Locale.ROOT, "%.1f", rating),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MikomiTheme.colors.media.content,
                ),
            )
        }
    }
}
