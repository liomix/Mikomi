package com.liomix.mikomi.feature.player.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.ui.component.ScrollingText
import com.liomix.mikomi.feature.player.EpisodeItem
import com.liomix.mikomi.feature.player.EpisodeOrder
import com.liomix.mikomi.feature.player.R

/**
 * 卡片最小宽度：360dp 竖屏下正好 3 列（与参考图一致），
 * 更宽的窗口自动增列，平板上不会被拉成巨型卡片。
 */
private val EpisodeMinWidth = 96.dp

/** 卡片最小高度（两行文字 + 上下内边距）。 */
private val EpisodeCardMinHeight = 66.dp

/**
 * 总集数胶囊高度：比排序胶囊（36dp）低一档。
 * 它是纯展示的次要信息，和可点的排序按钮在视觉权重上要能分开。
 */
private val EpisodeCountChipHeight = 32.dp

/**
 * 选集区：排序头 + 自适应网格。
 *
 * - 列数用 [GridCells.Adaptive]，横向多宽就摆多少列，不把「手机竖屏 3 列」写死；
 * - 网格自己滚动，播放器与 Tab 栏固定不动；
 * - 进入页面时把当前集滚到可见位置（只做一次，之后用户滚动不再被拉回）。
 */
@Composable
internal fun EpisodeSection(
    episodes: List<EpisodeItem>,
    selectedEpisodeId: String?,
    order: EpisodeOrder,
    gridState: LazyGridState,
    onOrderToggle: () -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EpisodeHeader(
            episodeCount = episodes.size,
            order = order,
            onOrderToggle = onOrderToggle,
        )

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

        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // 首次进入把当前集滚进视野；网格已经有滚动位置（从评论 Tab 切回来、
        // 或用户自己滚过）就尊重它，不要再拉回去。
        LaunchedEffect(episodes, selectedEpisodeId) {
            if (episodes.isEmpty()) return@LaunchedEffect
            val atTop = gridState.firstVisibleItemIndex == 0 &&
                gridState.firstVisibleItemScrollOffset == 0
            if (!atTop) return@LaunchedEffect
            val index = episodes.indexOfFirst { it.id == selectedEpisodeId }
            if (index > 0) gridState.scrollToItem(index)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = EpisodeMinWidth),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 2.dp,
                bottom = bottomInset + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = episodes,
                key = { it.id },
                contentType = { "episode" },
            ) { episode ->
                EpisodeCard(
                    episode = episode,
                    selected = episode.id == selectedEpisodeId,
                    onClick = { onEpisodeClick(episode) },
                    // 切换正序/倒序时，卡片按 key 平滑移动到新位置，而不是整块闪一下。
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** 排序头：左侧排序方向按钮，右侧总集数胶囊。 */
@Composable
private fun EpisodeHeader(
    episodeCount: Int,
    order: EpisodeOrder,
    onOrderToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortButton(order = order, onClick = onOrderToggle)
        Spacer(modifier = Modifier.weight(1f))
        EpisodeCountChip(count = episodeCount)
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
        modifier = Modifier.height(EpisodeCountChipHeight),
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
 * 排序方向按钮：胶囊 + 会翻转的箭头。
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
        modifier = Modifier.height(36.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(15.dp)
                    .rotate(rotation),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = stringResource(
                    if (isAscending) R.string.player_order_asc else R.string.player_order_desc,
                ),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

/**
 * 单张选集卡片。
 *
 * 小标题用 `ScrollingText`：**只有选中卡片开启滚动**。
 * 网格里同时跑十几条 marquee 会让每帧都有十几次文本 layout + draw，
 * 正是首页列表掉帧的同一类问题；未选中的卡片退化为静态省略，视觉信息不丢。
 */
@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    selected: Boolean,
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
        modifier = modifier.heightIn(min = EpisodeCardMinHeight),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.player_episode_title, episode.number),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = episode.subtitle
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
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
