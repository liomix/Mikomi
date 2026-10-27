package com.liomix.mikomi.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.ScrollingText
import com.liomix.mikomi.core.ui.component.SkeletonBox
import java.util.Locale

/**
 * 详情页头部：模糊封面背景 + 标题 + 左封面 / 右信息（上映时间、排行、评分）。
 *
 * 对齐 Dart 版 `AnimeHeader`：背景图高斯模糊铺满，标题单行省略，
 * 左侧封面圆角，右侧竖排信息（label 14sp / value 20sp bold，与 Dart 版一致）。
 *
 * 头部固定不随内容滚动，因此不再做视差偏移。
 */
@Composable
internal fun DetailHeader(
    anime: Anime,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        // 模糊背景封面。
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(24.dp),
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // 左侧封面。
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp)),
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
