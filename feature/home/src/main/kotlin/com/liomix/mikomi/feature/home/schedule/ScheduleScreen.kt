package com.liomix.mikomi.feature.home.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.EmptyState
import com.liomix.mikomi.core.ui.component.SkeletonBox
import com.liomix.mikomi.feature.home.R
import kotlinx.coroutines.launch

/** 一周七天（周一 ~ 周日）对应的标签索引。 */
private val WEEKDAYS = listOf(
    R.string.schedule_monday,
    R.string.schedule_tuesday,
    R.string.schedule_wednesday,
    R.string.schedule_thursday,
    R.string.schedule_friday,
    R.string.schedule_saturday,
    R.string.schedule_sunday,
)

/** 加载状态：骨架 / 数据 / 空。 */
sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState

    data class Success(val days: List<List<Anime>>) : ScheduleUiState

    data object Empty : ScheduleUiState
}

/**
 * 时间表：二级页面（周一~周日七天番剧列表）。
 *
 * - 顶部 [CenterAlignedTopAppBar]，返回交给上层导航；
 * - 七天用 [ScrollableTabRow] 选中，[HorizontalPager] 左右滑动切换，二者双向联动；
 * - 数据源未接入时由 [onActionClick] 传入的 [ScheduleUiState] 驱动 UI，
 *   由调用方决定展示骨架 / 数据 / 空态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_action_schedule),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.schedule_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            ScheduleUiState.Loading -> ScheduleSkeleton(
                modifier = Modifier.padding(innerPadding),
            )

            ScheduleUiState.Empty -> EmptyState(
                message = stringResource(R.string.schedule_empty),
                modifier = Modifier.padding(innerPadding),
            )

            is ScheduleUiState.Success -> ScheduleContent(
                days = uiState.days,
                onAnimeClick = onAnimeClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ScheduleContent(
    days: List<List<Anime>>,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 七天 Tab 与 Pager 双向联动：点击 Tab 跳页，滑动页同步 Tab。
    val pagerState = rememberPagerState(pageCount = { days.size })
    val coroutineScope = rememberCoroutineScope()
    var selectedDay by remember(pagerState.currentPage) { mutableStateOf(pagerState.currentPage) }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedDay,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {},
        ) {
            days.indices.forEach { index ->
                Tab(
                    selected = selectedDay == index,
                    onClick = {
                        selectedDay = index
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = {
                        Text(
                            text = stringResource(WEEKDAYS[index % WEEKDAYS.size]),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedDay == index) {
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
            DayList(
                anime = days[page],
                onAnimeClick = onAnimeClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 单日番剧列表：左封面 + 右标题/简介占位。 */
@Composable
private fun DayList(
    anime: List<Anime>,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (anime.isEmpty()) {
        EmptyState(
            message = stringResource(R.string.schedule_empty),
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
            bottom = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = anime, key = { it.id }) { item ->
            ScheduleAnimeRow(
                anime = item,
                onClick = { onAnimeClick(item.id) },
            )
        }
    }
}

@Composable
private fun ScheduleAnimeRow(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.title,
                modifier = Modifier
                    .size(width = 64.dp, height = 96.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = anime.synopsis ?: stringResource(R.string.schedule_no_synopsis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            RatingDot(rating = anime.rating)
        }
    }
}

/** 右侧评分圆点：评分缺失时显示占位圆圈。 */
@Composable
private fun RatingDot(
    rating: Double?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(40.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = rating?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "—",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 时间表骨架屏：与 Tab + 列表布局保持一致，避免数据到达时布局跳动。 */
@Composable
private fun ScheduleSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            repeat(4) {
                SkeletonBox(
                    modifier = Modifier.size(width = 44.dp, height = 14.dp),
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
                            modifier = Modifier.fillMaxWidth(0.9f).height(10.dp),
                            shape = RoundedCornerShape(5.dp),
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.5f).height(10.dp),
                            shape = RoundedCornerShape(5.dp),
                        )
                    }
                }
            }
        }
    }
}
