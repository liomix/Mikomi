package com.liomix.mikomi.feature.home.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.PillShape
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.anime.AirTime
import com.liomix.mikomi.core.model.anime.AirTimePrecision
import com.liomix.mikomi.core.model.anime.ScheduleEntry
import com.liomix.mikomi.core.ui.base.EmptyState
import com.liomix.mikomi.core.ui.base.ErrorState
import com.liomix.mikomi.core.ui.base.ScrollingText
import com.liomix.mikomi.core.ui.base.SkeletonBox
import com.liomix.mikomi.core.ui.base.Tab
import com.liomix.mikomi.core.ui.base.TabBar
import com.liomix.mikomi.core.ui.base.TabItemStyle
import com.liomix.mikomi.core.ui.business.AnimeCoverImage
import com.liomix.mikomi.core.ui.layout.BaseScreenLayout
import com.liomix.mikomi.feature.home.R
import java.util.Calendar
import java.util.Locale
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.mikomiClickable
import com.liomix.mikomi.core.ui.business.RefreshButton

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

/** 骨架列表的行数：多于一屏可见量，小屏滚动时也不露底。 */
private const val SKELETON_ROW_COUNT = 6

/** 日历标签栏容器圆角：比容器内边距（4dp）多 18dp，与两行标签圆角同心。 */
private val TAB_ROW_CORNER_RADIUS = 22.dp

