package com.liomix.mikomi.feature.search

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Motion
import com.liomix.mikomi.core.designsystem.PillShape
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.Stroke
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.ScrollingText
import com.liomix.mikomi.core.ui.base.SkeletonBox
import com.liomix.mikomi.core.ui.base.Tab
import com.liomix.mikomi.core.ui.base.TabBar
import com.liomix.mikomi.core.ui.base.TagStyle
import com.liomix.mikomi.core.ui.base.Tag
import com.liomix.mikomi.core.ui.base.TabHighlight
import com.liomix.mikomi.core.ui.base.TabItemStyle
import com.liomix.mikomi.core.ui.business.AnimeCoverImage
import java.util.Locale
import com.liomix.mikomi.core.ui.base.mikomiClickable

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
        contentPadding = PaddingValues(bottom = Spacing.Xl),
    ) {
        if (state.history.isNotEmpty()) {
            item(key = "history_header") {
                SectionHeader(
                    icon = Icons.History,
                    title = stringResource(R.string.search_history),
                    // 紧贴搜索框，减少顶部空白。
                    topPadding = Spacing.Xxs,
                    action = {
                        IconButton(onClick = onClearHistory) {
                            Icon(
                                icon = Icons.Delete,
                                contentDescription = stringResource(R.string.search_history_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                size = Sizes.IconMd,
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
                icon = Icons.Whatshot,
                title = stringResource(R.string.search_hot_ranking),
            )
        }

        // 标签栏与榜单是**同一张卡片**的两部分：卡片顶部一行分类，蓝线之下是内容。
        // 分成两项时标签浮在卡片外，读起来像两个不相干的东西，切换也像换了一整个区块。
        item(key = "hot_card") {
            HotCard(
                selected = state.hotTab,
                onSelect = onHotTabChange,
            ) {
                // 内容按 Tab 分组：切换时新榜单重新播一次淡入上移，标签栏本身不动。
                key(state.hotTab) {
                    when (state.currentHotState) {
                        LoadState.Idle,
                        LoadState.Loading,
                        -> HotSkeleton()

                        LoadState.Error -> RetryHint(
                            message = stringResource(R.string.search_load_failed),
                            onRetry = onRetryHot,
                        )

                        LoadState.Success -> {
                            if (state.currentHot.isEmpty()) {
                                EmptyHint(text = stringResource(R.string.search_hot_empty))
                            } else {
                                HotRankingList(
                                    items = state.currentHot.take(HOT_LIMIT),
                                    onItemClick = onHotItemClick,
                                )
                            }
                        }
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
            .padding(start = Spacing.Lg, end = Spacing.Sm, top = topPadding, bottom = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            size = Sizes.IconMd,
        )
        Spacer(modifier = Modifier.width(Spacing.Sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * 日漫 / 国漫热播榜切换。
 *
 * 用**下划线**而不是胶囊底：这两栏是同一种内容的两个分类，胶囊会把其中一栏读成
 * 「被按下的按钮」，压过下面真正要点的那张榜单卡片。
 */
@Composable
private fun HotTabs(
    selected: SearchHotTab,
    onSelect: (SearchHotTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabBar(
        selectedIndex = selected.ordinal,
        onSelect = { index -> onSelect(SearchHotTab.entries[index]) },
        // 内边距走 contentPadding 而不是 modifier：分割线画在 padding 外层，
        // 才能从屏幕左边缘贯到右边缘；挂到 modifier 上会让线两端各缩进 16。
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.Xs, vertical = Spacing.None),
        containerColor = Color.Transparent,
        // 40dp：下划线形态不需要胶囊那圈厚度，48 会留出一条谁也用不上的空白带。
        itemStyle = TabItemStyle.text().copy(height = Sizes.ChipHeight),
        itemSpacing = Spacing.None,
        highlight = TabHighlight.Underline,
    ) {
        SearchHotTab.entries.forEachIndexed { index, tab ->
            Tab(
                index = index,
                text = stringResource(tab.labelRes()),
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
            .padding(horizontal = Spacing.Lg)
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        visible.forEach { keyword ->
            HistoryTab(
                keyword = keyword,
                onClick = { onTagClick(keyword) },
                onLongClick = { onTagLongClick(keyword) },
            )
        }
        if (collapsible) {
            ExpandTab(expanded = expanded, onClick = onToggleExpand)
        }
    }
}

/** 历史关键词 chip：细描边 + 透明底，长词截断，保持一屏内可扫读。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryTab(
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
        border = BorderStroke(Stroke.Thin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm),
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
private fun ExpandTab(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = CHIP_MIN_HEIGHT)
            .clip(CircleShape)
            .mikomiClickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(Stroke.Thin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.Gutter,
                end = Spacing.Compact,
                top = Spacing.Sm,
                bottom = Spacing.Sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.search_history_collapse else R.string.search_history_expand,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(Spacing.Xxs))
            Icon(
                icon = if (expanded) Icons.ExpandLess else Icons.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = Sizes.IconMd,
            )
        }
    }
}

/** 热播榜卡片：一次淡入上移，避免滚动回看时反复播动画。 */
@Composable
private fun HotRankingList(
    items: List<AniRpbHotItem>,
    onItemClick: (AniRpbHotItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.Duration.Medium),
        label = "hotAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (revealed) 0f else 16f,
        animationSpec = tween(durationMillis = Motion.Duration.Medium),
        label = "hotOffset",
    )

    Column(
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

/** 空提示：与列表同一个卡片，只占一行高度。 */
@Composable
private fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Xxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 热播榜卡片：顶部是日/国切换，其下就是榜单本身。
 *
 * 标签栏放在卡片里面而不是外面 —— 蓝线是卡片的**内部分界**，不是「页面上两个区块之间
 * 的分割线」。所以它必须与内容同宽、同底，切换时只有内容换面，卡片不动。
 */
@Composable
private fun HotCard(
    selected: SearchHotTab,
    onSelect: (SearchHotTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BillboardSurface(modifier = modifier) {
        HotTabs(selected = selected, onSelect = onSelect)
        content()
    }
}

/** 榜单卡片外壳：圆角 + 低层容器色，内部行自带点击态。 */
@Composable
private fun BillboardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        // 12dp 而不是 20：这张卡里最高的是 84dp 的封面，大圆角会把四角切得像贴纸；
        // 与页面其它卡片同一档也更好看。
        shape = RoundedCornerShape(Radius.Lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Lg),
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.Tight)) {
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
            // 左右各 8dp：徽标要在「卡片边框」与「封面」之间**居中**，
            // 所以它到边框的距离必须等于它到封面的距离。之前是 12 / 6，读起来偏左。
            .padding(horizontal = Spacing.Sm)
            .clip(RoundedCornerShape(Radius.Xl))
            .mikomiClickable(onClick = onClick)
            .padding(horizontal = Spacing.None, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(rank = item.rank)
        Spacer(modifier = Modifier.width(Spacing.Sm))
        AnimeCoverImage(
            url = item.coverUrl,
            contentDescription = item.title,
            // 56dp 宽（2:3 → 84dp 高）：这一行只有名次、封面、标题和两个标签，
            // 封面是唯一能让人一眼认出片子的东西，太小就白占一行的位置。
            modifier = Modifier.width(56.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.Sm))
        Column(modifier = Modifier.weight(1f)) {
            ScrollingText(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth(),
            )
            // 信息侧只留两个标签：评分与播放量。集数、更新文案都属于详情页的信息，
            // 挤在这里会让每一行都变成一条小字带，反而看不出重点。
            if (item.score != null || item.playCount > 0) {
                Spacer(modifier = Modifier.height(Spacing.Tight))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs),
                ) {
                    // 圆头胶囊 + 图标跟随内容色：这两个标签是同一行的同级信息，
                    // 给评分单独一颗金色星标会让「播放量」看起来是另一类东西。
                    // 底色与文字都来自 `TagTone.Neutral`（主色容器），随主题换。
                    item.score?.let { score ->
                        Tag(
                            text = stringResource(
                                R.string.search_hot_score,
                                String.format(Locale.ROOT, "%.1f", score),
                            ),
                            style = hotMetaTagStyle,
                            leadingIcon = Icons.Star,
                        )
                    }
                    if (item.playCount > 0) {
                        Tag(
                            text = formatPlayCount(item.playCount),
                            style = hotMetaTagStyle,
                            leadingIcon = Icons.Play,
                        )
                    }
                }
            }
            // 动漫标签：纯文字不加胶囊。它是这一行的注脚（年份 / 类型 / 关键词），
            // 再套一层标签框就会和上面的评分、播放量抢注意力。
            if (item.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.Xxs))
                Text(
                    text = item.tags.joinToString("  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatPlayCount(count: Long): String = when {
    count >= 100_000_000 -> String.format(Locale.ROOT, "%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format(Locale.ROOT, "%.1f万", count / 10_000.0)
    else -> count.toString()
}

/** 榜单骨架屏：与真实行同构（数字块 + 封面块 + 两行文本）。 */
@Composable
private fun HotSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(HOT_SKELETON_COUNT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(
                    modifier = Modifier.size(width = 22.dp, height = 16.dp),
                    shape = RoundedCornerShape(Radius.Xs),
                )
                Spacer(modifier = Modifier.width(Spacing.Md))
                SkeletonBox(
                    // 与真实封面同尺寸（56dp 宽、2:3），数据到位时这一行不改变高度。
                    modifier = Modifier.size(width = 56.dp, height = 84.dp),
                    shape = RoundedCornerShape(Radius.Md),
                )
                Spacer(modifier = Modifier.width(Spacing.Md))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp),
                        shape = PillShape,
                    )
                    // 信息侧是两个标签（评分 / 播放量），骨架也占两个小胶囊的位置。
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs)) {
                        SkeletonBox(
                            modifier = Modifier.size(width = 44.dp, height = 18.dp),
                            shape = PillShape,
                        )
                        SkeletonBox(
                            modifier = Modifier.size(width = 40.dp, height = 18.dp),
                            shape = PillShape,
                        )
                    }
                }
            }
        }
    }
}

/** 卡片内提示（空态）。 */


/** 卡片内失败提示 + 重试。 */
/** 失败提示：同样长在卡片里，所以不是一张「错误卡片」，而是这栏内容暂时是空的。 */
@Composable
private fun RetryHint(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Xl),
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

/**
 * 名次徽标：一个圆，数字居中。
 *
 * 颜色与排行榜名片同源（`AppTheme.colors.rank`）：前三名取金属色的淡底 + 深墨数字
 * （`fill` 只用于底，`ink` 用于字 —— 金属色的 `fill` 直接当字会在浅底上发虚），
 * 第 4 名起落到中性容器色。
 */
@Composable
private fun RankBadge(rank: Int) {
    val metal = AppTheme.colors.rank(rank)
    val container = metal?.fill?.copy(alpha = RankBadgeFillAlpha)
        ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val content = metal?.ink ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(Sizes.RankBadge)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            // 行高直接取字号：`labelLarge` 的 20sp 行高是在 12sp 字之外多加的上下空当，
            // 盒子按行高居中，字就偏上。行高 = 字号之后布局高度贴着字形，Box 的中心
            // 才是字形的中心；`includeFontPadding = false` 再去掉字体自带的留白。
            style = rankBadgeTextStyle,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}

/** 金属色做底时的浓度：再深会压过封面上的主色，再浅就看不出是金/银/铜。 */
private const val RankBadgeFillAlpha = 0.18f

/**
 * 热播行的元信息标签：胶囊（圆头）+ 主色容器底。
 *
 * `TagStyle.meta()` 原本是 6dp 小圆角 —— 那是「贴着文字的一块底」，适合在正文里
 * 当注脚；这一行里它和封面并排、离文字更远，圆头才不显得像一块硬边小方块。
 */
private val hotMetaTagStyle: TagStyle
    @Composable get() = TagStyle.meta().copy(shape = PillShape)

/** 名次徽标里的数字：行高收到字号本身，让字形的中心就是盒子的中心。 */
private val rankBadgeTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelLarge.let { base ->
        base.copy(
            lineHeight = base.fontSize,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }

