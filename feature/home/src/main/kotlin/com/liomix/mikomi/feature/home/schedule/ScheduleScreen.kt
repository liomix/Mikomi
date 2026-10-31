package com.liomix.mikomi.feature.home.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.core.ui.component.AnimeCoverImage
import com.liomix.mikomi.core.ui.component.EmptyState
import com.liomix.mikomi.core.ui.component.ErrorState
import com.liomix.mikomi.core.ui.component.ScrollingText
import com.liomix.mikomi.core.ui.component.SkeletonBox
import com.liomix.mikomi.feature.home.R
import java.util.Calendar
import java.util.Locale
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

/** 加载状态：骨架 / 数据 / 空 / 错误。 */
sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState

    data class Success(val days: List<List<ScheduleEntry>>) : ScheduleUiState

    data object Empty : ScheduleUiState

    data object Error : ScheduleUiState
}

/**
 * 时间表：二级页面（周一~周日七天番剧列表），数据来自 AniRpb 每周更新表。
 *
 * - 顶部 [CenterAlignedTopAppBar]，返回交给上层导航；
 * - 七天用 [ScrollableTabRow] 选中，[HorizontalPager] 左右滑动切换，二者联动，
 *   默认定位到今天；
 * - 加载 / 数据 / 空态 / 错误由 [ScheduleUiState] 驱动，错误态可通过
 *   [onRetryClick] 重新拉取。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    onBackClick: () -> Unit,
    onAnimeTitleClick: (String) -> Unit,
    onRetryClick: () -> Unit,
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

            ScheduleUiState.Error -> ErrorState(
                message = stringResource(R.string.schedule_error),
                onRetry = onRetryClick,
                modifier = Modifier.padding(innerPadding),
            )

            is ScheduleUiState.Success -> ScheduleContent(
                days = uiState.days,
                onAnimeTitleClick = onAnimeTitleClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ScheduleContent(
    days: List<List<ScheduleEntry>>,
    onAnimeTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 默认定位到今天；Tab 高亮直接取 Pager 当前页，双向联动无需额外状态。
    val todayIndex = remember { todayWeekdayIndex() }
    val pagerState = rememberPagerState(
        initialPage = todayIndex,
        pageCount = { days.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val selectedDay = pagerState.currentPage

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
                entries = days[page],
                isToday = page == todayIndex,
                onAnimeTitleClick = onAnimeTitleClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 单日番剧列表：左封面 + 右标题/放送信息/简介。 */
@Composable
private fun DayList(
    entries: List<ScheduleEntry>,
    isToday: Boolean,
    onAnimeTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        EmptyState(
            message = if (isToday) {
                stringResource(R.string.schedule_empty)
            } else {
                stringResource(R.string.schedule_day_empty)
            },
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
        items(
            items = entries,
            key = { "${it.anime.id}:${it.episode}" },
            contentType = { "schedule_entry" },
        ) { entry ->
            ScheduleAnimeRow(
                entry = entry,
                onClick = { onAnimeTitleClick(entry.anime.title) },
            )
        }
    }
}

@Composable
private fun ScheduleAnimeRow(
    entry: ScheduleEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anime = entry.anime
    val fallbackEpisode = if (entry.episode > 0) {
        stringResource(R.string.schedule_episode, entry.episode)
    } else {
        ""
    }
    val episodeLabel = entry.episodeText.ifBlank { fallbackEpisode }
    val scheduleLabel = entry.updateText.ifBlank {
        if (entry.airingAtMillis > 0) formatAiringTime(entry.airingAtMillis) else ""
    }

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
            AnimeCoverImage(
                url = anime.coverUrl,
                contentDescription = anime.title,
                modifier = Modifier.size(width = 64.dp, height = 96.dp),
                browserUserAgent = true,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ScrollingText(
                    text = anime.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (episodeLabel.isNotBlank() || scheduleLabel.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (episodeLabel.isNotBlank()) {
                            Text(
                                text = episodeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (scheduleLabel.isNotBlank()) {
                            Text(
                                text = scheduleLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (entry.updateText.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                Text(
                    text = anime.synopsis ?: stringResource(R.string.schedule_no_synopsis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // AniRpb 更新表没有评分字段；不要显示一个无意义的「—」圆点。
            anime.rating?.let { RatingDot(rating = it) }
        }
    }
}

/** 放送时刻（本地时区 HH:mm）。 */
private fun formatAiringTime(millis: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    return String.format(
        Locale.ROOT,
        "%02d:%02d",
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
}

/** 今天是本周第几天（周一=0）。Calendar 的 DAY_OF_WEEK 周日=1。 */
private fun todayWeekdayIndex(): Int {
    val calendar = Calendar.getInstance()
    return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
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
                text = rating?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—",
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

