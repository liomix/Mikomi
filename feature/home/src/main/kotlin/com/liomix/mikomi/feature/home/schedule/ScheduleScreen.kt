package com.liomix.mikomi.feature.home.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.AirTime
import com.liomix.mikomi.core.model.AirTimePrecision
import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.core.ui.component.business.AnimeCoverImage
import com.liomix.mikomi.core.ui.component.layout.BaseScreenLayout
import com.liomix.mikomi.core.ui.component.base.EmptyState
import com.liomix.mikomi.core.ui.component.base.ErrorState
import com.liomix.mikomi.core.ui.component.base.ScrollingText
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.component.base.Tab
import com.liomix.mikomi.core.ui.component.base.TabBar
import com.liomix.mikomi.core.ui.component.base.TabItemStyle
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

/** 今天是哪一天时，标签上的星期字替换为「今」。 */
private val TODAY_LABEL = R.string.schedule_today

/** ISO 日期总长度（`yyyy-MM-dd`）。 */
private const val DATE_LENGTH = 10

/** 「月-日」在 ISO 日期中的起始下标（跳过 `yyyy-`）。 */
private const val DATE_MONTH_DAY_START = 5

/** 一周的天数：日历标签栏与骨架屏共用。 */
private const val DAYS_IN_WEEK = 7

/** 日历标签栏容器圆角：比容器内边距（4dp）多 18dp，与两行标签圆角同心。 */
private val TAB_ROW_CORNER_RADIUS = 22.dp

/** 加载状态：骨架 / 数据 / 空 / 错误见 [ScheduleUiState]（随 ViewModel 定义）。 */

/**
 * 时间表：二级页面（周一~周日七天番剧列表），数据来自多源合并后的本周时间表。
 *
 * - 顶栏由 [BaseScreenLayout] 统一提供，返回交给上层导航；
 * - 七天用 [TabBar] 日历标签栏选中（星期 + 月-日，今天显示「今」），
 *   [HorizontalPager] 左右滑动切换，二者联动，默认定位到今天；
 * - 放送时间按三态精度展示：精确时刻 / 约 HH:mm（低优先级源推断）/ 时间待定 / 待公布；
 * - 加载 / 数据 / 空态 / 错误由 [ScheduleUiState] 驱动，错误态可通过
 *   [onRetryClick] 重新拉取。
 */
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    onBackClick: () -> Unit,
    onAnimeTitleClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseScreenLayout(
        title = stringResource(R.string.home_action_schedule),
        modifier = modifier,
        onBackClick = onBackClick,
        // 内容自带滚动（标签栏固定 + Pager 内的 LazyColumn），骨架不再套一层垂直滚动。
        scrollable = false,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        // 各状态都撑满剩余高度：weight 而不是 fillMaxSize，
        // 否则子项会按整列高度测量，把标签栏顶出可视区。
        when (uiState) {
            ScheduleUiState.Loading -> ScheduleSkeleton(
                modifier = Modifier.weight(1f),
            )

            ScheduleUiState.Empty -> EmptyState(
                message = stringResource(R.string.schedule_empty),
                modifier = Modifier.weight(1f),
            )

            ScheduleUiState.Error -> ErrorState(
                message = stringResource(R.string.schedule_error),
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )

            is ScheduleUiState.Success -> ScheduleContent(
                days = uiState.days,
                dates = uiState.dates,
                onAnimeTitleClick = onAnimeTitleClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScheduleContent(
    days: List<List<ScheduleEntry>>,
    dates: List<String>,
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
        DayTabBar(
            dates = dates,
            selectedIndex = selectedDay,
            todayIndex = todayIndex,
            onSelect = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        HorizontalPager(
            state = pagerState,
            // 固定高度的标签栏先占位，剩下全给 Pager；fillMaxSize 会按整列高度测量，把标签栏顶出去。
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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

/**
 * 周日历标签栏：上用星期（今天显示「今」），下用「月-日」。
 *
 * 复用通用标签组件 [TabBar]/[Tab] 的两行形态：选中时整块被主题色淡底包裹，
 * 不画下划线指示器；横向等分七格，无需滚动。
 */
@Composable
private fun DayTabBar(
    dates: List<String>,
    selectedIndex: Int,
    todayIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabBar(
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
        // 整行铺满的七等分卡片，胶囊容器在宽屏上会显得过于圆，改用大圆角矩形。
        shape = RoundedCornerShape(TAB_ROW_CORNER_RADIUS),
        itemStyle = TabItemStyle.twoLine(),
        itemSpacing = 0.dp,
    ) {
        dates.indices.forEach { index ->
            val isToday = index == todayIndex
            Tab(
                index = index,
                text = stringResource(
                    if (isToday) TODAY_LABEL else WEEKDAYS[index % WEEKDAYS.size],
                ),
                supportingText = dates[index].let(::monthDayLabel).takeIf { it.isNotBlank() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** ISO 日期（2026-11-01）→ 月-日（11-01），作为周标签的副标题。 */
private fun monthDayLabel(date: String): String =
    if (date.length >= DATE_LENGTH) date.substring(DATE_MONTH_DAY_START) else date

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
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            // 导航栏留白由 BaseScreenLayout 统一消费，这里只补列表自己的收尾余量。
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items = entries,
            key = { "${it.canonicalKey}:${it.airTime.date}:${it.episode}" },
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
    // 放送信息优先用 base 源的现成文案；没有文案时按三态精度展示。
    val scheduleLabel = entry.updateText.ifBlank { airTimeLabel(entry.airTime) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
            // 信息区顶部与封面顶部对齐（不再整体垂直居中）。
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimeCoverImage(
                url = anime.coverUrl,
                contentDescription = anime.title,
                modifier = Modifier.size(width = 80.dp, height = 120.dp),
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
                    enabled = false,
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
                                color = if (entry.airTime.hasExactTime) {
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
            // 评分圆点仍按封面垂直居中，避免顶到标题行。
            anime.rating?.let { rating ->
                RatingDot(
                    rating = rating,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

/** 放送时间按三态精度展示：精确时刻 / 约 HH:mm（低优先级源推断）/ 时间待定 / 待公布。 */
@Composable
private fun airTimeLabel(airTime: AirTime): String = when {
    airTime.hasExactTime -> {
        val time = airTime.time.orEmpty()
        if (airTime.inferred) {
            stringResource(R.string.schedule_time_approx, time)
        } else {
            time
        }
    }

    airTime.precision == AirTimePrecision.DATE_ONLY ->
        stringResource(R.string.schedule_time_pending)

    else -> stringResource(R.string.schedule_time_tbd)
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
        // 标签栏骨架：与真实标签栏同高同圆角（七等分卡片），避免数据到达时跳动。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(TAB_ROW_CORNER_RADIUS))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(DAYS_IN_WEEK) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(width = 16.dp, height = 16.dp),
                        shape = RoundedCornerShape(5.dp),
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(width = 30.dp, height = 10.dp),
                        shape = RoundedCornerShape(5.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(6) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(width = 80.dp, height = 120.dp),
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

