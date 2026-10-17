package com.liomix.mikomi.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.liomix.mikomi.core.model.Anime
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 首页轮播图：自动播放的 [HorizontalPager] + 底部渐变遮罩（标题 / 评分）+ 圆点指示器。
 *
 * 自动播放为组件级副作用（非懒列表 item 内），数据源接入后 banner 数量与内容随之替换。
 */
@Composable
fun HomeBanner(
    banners: List<Anime>,
    onBannerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (banners.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { banners.size })

    LaunchedEffect(banners.size) {
        if (banners.size <= 1) return@LaunchedEffect
        while (true) {
            delay(AUTO_SCROLL_INTERVAL_MS)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % banners.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Box(modifier = modifier) {
        HorizontalPager(state = pagerState) { page ->
            BannerItem(
                anime = banners[page],
                onClick = { onBannerClick(banners[page].id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PagerIndicator(
            pageCount = banners.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun BannerItem(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(BANNER_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = anime.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.7f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val rating = formatRating(anime.rating)
            if (rating != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .width(if (selected) 16.dp else 6.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            Color.White.copy(alpha = 0.9f)
                        } else {
                            Color.White.copy(alpha = 0.4f)
                        },
                    ),
            )
        }
    }
}

private fun formatRating(rating: Double?): String? =
    rating?.let { String.format(Locale.ROOT, "%.1f", it) }

private val BANNER_HEIGHT = 180.dp
private const val AUTO_SCROLL_INTERVAL_MS = 4_000L
