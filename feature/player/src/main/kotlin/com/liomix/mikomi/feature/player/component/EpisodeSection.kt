package com.liomix.mikomi.feature.player.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.ui.component.ScrollingText
import com.liomix.mikomi.core.ui.component.SkeletonBox
import com.liomix.mikomi.feature.player.EpisodeItem
import com.liomix.mikomi.feature.player.EpisodeOrder
import com.liomix.mikomi.feature.player.R
import kotlinx.coroutines.launch

/**
 * 卡片最小宽度：360dp 竖屏下正好 3 列（与参考图一致），
 * 更宽的窗口自动增列，平板上不会被拉成巨型卡片。
 */
private val EpisodeMinWidth = 96.dp

/**
 * 网格的内边距与间距：照 Dart-Mikomi 视频页的 `EdgeInsets.symmetric(horizontal: 16)`
 * 与 `crossAxisSpacing/mainAxisSpacing: 12`。间距从 10 提到 12 不是为了好看——
 * 卡片的**高度**由列宽除以宽高比算出来，间距会挤进列宽，差 2dp 高度就差 1dp。
 */
private val EpisodeGridPadding = 16.dp
private val EpisodeGridSpacing = 12.dp

/**
 * 卡片宽高比，照 Dart-Mikomi 的 `childAspectRatio: 2.2`。
 *
 * 用比例而不是固定高度：列宽随屏宽变，卡片跟着一起变，平板上的 4 列不会变成一排又扁又长的条。
 */
private const val EpisodeCardAspectRatio = 2.2f

/**
 * 选集区顶部两个胶囊（排序按钮、总集数）共用的高度。
 *
 * 它们并排站在一起，差 2dp 都会被一眼看出来，所以高度只在这里写一次。
 * 比卡片低一档是刻意的：这一行是「操作 + 统计」，不该跟剧集本身抢注意力。
 */
private val EpisodeChipHeight = 32.dp

/**
 * 两个胶囊（排序按钮、总集数）的最小宽度，同时也是**骨架屏的宽度**。
 *
 * 「正序」两个字加箭头比「共 5 集」短，不兜一个下限的话，集数少的番里两个胶囊会一长一短。
 * 68dp 是「共 12 集」这类常见文案的宽度，两边都按它对齐；
 * 骨架屏也照这个尺寸铺，数据到位时不会突然变宽。
 */
private val EpisodeChipMinWidth = 68.dp

/**
 * 剧集区默认只显示这么多集，超出的部分等用户点「展开」。
 *
 * 长番（几百集）真把全部卡片铺出来，用户在一片数字里找不到自己在看哪一集，
 * 而且首屏测量/绘制的成本随集数线性上升。20 集正好是竖屏三列下五六行的量。
 */
private const val EPISODE_PREVIEW_COUNT = 20

/**
 * 悬浮按钮直径：与详情页的收藏按钮同尺寸（48dp）。
 *
 * 以前这里是 36dp 的小圆钮，而且靠 `contentPadding.end` 给网格让位——那是错的：
 * 让位是从网格的可用宽度里扣，列宽被压窄、整块剧集跟着缩，用户看到的就是「按钮挤剧集」。
 * 详情页的做法正好相反：按钮浮在内容**上面**，内容一行字都不挪，只在滚到底时留出空白。
 */
private val EpisodeRailButtonSize = 48.dp

/**
 * 网格底部额外留白：避让右下角悬浮按钮组（48 + 间距 12 + 48 + 外边距 24 + 余量）。
 *
 * 只影响「滚到最底」时的空白，不参与列宽计算——这正是它不会挤压剧集的原因。
 */
private val EpisodeRailClearance = 144.dp

/**
 * 选集区：排序头 + 自适应网格 + 右侧悬浮按钮列。
 *
 * - 列数按屏宽算（360dp 竖屏正好 3 列），卡片宽高比固定 2.2，尺寸与 Dart-Mikomi 视频页一致；
 * - 排序按钮与卡片同宽同高，看上去是网格的第一格；
 * - 网格自己滚动，播放器与 Tab 栏固定不动；
 * - 进入页面时把当前集滚到可见位置（只做一次，之后用户滚动不再被拉回）；
 * - 超过 [EPISODE_PREVIEW_COUNT] 集时默认截断，右侧给「展开全部」，并始终给「回到最上面」；
 * - 剧集还没取到时铺骨架屏（见 [EpisodeSkeleton]），而不是先亮一句「暂无选集」再翻成列表。
 */
