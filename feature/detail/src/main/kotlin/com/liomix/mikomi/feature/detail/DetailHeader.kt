package com.liomix.mikomi.feature.detail

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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.liomix.mikomi.core.model.Anime
import java.util.Locale

/**
 * 详情页头部：模糊封面背景 + 渐变遮罩 + 标题 + 左封面 / 右信息（上映时间、排行、评分）。
 *
 * 对齐 Dart 版 `AnimeHeader`：背景图高斯模糊铺满，上深下浅渐变遮罩，
 * 标题单行省略，左侧封面圆角，右侧竖排信息。
 */
@Composable
internal fun DetailHeader(
    anime: Anime,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // 模糊背景封面。
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(20.dp),
        )
        // 上深下浅渐变遮罩。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.7f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 16.dp),
        ) {
            // 标题单行省略。
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 左侧封面。
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp)),
                )
                // 右侧信息。
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
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
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
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
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )
        }
    }
}
