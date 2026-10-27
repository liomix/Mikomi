package com.liomix.mikomi.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.ScrollingText
import com.liomix.mikomi.core.ui.component.SkeletonBox

/** 历史未展开时最多显示的 tag 数。 */
private const val HISTORY_COLLAPSED_COUNT = 8

/** 热门榜骨架屏条数。 */
private const val TRENDING_SKELETON_COUNT = 6

/** 热门榜展示条数。 */
private const val TRENDING_LIMIT = 10

/**
 * 浏览态内容：搜索历史（可折叠）+ 热门推荐。
 *
 * 两部分共用一个 [LazyColumn]，避免嵌套滚动；
 * 历史为空时只显示热门推荐，不留空白区。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchBrowseSection(
    state: SearchUiState,
    onHistoryClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onRetryTrending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var historyExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (state.history.isNotEmpty()) {
            item(key = "history_header") {
                SectionHeader(
                    title = stringResource(R.string.search_history),
                    action = {
                        IconButton(onClick = onClearHistory) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.search_history_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
            item(key = "history_tags") {
                HistoryTags(
                    history = state.history,
                    expanded = historyExpanded,
                    onToggleExpand = { historyExpanded = !historyExpanded },
                    onTagClick = onHistoryClick,
                    onTagLongClick = onRemoveHistory,
                )
            }
        }

        item(key = "trending_header") {
            SectionHeader(title = stringResource(R.string.search_trending))
        }

        when (state.trendingState) {
            LoadState.Idle,
            LoadState.Loading,
            -> items(
                count = TRENDING_SKELETON_COUNT,
                key = { "trending_skeleton_$it" },
                contentType = { "trending_skeleton" },
            ) {
                TrendingSkeletonItem()
            }

            LoadState.Error -> item(key = "trending_error") {
                RetryRow(
                    message = stringResource(R.string.search_load_failed),
                    onRetry = onRetryTrending,
                )
            }

            LoadState.Success -> {
                if (state.trending.isEmpty()) {
                    item(key = "trending_empty") {
                        HintRow(text = stringResource(R.string.search_trending_empty))
                    }
                } else {
                    itemsIndexed(
                        items = state.trending.take(TRENDING_LIMIT),
                        key = { _, anime -> "trending_${anime.id}" },
                        contentType = { _, _ -> "trending_item" },
                    ) { index, anime ->
                        TrendingItem(
                            rank = index + 1,
                            anime = anime,
                            onClick = { onAnimeClick(anime.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 区块标题（可选右侧操作）。 */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** 历史关键词标签：横向流式排列，超过阈值可展开/收起。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryTags(
    history: List<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onTagClick: (String) -> Unit,
    onTagLongClick: (String) -> Unit,
) {
    val collapsible = history.size > HISTORY_COLLAPSED_COUNT
    val visible = if (expanded || !collapsible) history else history.take(HISTORY_COLLAPSED_COUNT)

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.forEach { keyword ->
            HistoryTag(
                keyword = keyword,
                onClick = { onTagClick(keyword) },
                onLongClick = { onTagLongClick(keyword) },
            )
        }
        if (collapsible) {
            ExpandTag(expanded = expanded, onClick = onToggleExpand)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryTag(
    keyword: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 长按删除单条，点击直接搜索。
    Surface(
        modifier = modifier.heightIn(min = 40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ExpandTag(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.search_history_collapse else R.string.search_history_expand,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 热门榜单条：排名徽章 + 标题 + 评分。 */
@Composable
private fun TrendingItem(
    rank: Int,
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(rank = rank)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ScrollingText(
                text = anime.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            anime.rating?.let { rating ->
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = RatingColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(java.util.Locale.ROOT, "%.1f", rating),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 排名徽章：前三名金/银/铜，其余中性数字。 */
@Composable
private fun RankBadge(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    val container = when (rank) {
        1 -> GoldColor
        2 -> SilverColor
        3 -> BronzeColor
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendingSkeletonItem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(
            modifier = Modifier.size(26.dp),
            shape = RoundedCornerShape(7.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(15.dp),
                shape = RoundedCornerShape(7.dp),
            )
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.2f)
                    .height(12.dp),
                shape = RoundedCornerShape(6.dp),
            )
        }
    }
}

/** 居中提示行（空态）。 */
@Composable
private fun HintRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 失败行：文案 + 重试。 */
@Composable
private fun RetryRow(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.search_retry))
        }
    }
}

/** 评星星色。 */
internal val RatingColor = Color(0xFFFFB300)

private val GoldColor = Color(0xFFF2B705)
private val SilverColor = Color(0xFF9AA3AD)
private val BronzeColor = Color(0xFFC07A3E)