@Composable
internal fun EpisodeSection(
    episodes: List<EpisodeItem>,
    selectedEpisodeId: String?,
    order: EpisodeOrder,
    loading: Boolean,
    gridState: LazyGridState,
    onOrderToggle: () -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只有「剧集还没到」才用骨架屏。
    //
    // `loading` 同时覆盖探测、取剧集、出流三个阶段：换集/换源时它也会为 true，
    // 但那时剧集已经在手上。用 `episodes.isEmpty()` 把后两种排除掉，
    // 否则用户点一下下一集，整块选集会先塌成骨架屏再长回来。
    val skeleton = loading && episodes.isEmpty()

    // 列宽与卡片尺寸只在这里算一次，排序按钮 / 卡片 / 骨架屏共用同一组数：
    // 三处各算一次的话，少乘一个间距就会让排序按钮比卡片宽出 12dp。
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = episodeColumns(maxWidth)
        val cardWidth =
            (maxWidth - EpisodeGridPadding * 2 - EpisodeGridSpacing * (columns - 1)) / columns
        val cardHeight = cardWidth / EpisodeCardAspectRatio

        Column(modifier = Modifier.fillMaxSize()) {
            EpisodeHeader(
                episodeCount = episodes.size,
                order = order,
                loading = skeleton,
                onOrderToggle = onOrderToggle,
            )

            if (skeleton) {
                EpisodeSkeleton(columns = columns, cardHeight = cardHeight)
                return@Column
            }

            if (episodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.player_episodes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            // 「展开」是这一块自己的视图状态：它只决定网格摆多少张卡，与业务无关，
            // 进 UiState 只会让换集也去动它。用 rememberSaveable 是为了转屏/进出全屏后还在。
            var expanded by rememberSaveable { mutableStateOf(false) }
            val collapsible = episodes.size > EPISODE_PREVIEW_COUNT
            val shownEpisodes = if (collapsible && !expanded) {
                episodes.take(EPISODE_PREVIEW_COUNT)
            } else {
                episodes
            }

            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val scope = rememberCoroutineScope()

            // 首次进入把当前集滚进视野；网格已经有滚动位置（从评论 Tab 切回来、
            // 或用户自己滚过）就尊重它，不要再拉回去。
            LaunchedEffect(episodes, selectedEpisodeId) {
                val index = episodes.indexOfFirst { it.id == selectedEpisodeId }
                // 当前集落在预览窗口之外：先展开，否则用户翻回来根本看不到自己在看哪一集。
                if (collapsible && index >= EPISODE_PREVIEW_COUNT) {
                    expanded = true
                    // 等一帧让网格按新的列表重新测量，否则这一项还不存在，滚动会落到末尾。
                    withFrameNanos { }
                }
                val atTop = gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0
                if (!atTop) return@LaunchedEffect
                if (index > 0) gridState.scrollToItem(index)
            }

            // 能不能往上滚：只有滚下去了「回到最上面」才有意义。
            val canScrollBackward by remember(gridState) {
                derivedStateOf { gridState.canScrollBackward }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = EpisodeGridPadding,
                        end = EpisodeGridPadding,
                        top = 2.dp,
                        // 只在底部避让：按钮浮在上面，不占列宽。
                        bottom = bottomInset + EpisodeRailClearance,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(EpisodeGridSpacing),
                    verticalArrangement = Arrangement.spacedBy(EpisodeGridSpacing),
                ) {
                    items(
                        items = shownEpisodes,
                        key = { it.id },
                        contentType = { "episode" },
                    ) { episode ->
                        EpisodeCard(
                            episode = episode,
                            selected = episode.id == selectedEpisodeId,
                            height = cardHeight,
                            onClick = { onEpisodeClick(episode) },
                            // 切换正序/倒序时，卡片按 key 平滑移动到新位置，而不是整块闪一下。
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                EpisodeRail(
                    // 上箭头恒在列里，但只有真的能往上滚时才显示：三集的番不该出现「回到顶部」。
                    showBackToTop = canScrollBackward,
                    expandable = collapsible,
                    expanded = expanded,
                    onBackToTop = { scope.launch { gridState.animateScrollToItem(0) } },
                    onToggleExpand = { expanded = !expanded },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 24.dp),
                )
            }
        }
    }
}

/**
 * 一屏摆几列。
 *
 * 算法与 [GridCells.Adaptive] 一致——`(可用宽 + 间距) / (最小宽 + 间距)` 向下取整——
 * 只是把间距换成 12，并保证至少 1 列。360dp 竖屏正好 3 列，和 Dart-Mikomi 视频页一致；
 * 平板再多给几列，卡片按同一宽高比自己变，不会被拉成又扁又长的一条。
 */
private fun episodeColumns(availableWidth: Dp): Int {
    val usable = availableWidth - EpisodeGridPadding * 2 + EpisodeGridSpacing
    return (usable / (EpisodeMinWidth + EpisodeGridSpacing)).toInt().coerceAtLeast(1)
}

/**
 * 右下角悬浮按钮列，从上到下：回到最上面、展开/收起全部剧集。
 *
 * 版式照抄详情页的「收藏 + 追番」按钮组（[feature:detail] 的 `BottomActions`）：
 * 右下角对齐、导航栏内边距、右 20 下 24、间距 12、圆形按钮 48dp。
 * 关键是**浮在内容之上**而不是挤占内容——靠 `padding` 把网格压窄的那套做法已经删掉。
 *
 * 为什么放在右下角而不是右侧居中：居中会正好压住中间那几行的卡片，
 * 而右下角在任何列表长度下都只可能压到最后一行，且滚到底时会因为底部留白落空。
 */
@Composable
private fun EpisodeRail(
    showBackToTop: Boolean,
    expandable: Boolean,
    expanded: Boolean,
    onBackToTop: () -> Unit,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        EpisodeRailButton(
            icon = Icons.Rounded.ArrowUpward,
            contentDescription = stringResource(R.string.player_episodes_back_to_top),
            visible = showBackToTop,
            primary = false,
            onClick = onBackToTop,
        )
        if (expandable) {
            EpisodeRailButton(
                icon = if (expanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                contentDescription = stringResource(
                    if (expanded) R.string.player_episodes_collapse else R.string.player_episodes_expand,
                ),
                visible = true,
                // 这一列的主操作：展开/收起是用户真正会反复点的那个，给它主色容器。
                primary = true,
                onClick = onToggleExpand,
            )
        }
    }
}

/**
 * 一个悬浮圆钮。
 *
 * 视觉与详情页的收藏按钮一致：`surfaceContainerHighest` 底 + `onSurfaceVariant` 图标，
 * 主操作（展开/收起）换成 `primaryContainer`，让「哪个是次要的」不必靠位置去猜。
 *
 * [visible] 为 false 时只做淡出、**保留占位**：列是底对齐的，占位不会让下面的按钮跳动，
 * 但保留它可以让淡入淡出走完，而不是硬闪一下。
 */
@Composable
private fun EpisodeRailButton(
    icon: ImageVector,
    contentDescription: String,
    visible: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "episodeRailButtonOpacity",
    )
    Surface(
        onClick = onClick,
        enabled = visible,
        shape = CircleShape,
        color = if (primary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (primary) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .size(EpisodeRailButtonSize)
            .alpha(opacity),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 排序头：左侧排序按钮，右侧「共 N 集」。
 *
 * 两个胶囊**同一套规格**：同高（[EpisodeChipHeight]）、同水平内边距、同字号、同形状，
 * 所以看起来是一样大的一样长的两块，而不是「一个大一个小」。
 */
@Composable
private fun EpisodeHeader(
    episodeCount: Int,
    order: EpisodeOrder,
    loading: Boolean,
    onOrderToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = EpisodeGridPadding,
                end = EpisodeGridPadding,
                top = 10.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            // 加载中连排序按钮一起做成骨架：这一排要么全是数据，要么全没有。
            // 留一个能点的排序钮在前面的坏处很具体——点了没有任何可见反馈（列表还没来）。
            //
            // 两个骨架块与真实胶囊**同宽同高**（[EpisodeChipMinWidth] × [EpisodeChipHeight]）：
            // 数据到位时只是底色变成文字，位置和尺寸一点不动，不会出现「骨架两个小方块 → 真按钮两个大胶囊」。
            SkeletonBox(
                modifier = Modifier.size(width = EpisodeChipMinWidth, height = EpisodeChipHeight),
                shape = CircleShape,
            )
            Spacer(modifier = Modifier.weight(1f))
            SkeletonBox(
                modifier = Modifier.size(width = EpisodeChipMinWidth, height = EpisodeChipHeight),
                shape = CircleShape,
            )
        } else {
            SortButton(order = order, onClick = onOrderToggle)
            Spacer(modifier = Modifier.weight(1f))
            EpisodeCountChip(count = episodeCount)
        }
    }
}

/**
 * 选集骨架屏：与真实网格**同一套列宽、间距、内边距**，只把卡片换成扫光块。
 *
 * 尺寸对齐是刻意的：数据到位时只是块的底色变成卡片内容，行高、列数、滚动范围都不变，
 * 所以不会出现「骨架屏 4 行 → 真列表 5 行」那种跳动。
 *
 * 骨架块数量取 [EPISODE_PREVIEW_COUNT]：与默认预览的集数一致，铺满首屏即止。
 * `userScrollEnabled = false`——底下没有任何内容，能拖动只会滚出一片空白。
 */
@Composable
private fun EpisodeSkeleton(
    columns: Int,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(
            start = EpisodeGridPadding,
            end = EpisodeGridPadding,
            top = 2.dp,
            bottom = bottomInset + EpisodeRailClearance,
        ),
        horizontalArrangement = Arrangement.spacedBy(EpisodeGridSpacing),
        verticalArrangement = Arrangement.spacedBy(EpisodeGridSpacing),
    ) {
        items(
            count = EPISODE_PREVIEW_COUNT,
            key = { index -> "episodeSkeleton$index" },
            contentType = { "episodeSkeleton" },
        ) {
            SkeletonBox(
                modifier = Modifier.height(cardHeight),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

/**
 * 总集数胶囊：「共 N 集」整句仍然来自 `strings.xml`，只有数字那一段单独上主题色。
 *
 * 之所以用 [buildAnnotatedString] 在整句里定位数字，而不是拆成「共 / N / 集」三个 Text：
 * 语序在别的语言里会变（"12 eps" 数字在前），按整句渲染才不会因为翻译而错位，
 * 且数字的字重、颜色只在 spans 里声明一次。数字颜色取 `primary`，日/夜自动跟随主题。
 */
@Composable
private fun EpisodeCountChip(count: Int) {
    val label = stringResource(R.string.player_episode_count, count)
    val numberColor = MaterialTheme.colorScheme.primary
    val text = remember(label, numberColor) {
        buildAnnotatedString {
            append(label)
            val range = label.digitRange() ?: return@buildAnnotatedString
            addStyle(
                style = SpanStyle(color = numberColor, fontWeight = FontWeight.Bold),
                start = range.first,
                end = range.last + 1,
            )
        }
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .height(EpisodeChipHeight)
            .widthIn(min = EpisodeChipMinWidth),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** 找出字符串里第一段连续数字的下标区间，没有则返回 null。 */
private fun String.digitRange(): IntRange? {
    val start = indexOfFirst { it.isDigit() }
    if (start < 0) return null
    var end = start
    while (end < length && this[end].isDigit()) end++
    return start until end
}

/**
 * 排序方向按钮：与「共 N 集」**同规格**的胶囊（同高、同内边距、同字号）。
 *
 * 只有正序/倒序两态，点一下即切；箭头旋转 180° 让「方向变了」这件事在按下瞬间可见，
 * 不用读文字也能确认。
 */
@Composable
private fun SortButton(
    order: EpisodeOrder,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (order == EpisodeOrder.ASCENDING) 0f else 180f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "episodeSortArrow",
    )
    val isAscending = order == EpisodeOrder.ASCENDING

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .height(EpisodeChipHeight)
            .widthIn(min = EpisodeChipMinWidth),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(rotation),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(
                    if (isAscending) R.string.player_order_asc else R.string.player_order_desc,
                ),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/**
 * 单张选集卡片。
 *
 * 高度由网格列宽除以宽高比算出来（[EpisodeCardAspectRatio]），不是写死的最小高度：
 * 列宽一变，卡片跟着变，一行两行文字都始终在卡片里居中。
 *
 * 小标题用 `ScrollingText`：**只有选中卡片开启滚动**。
 * 网格里同时跑十几条 marquee 会让每帧都有十几次文本 layout + draw，
 * 正是首页列表掉帧的同一类问题；未选中的卡片退化为静态省略，视觉信息不丢。
 */
@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    selected: Boolean,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            // 日间是「浅灰底 + 白卡」、夜间是「纯黑底 + #181818 卡」，两种模式下都比背景亮一档。
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 150),
        label = "episodeCardContainer",
    )
    val titleColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 150),
        label = "episodeCardTitle",
    )

    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.height(height),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.player_episode_title, episode.number),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = episode.subtitle
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                ScrollingText(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    enabled = selected,
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
