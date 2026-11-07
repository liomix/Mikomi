package com.liomix.mikomi.feature.profile.collection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiPillShape
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiStroke
import com.liomix.mikomi.core.designsystem.theme.MikomiTextStyles
import com.liomix.mikomi.core.model.Favorite
import com.liomix.mikomi.core.ui.component.base.MikomiIcon
import com.liomix.mikomi.core.ui.component.base.PressButton
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.component.base.SkeletonShimmer
import com.liomix.mikomi.core.ui.component.business.AnimeCoverImage
import com.liomix.mikomi.core.ui.component.business.LocalHeroFlightController
import com.liomix.mikomi.core.ui.component.layout.BaseScreenLayout
import com.liomix.mikomi.feature.profile.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * 收藏页的固定体格。这里是「一个组件的物理尺寸」，不是页面间距，
 * 所以不往 MikomiSpacing 里塞（见 Dimens.kt 文件头的判别方法）。
 */

/**
 * 时间轴竖线与圆点所在的左栏宽度。
 *
 * 取 28dp 是为了让竖线正好落在页面水槽上（`MikomiSpacing.Gutter` 的一半）：
 * 线与其他页面的内容左边线共用一条基准，看过去不是「悬在内容里的装饰线」。
 */
private val TimelineRailWidth = 28.dp

/** 日期节点圆点直径。 */
private val TimelineDotSize = 10.dp

/** 行内缩略图宽度（2:3 竖版，高由 `AnimeCoverImage` 自己按比例算）。 */
private val TimelineThumbWidth = 56.dp

/** 缩略图与文字之间的间距。 */
private val TimelineThumbGap = MikomiSpacing.Md

/** 选中圆点的直径：比封面卡那套（24dp）小一档，列表行里不该压住标题。 */
private val SelectionBadgeSize = MikomiSizes.IconMd

/** 选中圆点里的勾。 */
private val SelectionCheckSize = MikomiSizes.IconXs

/** 骨架行数（一屏大约能摆下这么多）。 */
private const val SkeletonRows = 7

/** 骨架里文字条的高度。 */
private val SkeletonLineHeight = 12.dp

/** 一天有多少毫秒。用来判断「昨天」，只在无夏令时的本地时区上成立。 */
private const val MillisPerDay = 24L * 60L * 60L * 1000L

/**
 * 收藏页 Route：订阅一次状态、把「打开详情」转给导航、渲染 Screen。
 *
 * @param editAnimeId 从个人页长按某张封面进来的话，这里带上那部番的 id：
 *   进页面就处于整理态并选中它，省掉「再点一次」。
 */
@Composable
fun CollectRoute(
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    editAnimeId: String? = null,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 只在进页面时用一次；选中集与「真正存在的条目」在 ViewModel 里求过交，
    // 所以即便这条 id 已经不在收藏里，也不会在列表上留下幽灵高亮。
    LaunchedEffect(editAnimeId) {
        if (editAnimeId != null) viewModel.onEvent(CollectionEvent.StartManage(editAnimeId))
    }

    // 事件转发用同一个实例：每次重组新建 lambda 会让列表每一行都无法跳过重组。
    val onEvent = remember(viewModel, onAnimeClick) {
        { event: CollectionEvent ->
            when (event) {
                is CollectionEvent.Open -> onAnimeClick(event.animeId)
                else -> viewModel.onEvent(event)
            }
        }
    }

    CollectScreen(
        state = state,
        onEvent = onEvent,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

/**
 * 收藏页：时间轴列表 + 整理 / 删除。
 *
 * 为什么是列表而不是网格：收藏是**按时间累积**的东西，用户来这一页的动作通常是
 * 「我最近收了什么」「把上个月乱收的删掉」，时间比版式重要。网格把时间信息压成
 * 一堆同尺寸的方块，只能靠记忆定位；按天分段的时间轴让「什么时候收的」成为
 * 第一眼信息，删除时也更容易圈中同一批。
 *
 * 骨架仍是二级页面的那一套（[BaseScreenLayout]）：顶栏、返回、状态栏与手势条留白
 * 全部由它保证，这里只负责「列表自己滚动」——所以 `scrollable = false`。
 *
 * @param state 收藏状态（与个人页同一个 ViewModel 类型，进这个页面时是新实例）。
 * @param onEvent 用户动作；打开详情由 Route 转给导航。
 * @param onBackClick 顶栏返回。
 */
@Composable
internal fun CollectScreen(
    state: CollectionUiState,
    onEvent: (CollectionEvent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = state.content is CollectionContent.Loaded

    BaseScreenLayout(
        title = stringResource(R.string.profile_collection_title),
        onBackClick = onBackClick,
        modifier = modifier,
        // 列表自己滚：交给骨架的 verticalScroll 会把整页一次性组合出来，
        // 收藏可以很长，那样等于把懒加载全丢掉。
        scrollable = false,
        // 留白由列表自己管（时间轴的线要贴到行首，不能被页面水槽推开）。
        contentPadding = PaddingValues(),
        actions = {
            if (loaded) {
                if (state.managing) {
                    TextButton(onClick = { onEvent(CollectionEvent.ToggleSelectAll) }) {
                        Text(
                            text = stringResource(
                                if (state.allSelected) {
                                    R.string.profile_collection_select_none
                                } else {
                                    R.string.profile_collection_select_all
                                },
                            ),
                        )
                    }
                    TextButton(onClick = { onEvent(CollectionEvent.ToggleManage) }) {
                        Text(text = stringResource(R.string.profile_collection_done))
                    }
                } else {
                    TextButton(onClick = { onEvent(CollectionEvent.ToggleManage) }) {
                        Text(text = stringResource(R.string.profile_collection_edit))
                    }
                }
            }
        },
    ) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val content = state.content) {
                    CollectionContent.Loading -> CollectSkeleton()
                    CollectionContent.Empty -> CollectionEmptyState()
                    is CollectionContent.Loaded -> CollectTimeline(
                        items = content.items,
                        managing = state.managing,
                        selection = state.selection,
                        onEvent = onEvent,
                    )
                }
                CollectUndoBar(
                    undo = state.undo,
                    onUndo = { onEvent(CollectionEvent.Undo) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            // 删除按钮贴在列表下方而不是顶栏：整理时手指在列表里圈选，
            // 动作应该落在拇指够得到的地方。用出现/消失动画而不是常驻，
            // 浏览态不该有一条按不动的按钮占着底部。
            AnimatedVisibility(
                visible = state.managing,
                enter = fadeIn() + slideInVertically { height -> height },
                exit = fadeOut() + slideOutVertically { height -> height },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MikomiSpacing.Gutter, vertical = MikomiSpacing.Md),
                ) {
                    PressButton(
                        text = stringResource(
                            R.string.profile_collection_remove_count,
                            state.selection.size,
                        ),
                        onClick = { onEvent(CollectionEvent.RemoveSelected) },
                        enabled = state.selection.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- 时间轴 */

/** 一段「同一天」的收藏。 */
private class CollectSection(
    val dayStart: Long,
    val items: List<Favorite>,
    /** 是不是第一段：第一段的线从圆点开始，不从行顶开始。 */
    val first: Boolean,
)

/**
 * 按天分段。
 *
 * `Favorite` 从数据库出来已经是 `addedAt DESC`，`groupBy` 用 `LinkedHashMap` 保序，
 * 所以这里不需要再排序（多排一次只是白扫一遍列表）。
 */
@Composable
private fun rememberSections(items: List<Favorite>): List<CollectSection> = remember(items) {
    items.groupBy { startOfDay(it.addedAt) }
        .entries
        .mapIndexed { index, entry ->
            CollectSection(dayStart = entry.key, items = entry.value, first = index == 0)
        }
}

@Composable
private fun CollectTimeline(
    items: List<Favorite>,
    managing: Boolean,
    selection: Set<String>,
    onEvent: (CollectionEvent) -> Unit,
) {
    val sections = rememberSections(items)
    // 三个动作包装成稳定实例：否则每张行都拿到一个新 lambda，滚动时无法跳过重组。
    val open: (String) -> Unit = remember(onEvent) { { onEvent(CollectionEvent.Open(it)) } }
    val toggleSelect: (String) -> Unit = remember(onEvent) {
        { onEvent(CollectionEvent.ToggleSelect(it)) }
    }
    val startManage: (String) -> Unit = remember(onEvent) {
        { onEvent(CollectionEvent.StartManage(it)) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = rememberTimelinePadding(),
    ) {
        sections.forEachIndexed { sectionIndex, section ->
            item(key = "day-${section.dayStart}", contentType = "day") {
                TimelineDayHeader(dayStart = section.dayStart, first = section.first)
            }
            val lastSection = sectionIndex == sections.lastIndex
            itemsIndexed(
                items = section.items,
                key = { _, favorite -> favorite.animeId },
                contentType = { _, _ -> "row" },
            ) { index, favorite ->
                // 只传稳定字段：`Favorite` 是普通 data class，整对象传进去会让每一行都无法跳过。
                TimelineRow(
                    animeId = favorite.animeId,
                    title = favorite.title,
                    coverUrl = favorite.coverUrl,
                    addedAt = favorite.addedAt,
                    managing = managing,
                    selected = favorite.animeId in selection,
                    // 整个时间轴的最后一行：竖线在它身上收口，不再垂到列表底。
                    terminal = lastSection && index == section.items.lastIndex,
                    onOpen = open,
                    onToggleSelect = toggleSelect,
                    onStartManage = startManage,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * 日期节点：圆点 + `今天 / 昨天 / 11月3日`。
 *
 * 圆点与文字共用一行的垂直中线（Row 的 `CenterVertically`），所以圆点永远贴着文字，
 * 不会因为上下留白不对称而上下漂。
 */
@Composable
private fun TimelineDayHeader(dayStart: Long, first: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val todayStart = remember(dayStart) { startOfDay(System.currentTimeMillis()) }
    val label = rememberDayLabel(dayStart = dayStart, todayStart = todayStart)
    val dotColor = if (dayStart == todayStart) scheme.primary else scheme.outline
    val railColor = scheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = TimelineRailWidth.toPx() / 2f
                val centerY = size.height / 2f
                // 第一段不从行顶拉线：那段上方什么都没有，凭空一截像是被裁断的。
                drawLine(
                    color = railColor,
                    start = Offset(x, if (first) centerY else 0f),
                    end = Offset(x, size.height),
                    strokeWidth = MikomiStroke.Thin.toPx(),
                )
                drawCircle(
                    color = dotColor,
                    radius = TimelineDotSize.toPx() / 2f,
                    center = Offset(x, centerY),
                )
            }
            .padding(
                start = TimelineRailWidth,
                top = MikomiSpacing.Room,
                bottom = MikomiSpacing.Xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MikomiTextStyles.SectionTitle,
            color = scheme.onSurface,
        )
    }
}

/**
 * 一行收藏：时间轴上的竖线 + 缩略图 + 标题 + 收藏时刻。
 *
 * 点击：浏览态进详情，整理态切换选中；长按：浏览态直接进整理并选中它，
 * 整理态同样是切换选中。`indication = null` —— 这里的按压层会把整行（含时间轴
 * 那段留白）铺成一块灰，看起来像在显示触控范围。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    animeId: String,
    title: String,
    coverUrl: String?,
    addedAt: Long,
    managing: Boolean,
    selected: Boolean,
    terminal: Boolean,
    onOpen: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onStartManage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val heroController = LocalHeroFlightController.current
    val interaction = remember { MutableInteractionSource() }
    val railColor = scheme.outlineVariant
    val time = remember(addedAt) { formatTime(addedAt) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val x = TimelineRailWidth.toPx() / 2f
                // 最后一行把线收在行中线上（缩略图的垂直中心），
                // 否则线会一直垂到列表底部，像被裁断的一截。
                drawLine(
                    color = railColor,
                    start = Offset(x, 0f),
                    end = Offset(x, if (terminal) size.height / 2f else size.height),
                    strokeWidth = MikomiStroke.Thin.toPx(),
                )
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    if (managing) {
                        onToggleSelect(animeId)
                    } else {
                        // 与首页一致：先记下起点，详情页封面测量完成后再起飞。
                        heroController?.begin(animeId)
                        onOpen(animeId)
                    }
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (managing) onToggleSelect(animeId) else onStartManage(animeId)
                },
            )
            .padding(
                start = TimelineRailWidth,
                top = MikomiSpacing.Tight,
                bottom = MikomiSpacing.Tight,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimeCoverImage(
            url = coverUrl,
            // 标题为空串时不要给读屏念一个空描述。
            contentDescription = title.ifEmpty { null },
            // 整理时行会被圈选，别让封面飞起来 —— 起点是个正在被涂色的格子，像误触。
            heroKey = if (managing) null else animeId,
            crossfade = false,
            shimmerWhileLoading = true,
            modifier = Modifier.width(TimelineThumbWidth),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = TimelineThumbGap),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title.ifEmpty { stringResource(R.string.profile_collection_unknown) },
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(MikomiSpacing.Xxs))
            Text(
                text = time,
                style = MikomiTextStyles.Caption,
                color = scheme.onSurfaceVariant,
            )
        }
        if (managing) {
            SelectionBadge(
                selected = selected,
                modifier = Modifier.padding(start = MikomiSpacing.Md),
            )
        }
    }
}

/**
 * 选中圆点：选中填主色 + 勾，未选中是一个空心圈。
 *
 * 选中与未选中用同一个尺寸、同一个位置，只是填色不同 —— 圈的位置不能动，
 * 否则点一下会看到它在两处之间跳。
 */
@Composable
private fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(SelectionBadgeSize)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(scheme.primary)
                } else {
                    Modifier
                        .background(scheme.surface)
                        .border(MikomiStroke.Thin, scheme.outline, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            MikomiIcon(
                icon = MikomiIcons.Check,
                contentDescription = null,
                tint = scheme.onPrimary,
                size = SelectionCheckSize,
            )
        }
    }
}

/* ---------------------------------------------------------------- 骨架 / 撤销 */

/** 加载态：与真实行同构（缩略图 + 两行文字），数据到了原地替换。 */
@Composable
private fun CollectSkeleton() {
    SkeletonShimmer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = TimelineRailWidth,
                    end = MikomiSpacing.Gutter,
                    top = MikomiSpacing.Sm,
                ),
            verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Md),
        ) {
            repeat(SkeletonRows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SkeletonBox(
                        modifier = Modifier.width(TimelineThumbWidth),
                        shape = RoundedCornerShape(MikomiRadius.Md),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = TimelineThumbGap, top = MikomiSpacing.Tight),
                        verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm),
                    ) {
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(SkeletonLineHeight),
                            shape = RoundedCornerShape(MikomiRadius.Xs),
                        )
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(0.3f)
                                .height(SkeletonLineHeight),
                            shape = RoundedCornerShape(MikomiRadius.Xs),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 撤销条：删完之后贴在列表底部的一条深色胶囊。
 *
 * 位置固定在最下方而不是跟着行走：删除会改变列表长度，跟随式提示会跟着一起跳。
 */
@Composable
private fun CollectUndoBar(
    undo: CollectionUndo?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 退出动画期间 `undo` 已经是 null，这里留一份最后一次的值：
    // 否则横条会在滑出的半路上先变成「已移除 0 部」。
    val lastUndo = remember { mutableStateOf<CollectionUndo?>(null) }
    if (undo != null) lastUndo.value = undo
    val shown = undo ?: lastUndo.value
    val scheme = MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = undo != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { height -> height / 2 },
        exit = fadeOut() + slideOutVertically { height -> height / 2 },
    ) {
        Row(
            modifier = Modifier
                .padding(MikomiSpacing.Md)
                .clip(MikomiPillShape)
                .background(scheme.inverseSurface)
                .padding(
                    start = MikomiSpacing.Lg,
                    end = MikomiSpacing.Sm,
                    top = MikomiSpacing.Xs,
                    bottom = MikomiSpacing.Xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.profile_collection_removed,
                    shown?.items?.size ?: 0,
                ),
                style = MikomiTextStyles.Caption,
                color = scheme.inverseOnSurface,
            )
            Spacer(modifier = Modifier.width(MikomiSpacing.Xs))
            TextButton(onClick = onUndo) {
                Text(
                    text = stringResource(R.string.profile_collection_undo),
                    style = MikomiTextStyles.Caption,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.inverseOnSurface,
                )
            }
        }
    }
}

/**
 * 列表内边距：左右只留右侧的页面水槽，顶部 4dp、底部 12dp。
 *
 * 左侧不留水槽，是因为时间轴的竖线正好落在水槽那条线上（[TimelineRailWidth] 的一半），
 * 内容再被这半边推到右侧 —— 竖线于是与页面其它内容的左边线对齐，而不是悬在半空。
 * `PaddingValues` 是接口类型（Compose 视为不稳定），每次重组新建会让列表白跑一次重排，
 * 所以固定实例。
 */
@Composable
private fun rememberTimelinePadding(): PaddingValues = remember {
    PaddingValues(
        end = MikomiSpacing.Gutter,
        top = MikomiSpacing.Xs,
        bottom = MikomiSpacing.Md,
    )
}

/* ---------------------------------------------------------------- 日期 */

/** 当天零点。用 [Calendar] 而不是 `java.time`：minSdk 24 且未开 core library desugaring。 */
private fun startOfDay(millis: Long): Long = Calendar.getInstance().run {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    timeInMillis
}

/**
 * 日期节点的文案：今天 / 昨天 / `11月3日` / `2025年11月3日`。
 *
 * 跨年才带上年份：同一年里每条都带年份，等于把真正有信息量的「月日」淹掉。
 */
@Composable
private fun rememberDayLabel(dayStart: Long, todayStart: Long): String = when (dayStart) {
    todayStart -> stringResource(R.string.profile_collection_today)
    todayStart - MillisPerDay -> stringResource(R.string.profile_collection_yesterday)
    else -> remember(dayStart, todayStart) { formatDay(dayStart, todayStart) }
}

private fun formatDay(dayStart: Long, todayStart: Long): String {
    val sameYear = yearOf(dayStart) == yearOf(todayStart)
    val pattern = if (sameYear) "M'月'd'日'" else "yyyy'年'M'月'd'日'"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(dayStart))
}

/** 行内时刻（收藏时间）。 */
private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun yearOf(millis: Long): Int =
    Calendar.getInstance().run { timeInMillis = millis; get(Calendar.YEAR) }
