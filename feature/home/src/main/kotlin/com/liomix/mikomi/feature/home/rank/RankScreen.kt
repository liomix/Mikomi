package com.liomix.mikomi.feature.home.rank

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.ui.component.AnimeCoverImage
import com.liomix.mikomi.core.ui.component.EmptyState
import com.liomix.mikomi.core.ui.component.ErrorState
import com.liomix.mikomi.core.ui.component.ScrollingText
import com.liomix.mikomi.core.ui.component.SkeletonBox
import com.liomix.mikomi.feature.home.R
import java.util.Locale
import kotlinx.coroutines.launch

/** 排行榜分类：国漫 / 日漫，两榜均按综合评分降序。 */
enum class RankType(@StringRes val labelRes: Int) {
    CHINESE(R.string.rank_tab_chinese),
    JAPANESE(R.string.rank_tab_japanese),
}

/**
 * 排行榜：首页快捷入口的二级页面（国漫 / 日漫综合评分榜）。
 *
 * - 顶部 [CenterAlignedTopAppBar]，返回交给上层导航；
 * - 分类用 [TabRow] 选中，[HorizontalPager] 左右滑动切换，二者联动；
 * - 榜单列表突出名次层级：前三名高亮徽章，其余为常规序号；
 * - 加载 / 数据 / 空态 / 错误由 [RankUiState] 驱动，错误态可通过
 *   [onRetryClick] 重新拉取。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankScreen(
    uiState: RankUiState,
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_action_rank),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.rank_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            RankUiState.Loading -> RankSkeleton(
                modifier = Modifier.padding(innerPadding),
            )

            RankUiState.Empty -> EmptyState(
                message = stringResource(R.string.rank_empty),
                modifier = Modifier.padding(innerPadding),
            )

            RankUiState.Error -> ErrorState(
                message = stringResource(R.string.rank_error),
                onRetry = onRetryClick,
                modifier = Modifier.padding(innerPadding),
            )

            is RankUiState.Success -> RankContent(
                chinese = uiState.chinese,
                japanese = uiState.japanese,
                onAnimeClick = onAnimeClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun RankContent(
    chinese: List<RankEntry>,
    japanese: List<RankEntry>,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val types = remember { RankType.entries }
    val pagerState = rememberPagerState(pageCount = { types.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {},
        ) {
            types.forEachIndexed { index, type ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = {
                        Text(
                            text = stringResource(type.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (pagerState.currentPage == index) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val entries = when (types[page]) {
                RankType.CHINESE -> chinese
                RankType.JAPANESE -> japanese
            }
            RankList(
                entries = entries,
                onAnimeClick = onAnimeClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 榜单列表：名次徽章 + 封面 + 标题/标签 + 右侧综合评分。 */
@Composable
private fun RankList(
    entries: List<RankEntry>,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        EmptyState(
            message = stringResource(R.string.rank_empty),
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            // 底部留出悬浮导航栏空间，避免最后一项被遮挡。
            bottom = WindowInsets.navigationBars.asPaddingValues()
                .calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            items = entries,
            key = { _, item -> item.anime.id },
            contentType = { _, _ -> "rank_entry" },
        ) { index, entry ->
            RankAnimeRow(
                entry = entry,
                rank = index + 1,
                onClick = { onAnimeClick(entry.anime.id) },
            )
        }
    }
}

@Composable
private fun RankAnimeRow(
    entry: RankEntry,
    rank: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anime = entry.anime
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RankBadge(rank = rank)

            AnimeCoverImage(
                url = anime.coverUrl,
                contentDescription = anime.title,
                modifier = Modifier.size(width = 64.dp, height = 96.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ScrollingText(
                    text = anime.title,
                    style = MaterialTheme.typography.titleSmall
                        .copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = entry.genres.take(2).joinToString(" · ")
                        .ifEmpty { statusLabel(anime.status) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.rank_score,
                        anime.rating?.let {
                            String.format(Locale.ROOT, "%.1f", it)
                        } ?: stringResource(R.string.rank_no_score),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            RankMetric(
                value = anime.rating?.let {
                    String.format(Locale.ROOT, "%.1f", it)
                } ?: "—",
            )
        }
    }
}

/** 前三名高亮徽章（主题色容器），其余为常规序号。 */
@Composable
private fun RankBadge(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    val isTopRank = rank in 1..TOP_RANK_HIGHLIGHT
    if (isTopRank) {
        Surface(
            shape = CircleShape,
            color = when (rank) {
                1 -> MaterialTheme.colorScheme.primaryContainer
                2 -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (rank) {
                        1 -> MaterialTheme.colorScheme.onPrimaryContainer
                        2 -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
        }
    } else {
        Box(
            modifier = modifier.width(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 右侧综合评分：数值突出，形成稳定视觉锚点。 */
@Composable
private fun RankMetric(
    value: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun statusLabel(status: AnimeStatus): String = when (status) {
    AnimeStatus.ONGOING -> stringResource(R.string.rank_status_ongoing)
    AnimeStatus.COMPLETED -> stringResource(R.string.rank_status_completed)
    AnimeStatus.UPCOMING -> stringResource(R.string.rank_status_upcoming)
    AnimeStatus.UNKNOWN -> stringResource(R.string.rank_status_unknown)
}

/** 排行榜骨架屏：与 Tab + 列表布局保持一致，避免数据到达时布局跳动。 */
@Composable
private fun RankSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            repeat(2) {
                SkeletonBox(
                    modifier = Modifier.size(width = 56.dp, height = 14.dp),
                    shape = RoundedCornerShape(7.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(6) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                    )
                    SkeletonBox(
                        modifier = Modifier.size(width = 64.dp, height = 96.dp),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.7f).height(14.dp),
                            shape = RoundedCornerShape(7.dp),
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.5f).height(10.dp),
                            shape = RoundedCornerShape(5.dp),
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.4f).height(10.dp),
                            shape = RoundedCornerShape(5.dp),
                        )
                    }
                    SkeletonBox(
                        modifier = Modifier.size(width = 40.dp, height = 18.dp),
                        shape = RoundedCornerShape(9.dp),
                    )
                }
            }
        }
    }
}

/** 前三名高亮名次的最大名次。 */
private const val TOP_RANK_HIGHLIGHT = 3
