package com.liomix.mikomi.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.liomix.mikomi.core.model.Anime
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
) {
    if (banners.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { banners.size })

    // 自动播放：每次 settledPage 变化（含手势结束）都会重启计时，
    // 用户操作后 4s 内不会再被自动翻页打断。
    LaunchedEffect(banners.size, pagerState.settledPage) {
        if (banners.size <= 1) return@LaunchedEffect
        delay(AUTO_SCROLL_INTERVAL_MS)
        if (!pagerState.isScrollInProgress) {
            val next = (pagerState.settledPage + 1) % banners.size
            pagerState.animateScrollToPage(next)
        }
    }

    Box(modifier = modifier) {
        val coroutineScope = rememberCoroutineScope()
        HorizontalPager(
            state = pagerState,
            pageSpacing = BANNER_PAGE_SPACING,
            // spring 让翻页带轻微"过冲-回落"，比 tween 硬切更丝滑
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        ) { page ->
            BannerItem(
                anime = banners[page],
                onClick = { onBannerClick(banners[page].id) },
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
            currentPage = pagerState.settledPage,
            onIndicatorClick = { index ->
                // 指示器点击同样走 spring 动画，与手势翻页保持一致的视觉
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
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
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "bannerPressCorner",
    )

    Box(
        modifier = modifier
            .height(BANNER_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = anime.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // 底部磨砂渐变遮罩：0.35 起比 0.5 更柔和，配合 20dp 圆角更现代
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(BANNER_CONTENT_PADDING),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = (-0.2).sp, // 标题字距微收，更精致
                ),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = Color(0xFFFFC107),
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = rating,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
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
                    Color.White.copy(alpha = 0.95f)
                } else {
                    Color.White.copy(alpha = 0.45f)
                },
                animationSpec = tween(durationMillis = 200),
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

private val BANNER_HEIGHT = 180.dp
private val BANNER_CORNER = 20.dp
private val BANNER_CORNER_PRESSED = 16.dp
private val BANNER_CONTENT_PADDING = 16.dp
private val BANNER_TITLE_TO_RATING_GAP = 6.dp
private val BANNER_PAGE_SPACING = 12.dp
private val BANNER_INDICATOR_BOTTOM_PADDING = 12.dp
private val BANNER_INDICATOR_SPACING = 6.dp
private val BANNER_INDICATOR_DOT_SIZE = 6.dp
private val BANNER_INDICATOR_SELECTED_WIDTH = 22.dp

private const val AUTO_SCROLL_INTERVAL_MS = 4_000L