/**
 * 时间表：二级页面（周一~周日七天番剧列表），数据来自多源合并后的本周时间表。
 *
 * - 顶栏由 [BaseScreenLayout] 统一提供，返回交给上层导航；
 * - 七天用 [TabBar] 日历标签栏选中（星期 + 月-日，今天显示「今」），
 *   [HorizontalPager] 左右滑动切换，二者联动，默认定位到今天；
 * - 放送时间按三态精度展示：精确时刻 / 约 HH:mm（低优先级源推断）/ 时间待定 / 待公布；
 * - 加载 / 数据 / 空态 / 错误由 [ScheduleContent] 驱动，错误态可通过
 *   [onRefresh] 重新拉取；
 * - 顶栏右侧常驻刷新按钮，刷新中换成进度环（组件见 [RefreshButton]）。
 */
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    onBackClick: () -> Unit,
    onAnimeTitleClick: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseScreenLayout(
        title = stringResource(R.string.home_action_schedule),
        modifier = modifier,
        onBackClick = onBackClick,
        actions = {
            RefreshButton(
                isRefreshing = uiState.isRefreshing,
                contentDescription = stringResource(R.string.schedule_refresh),
                onClick = onRefresh,
            )
        },
        // 内容自带滚动（标签栏固定 + Pager 内的 LazyColumn），骨架不再套一层垂直滚动。
        scrollable = false,
        contentPadding = PaddingValues(Spacing.None),
        verticalArrangement = Arrangement.Top,
    ) {
        // 各状态都撑满剩余高度：weight 而不是 fillMaxSize，
        // 否则子项会按整列高度测量，把标签栏顶出可视区。
        when (val content = uiState.content) {
            ScheduleContent.Loading -> ScheduleSkeleton(
                modifier = Modifier.weight(1f),
            )

            ScheduleContent.Empty -> EmptyState(
                message = stringResource(R.string.schedule_empty),
                modifier = Modifier.weight(1f),
            )

            ScheduleContent.Error -> ErrorState(
                message = stringResource(R.string.schedule_error),
                onRetry = onRefresh,
                modifier = Modifier.weight(1f),
            )

            is ScheduleContent.Success -> ScheduleContentBody(
                days = content.days,
                dates = content.dates,
                onAnimeTitleClick = onAnimeTitleClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScheduleContentBody(
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

    Column(modifier = modifier.fillMaxSize()) {
        DayTabBar(
            dates = dates,
            todayIndex = todayIndex,
            // 与 pager 联动：拖动时高亮跟手在两格之间移动，松手落页。
            pagerState = pagerState,
            // 切页由 TabBar 自己做：时长与指示器同源。
            onSelect = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
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
    todayIndex: Int,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabBar(
        // 选中项就是 pager 的当前页：两者本来就是一回事，再传一个 `selectedIndex`
        // 只会多出一个可能不一致的来源。
        selectedIndex = pagerState.currentPage,
        onSelect = onSelect,
        modifier = modifier,
        // 整行铺满的七等分卡片，胶囊容器在宽屏上会显得过于圆，改用大圆角矩形。
        shape = RoundedCornerShape(TAB_ROW_CORNER_RADIUS),
        itemStyle = TabItemStyle.twoLine(),
        itemSpacing = Spacing.None,
        // 指示器跟手：`TabBar` 读 pager 的 `currentPage + offsetFraction` 插值，
        // 不传这一项就只能在松手后一次性弹到新格。
        pagerState = pagerState,
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
            start = Spacing.Md,
            end = Spacing.Md,
            top = Spacing.Sm,
            // 导航栏留白由 BaseScreenLayout 统一消费，这里只补列表自己的收尾余量。
            bottom = Spacing.Xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.Compact),
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

    ScheduleRowCard(modifier = modifier, onClick = onClick) {
        AnimeCoverImage(
            url = anime.coverUrl,
            contentDescription = anime.title,
            modifier = Modifier.size(width = 80.dp, height = 120.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
                ScrollingText(
                    text = anime.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (episodeLabel.isNotBlank() || scheduleLabel.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    ) {
                        if (episodeLabel.isNotBlank()) {
                            Text(
                                text = episodeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (scheduleLabel.isNotBlank()) {
                            // 时钟 + 时间是一个整体：颜色由「是否精确时刻」决定，
                            // 图标跟着文字走，不然会在主色文字旁留一颗灰图标。
                            val timeColor = if (entry.airTime.hasExactTime) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs),
                            ) {
                                Icon(
                                    icon = Icons.Clock,
                                    contentDescription = null,
                                    tint = timeColor,
                                    // 夹在 12sp 行里的小图标：16dp，与文字基线视觉等重。
                                    size = Sizes.IconXs,
                                )
                                Text(
                                    text = scheduleLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = timeColor,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = anime.synopsis ?: stringResource(R.string.schedule_no_synopsis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 三行：一句简介通常两三行收住，再长就该去详情页看。
                    maxLines = SynopsisMaxLines,
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

/**
 * 列表行的外壳：底色卡 + 内边距 + 横向排布。
 *
 * 真实行与骨架行共用它，所以「骨架 → 内容」换掉的只有里面的块，卡片材质、圆角、
 * 内边距、行高都不变。骨架曾经自己搭了一个 `Row`，于是数据到达时整行从「透明背景」
 * 变成「有底色卡片」，一屏六行同时跳一次。
 *
 * @param onClick 为 null 时不可点（骨架行）。
 */
@Composable
private fun ScheduleRowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val cardShape = RoundedCornerShape(Radius.Lg)
    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(if (onClick == null) Modifier else Modifier.mikomiClickable(onClick = onClick)),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.Sm,
                top = Spacing.Sm,
                end = Spacing.Md,
                bottom = Spacing.Sm,
            ),
            // 信息区顶部与封面顶部对齐（不再整体垂直居中）。
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
            content = content,
        )
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
        modifier = modifier.size(Sizes.AvatarMd),
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
                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm)
                .clip(RoundedCornerShape(TAB_ROW_CORNER_RADIUS))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Spacing.Xs, vertical = Spacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
        ) {
            repeat(DAYS_IN_WEEK) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizes.RowDouble),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(width = 16.dp, height = 16.dp),
                        shape = RoundedCornerShape(Radius.Sm),
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .padding(top = Spacing.Tight)
                            .size(width = 30.dp, height = 10.dp),
                        shape = PillShape,
                    )
                }
            }
        }

        // 列表部分的留白与真实列表一致（左/右 Md、上 Sm、行间 Compact），
        // 这样数据到达时骨架行是「同一个位置换了内容」，不是「列表整体挪一下」。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Spacing.Md, top = Spacing.Sm, end = Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Compact),
        ) {
            repeat(SKELETON_ROW_COUNT) {
                // 与真实行同一个外壳：底色、圆角、内边距都由 ScheduleRowCard 决定。
                ScheduleRowCard {
                    SkeletonBox(
                        modifier = Modifier.size(width = 80.dp, height = 120.dp),
                        shape = RoundedCornerShape(Radius.Md),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    ) {
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.7f).height(14.dp),
                            shape = PillShape,
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.9f).height(10.dp),
                            shape = PillShape,
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.5f).height(10.dp),
                            shape = PillShape,
                        )
                    }
                }
            }
        }
    }
}

/** 单日列表里简介的行数上限：三行之后开始省略。 */
private const val SynopsisMaxLines = 3
