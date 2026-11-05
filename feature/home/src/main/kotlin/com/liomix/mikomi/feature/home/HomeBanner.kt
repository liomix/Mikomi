package com.liomix.mikomi.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiTheme
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.base.ScrollingText
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.component.base.skeletonBaseColor
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页轮播图：现代化视觉 + 丝滑翻页。
 *
 * 视觉层：20.dp 圆角卡片、按压微缩放、底部磨砂渐变遮罩、评分胶囊、可点击指示器；
 * 交互层：[HorizontalPager] 使用 spring 物理 fling 行为，自动播放在用户手势后重置计时，
 * 避免"刚想滑就被切走"的割裂感。
 *
 * 数据源接入后 banner 数量与内容随之替换。
 */
@Composable
fun HomeBanner(
    banners: List<Anime>,
    onBannerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
) {
    if (banners.isEmpty()) return

    // 无限循环 pager：页数取 Int.MAX_VALUE，起始于中段，
    // 实际页码用 floorMod 取模映射回 banners.size，
    // 使手势滑动和自动翻页在最后一页后都自然回到第一页。
    val infinitePageCount = Int.MAX_VALUE
    val startPage = infinitePageCount / 2
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { infinitePageCount },
    )

    // 自动播放：每次 settledPage 变化（含手势结束）都会重启计时，
    // 用户操作后 4s 内不会再被自动翻页打断。
    // [autoPlay] 为 false（轮播滚出屏幕）时直接不启动：翻页会切图 → 新图请求与解码，
    // 在用户滚动列表时属于纯抢占。
    LaunchedEffect(banners.size, pagerState.settledPage, autoPlay) {
        if (banners.size <= 1 || !autoPlay) return@LaunchedEffect
        delay(AUTO_SCROLL_INTERVAL_MS)
        if (!pagerState.isScrollInProgress) {
            pagerState.scrollToPage(pagerState.settledPage + 1)
        }
    }

    Box(modifier = modifier) {
        val coroutineScope = rememberCoroutineScope()
        HorizontalPager(
            state = pagerState,
            pageSpacing = BANNER_PAGE_SPACING,
            // 硬切：snap 阶段瞬时落位，无动画无过冲
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = snap(),
            ),
        ) { page ->
            val realPage = Math.floorMod(page, banners.size)
            BannerItem(
                anime = banners[realPage],
                onClick = { onBannerClick(banners[realPage].id) },
                // 相邻页轻微缩放+透明度渐变，模拟 3D 轮播层次感
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val pageOffset = (
                            (pagerState.settledPage - page) + pagerState
                                .currentPageOffsetFraction
                            ).coerceIn(-1f, 1f)
                        val scale = lerp(
                            start = 0.92f,
                            stop = 1f,
                            fraction = 1f - abs(pageOffset).coerceIn(0f, 1f),
                        )
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(
                            start = 0.7f,
                            stop = 1f,
                            fraction = 1f - abs(pageOffset).coerceIn(0f, 1f),
                        )
                    },
            )
        }

        PagerIndicator(
            pageCount = banners.size,
            currentPage = Math.floorMod(pagerState.settledPage, banners.size),
            onIndicatorClick = { index ->
                // 点击指示器硬切到"当前循环窗口内"对应的真实页
                coroutineScope.launch {
                    val current = Math.floorMod(pagerState.settledPage, banners.size)
                    val offset = index - current
                    pagerState.scrollToPage(pagerState.settledPage + offset)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BANNER_INDICATOR_BOTTOM_PADDING),
        )
    }
}

@Composable
private fun BannerItem(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 按压微缩放 + 弹性回弹：按钮式触感反馈
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bannerPressScale",
    )
    val corner by animateDpAsState(
        targetValue = if (isPressed) BANNER_CORNER_PRESSED else BANNER_CORNER,
        animationSpec = tween(durationMillis = MikomiMotion.Duration.Short, easing = MikomiMotion.Curve.Standard),
        label = "bannerPressCorner",
    )

    // 与全站封面统一走 ImageRequest + 显式 memoryCacheKey：
    // 裸 AsyncImage(String) 与 AnimeCoverImage(ImageRequest) 在 Coil 内
    // 内存缓存 key 计算路径不同，可能串图（搜索完退回来轮播显示搜索海报）。
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val imageRequest = remember(anime.coverUrl, context) {
        ImageRequest.Builder(context)
            .data(anime.coverUrl)
            .memoryCacheKey(anime.coverUrl)
            .build()
    }
    // ColorPainter 必须 remember：翻页时每个可见页都会重组，字面量 new 出来的 painter
    // 会让 AsyncImage 认为占位图换了、重跑一遍比对与绘制。
    val placeholderPainter = remember(placeholderColor) { ColorPainter(placeholderColor) }

    // 图片是否已经画得出来。在此之前占位是「骨架同款扫光」，之后换成图片本身。
    // 与 AnimeCoverImage 同一套逻辑：扫光垫在图片底下，图片落地即整块盖住，
    // 所以不依赖这个状态提前一帧生效；它只负责把那条扫光时间线从组合里摘掉。
    var settled by remember(anime.id) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(BANNER_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            // 与骨架同色的底：图片画不出来时（url 为空 / 请求失败）仍是同一种材质，
            // 而不是透出页面底色的空洞。图片落地后整块被盖住。
            .background(skeletonBaseColor())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // 加载中：180dp 的大块占位。首屏这里原本是骨架（HomeBannerSkeleton），
        // 换成一块死灰会显得整个头图区「塌下去」，所以这里沿用同一种材质。
        // 外层已按圆角裁剪，块本身用直角，省一层遮罩。
        if (!settled) {
            SkeletonBox(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
            )
        }

        key(anime.id) {
            AsyncImage(
                model = imageRequest,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                // 有扫光占位时不能再叠纯色，否则灰块会盖住扫光。
                placeholder = null,
                error = placeholderPainter,
                onSuccess = { settled = true },
                // 失败（含 url 为空）就停在静态占位色上，别让扫光一直跑。
                onError = { settled = true },
            )
        }

        // 底部磨砂渐变遮罩：0.35 起比 0.5 更柔和，配合 20dp 圆角更现代
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.35f to Color.Transparent,
                            1f to MikomiTheme.colors.media.scrim,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(BANNER_CONTENT_PADDING),
        ) {
            ScrollingText(
                text = anime.title,
                // 字距不加：中文方块字加 0.1sp 只会让标题看起来是散的（见 `Type.kt`）。
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MikomiTheme.colors.media.content,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            val rating = formatRating(anime.rating)
            if (rating != null) {
                Spacer(modifier = Modifier.height(BANNER_TITLE_TO_RATING_GAP))
                RatingPill(rating = rating)
            }
        }
    }
}

/**
 * 评分胶囊：半透明磨砂底 + 星标 + 数值，比裸露的星标+文字更精致。
 */
@Composable
private fun RatingPill(
    rating: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MikomiTheme.colors.media.surface)
            .padding(horizontal = MikomiSpacing.Sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MikomiTheme.colors.rating,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = rating,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MikomiTheme.colors.media.content,
        )
    }
}

/**
 * 指示器：选中态由 [animateDpAsState] + [animateColorAsState] 平滑过渡，
 * 未选中圆点可点击跳转。
 */
@Composable
private fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    onIndicatorClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BANNER_INDICATOR_SPACING),
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) {
                    BANNER_INDICATOR_SELECTED_WIDTH
                } else {
                    BANNER_INDICATOR_DOT_SIZE
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "indicatorWidth",
            )
            val color by animateColorAsState(
                targetValue = if (selected) {
                    MikomiTheme.colors.media.content
                } else {
                    MikomiTheme.colors.media.contentTertiary
                },
                animationSpec = tween(durationMillis = MikomiMotion.Duration.Short),
                label = "indicatorColor",
            )

            Box(
                modifier = Modifier
                    .width(width)
                    .height(BANNER_INDICATOR_DOT_SIZE)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onIndicatorClick(index) },
                    ),
            )
        }
    }
}

private fun formatRating(rating: Double?): String? =
    rating?.let { String.format(Locale.ROOT, "%.1f", it) }

// 与 HomeScreen 的轮播骨架屏共用：骨架和真实卡片必须同高同圆角，
// 否则 banners 到货的那一刻整个头图区会「跳」一下。所以不再是 file-private。
internal val BANNER_HEIGHT = 180.dp
internal val BANNER_CORNER = 20.dp
private val BANNER_CORNER_PRESSED = 16.dp
private val BANNER_CONTENT_PADDING = 16.dp
private val BANNER_TITLE_TO_RATING_GAP = 6.dp
private val BANNER_PAGE_SPACING = 12.dp
private val BANNER_INDICATOR_BOTTOM_PADDING = 12.dp
private val BANNER_INDICATOR_SPACING = 6.dp
private val BANNER_INDICATOR_DOT_SIZE = 6.dp
private val BANNER_INDICATOR_SELECTED_WIDTH = 22.dp

private const val AUTO_SCROLL_INTERVAL_MS = 4_000L
