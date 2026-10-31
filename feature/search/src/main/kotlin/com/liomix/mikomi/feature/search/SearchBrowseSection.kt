package com.liomix.mikomi.feature.search

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.ui.component.AnimeCoverImage
import com.liomix.mikomi.core.ui.component.ScrollingText
import com.liomix.mikomi.core.ui.component.SkeletonBox
import com.liomix.mikomi.core.ui.component.Tag
import com.liomix.mikomi.core.ui.component.TagRow
import java.util.Locale

/** 历史未展开时最多显示的 tag 数。 */
private const val HISTORY_COLLAPSED_COUNT = 8

/** 历史 chip 最小高度（Material chip 规格，兼顾触控）与单条最大宽度。 */
private val CHIP_MIN_HEIGHT = 36.dp
private val CHIP_MAX_WIDTH = 180.dp

/** 热播榜展示条数。 */
private const val HOT_LIMIT = 10

/** 热播榜骨架屏条数。 */
private const val HOT_SKELETON_COUNT = 6

/**
 * 浏览态内容：搜索历史（可折叠）+ AniRpb 日漫 / 国漫热播榜。
 *
 * 两部分共用一个 [LazyColumn]，避免嵌套滚动；历史为空时只显示热播榜。
 * 榜单卡片以排名数字 + 封面 + 标题 + 更新文案/集数/评分/播放量呈现。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchBrowseSection(
    state: SearchUiState,
    onHistoryClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onHotTabChange: (SearchHotTab) -> Unit,
    onHotItemClick: (AniRpbHotItem) -> Unit,
    onRetryHot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    // 每次进入浏览态都从顶部开始：否则从结果页返回时，
    // 会把上次的滚动位置（可能停在榜单中段）恢复回来，导致搜索历史标题被顶出屏幕。
    val listState = remember { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (state.history.isNotEmpty()) {
            item(key = "history_header") {
                SectionHeader(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.search_history),
                    // 紧贴搜索框，减少顶部空白。
                    topPadding = 2.dp,
                    action = {
                        IconButton(onClick = onClearHistory) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.search_history_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
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

        item(key = "hot_header") {
            SectionHeader(
                icon = Icons.Outlined.Whatshot,
                title = stringResource(R.string.search_hot_ranking),
            )
        }

        item(key = "hot_tabs") {
            HotTabs(
                selected = state.hotTab,
                onSelect = onHotTabChange,
            )
        }

        // key 随 Tab 变化，切换时让新榜单重新播一次淡入上移。
        item(key = "hot_content_${state.hotTab.name}") {
            when (state.currentHotState) {
                LoadState.Idle,
                LoadState.Loading,
                -> HotSkeletonCard()

                LoadState.Error -> RetryCard(
                    message = stringResource(R.string.search_load_failed),
                    onRetry = onRetryHot,
                )

                LoadState.Success -> {
                    if (state.currentHot.isEmpty()) {
                        HintCard(text = stringResource(R.string.search_hot_empty))
                    } else {
                        HotRankingCard(
                            items = state.currentHot.take(HOT_LIMIT),
                            onItemClick = onHotItemClick,
                        )
                    }
                }
            }
        }
    }
}

/** 区块标题：图标 + 标题 + 可选右侧操作。 */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 20.dp,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = topPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** 日漫 / 国漫热播榜切换。 */
@Composable
private fun HotTabs(
    selected: SearchHotTab,
    onSelect: (SearchHotTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    TagRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SearchHotTab.entries.forEach { tab ->
            Tag(
                text = stringResource(tab.labelRes()),
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun SearchHotTab.labelRes(): Int = when (this) {
    SearchHotTab.JAPANESE -> R.string.search_hot_japanese
    SearchHotTab.CHINESE -> R.string.search_hot_chinese
}

/** 历史关键词标签：横向流式排列，超过阈值可展开/收起（高度变化带缓动）。 */
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
            .padding(horizontal = 16.dp)
            .animateContentSize(),
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

/** 历史关键词 chip：细描边 + 透明底，长词截断，保持一屏内可扫读。 */
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
        modifier = modifier
            .heightIn(min = CHIP_MIN_HEIGHT)
            .widthIn(max = CHIP_MAX_WIDTH),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 展开 / 收起 chip：与历史 chip 同款描边，仅文字与箭头用主色区分。 */
@Composable
private fun ExpandTag(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = CHIP_MIN_HEIGHT),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.search_history_collapse else R.string.search_history_expand,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 热播榜卡片：一次淡入上移，避免滚动回看时反复播动画。 */
@Composable
private fun HotRankingCard(
    items: List<AniRpbHotItem>,
    onItemClick: (AniRpbHotItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "hotAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (revealed) 0f else 16f,
        animationSpec = tween(durationMillis = 260),
        label = "hotOffset",
    )

    BillboardSurface(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        },
    ) {
        items.forEach { item ->
            HotRow(
                item = item,
                onClick = { onItemClick(item) },
            )
        }
    }
}

/** 榜单卡片外壳：圆角 + 低层容器色，内部行自带点击态。 */
@Composable
private fun BillboardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            content()
        }
    }
}

/** 热播榜行：名次 + 封面 + 标题 + 更新文案 + 集数/评分/播放量。 */
@Composable
private fun HotRow(
    item: AniRpbHotItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.rank.toString().padStart(2, '0'),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = rankColor(item.rank),
            modifier = Modifier.width(28.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        AnimeCoverImage(
            url = item.coverUrl,
            contentDescription = item.title,
            modifier = Modifier.width(40.dp),
            browserUserAgent = true,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ScrollingText(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth(),
            )
            if (item.updateText.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.updateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.currentEpisodes > 0 || item.score != null || item.playCount > 0) {
                Spacer(modifier = Modifier.height(3.dp))
                HotMeta(item = item)
            }
        }
    }
}

/** 集数 · 评分 · 播放量。 */
@Composable
private fun HotMeta(
    item: AniRpbHotItem,
    modifier: Modifier = Modifier,
) {
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var hasPrevious = false
        if (item.currentEpisodes > 0) {
            Text(
                text = stringResource(R.string.search_hot_episodes, item.currentEpisodes),
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
                maxLines = 1,
            )
            hasPrevious = true
        }
        if (item.score != null) {
            if (hasPrevious) MetaSeparator(color = metaColor)
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = RatingColor,
                modifier = Modifier.size(11.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = String.format(Locale.ROOT, "%.1f", item.score),
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
                maxLines = 1,
            )
            hasPrevious = true
        }
        if (item.playCount > 0) {
            if (hasPrevious) MetaSeparator(color = metaColor)
            Text(
                text = stringResource(R.string.search_hot_play_count, formatPlayCount(item.playCount)),
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetaSeparator(color: Color) {
    Text(
        text = " · ",
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

/** 播放量按中文习惯缩短，避免右侧挤掉集数与评分。 */
private fun formatPlayCount(count: Long): String = when {
    count >= 100_000_000 -> String.format(Locale.ROOT, "%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format(Locale.ROOT, "%.1f万", count / 10_000.0)
    else -> count.toString()
}

/** 榜单骨架屏：与真实行同构（数字块 + 封面块 + 两行文本）。 */
@Composable
private fun HotSkeletonCard(modifier: Modifier = Modifier) {
    BillboardSurface(modifier = modifier) {
        repeat(HOT_SKELETON_COUNT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(
                    modifier = Modifier.size(width = 22.dp, height = 16.dp),
                    shape = RoundedCornerShape(4.dp),
                )
                Spacer(modifier = Modifier.width(14.dp))
                SkeletonBox(
                    modifier = Modifier.size(width = 36.dp, height = 54.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp),
                        shape = RoundedCornerShape(7.dp),
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.25f)
                            .height(11.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                }
            }
        }
    }
}

/** 卡片内提示（空态）。 */
@Composable
private fun HintCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    BillboardSurface(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 卡片内失败提示 + 重试。 */
@Composable
private fun RetryCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BillboardSurface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
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
}

/** 排名文字色：前三名金/银/铜，其余低对比中性色。 */
@Composable
private fun rankColor(rank: Int): Color = when (rank) {
    1 -> GoldColor
    2 -> SilverColor
    3 -> BronzeColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
}

/** 评星星色。 */
internal val RatingColor = Color(0xFFFFB300)

private val GoldColor = Color(0xFFF2B705)
private val SilverColor = Color(0xFF9AA3AD)
private val BronzeColor = Color(0xFFC07A3E)

