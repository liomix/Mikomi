package com.liomix.mikomi.feature.player.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.liomix.mikomi.feature.player.PlayerLine
import com.liomix.mikomi.feature.player.PlayerSource
import com.liomix.mikomi.feature.player.R
import com.liomix.mikomi.feature.player.SourceAvailability

/** 换源弹窗内容区的固定高度。线路多少都这么高，避免高度随数据跳动。 */
private val SheetHeight = 360.dp

/** 小屏兜底比例：窗口装不下 [SheetHeight] 时按窗口高度收缩，别把弹窗顶到屏幕最上沿。 */
private const val SHEET_MAX_HEIGHT_RATIO = 0.78f

/** 线路行状态位的容器宽度：给圆点、转圈、勾留同一个对位基准。 */
private val StatusSlotSize = 16.dp

/** 线路行高度：48dp 是「一眼看清 + 手指好点」的平衡点。 */
private val LineRowHeight = 48.dp

/** 行尾小图标的触控盒：与可见图标一致，不额外外扩。 */
private val RowActionSize = 32.dp
private val RowActionIconSize = 18.dp

/** Tab 上的状态点：比线路行里的点小一档，它只用来扫一眼「这个源有没有东西」。 */
private val TabStatusDotSize = 7.dp
private val TabStatusSpinnerSize = 12.dp

/** Tab 名称上限：自建源一类名字可能带后缀，别让单个 Tab 占掉半个屏幕。 */
private val TabLabelMaxWidth = 132.dp

/**
 * 换源弹窗。
 *
 * 布局是 **大源 Tab + 子线路列表**：
 * 顶部一行 Tab 是「大源」（插件源、AniCyk、AniRpb…），下面列出当前 Tab 的子线路
 * （插件源下面是 DM84 / 樱花）。一个源里挂多条线路是常态，但用户第一眼要先分清
 * 「哪个源能用」，再决定「用它的哪条线路」，两层不该在同一张平面上平铺。
 *
 * 两个关键语义：
 * - **点 Tab 只切视图，不切播放源**。切源必须由「点某条线路」明确表达 ——
 *   翻看一下别的源就把当前播放干掉，是这里最不能接受的误操作。
 * - 弹窗**只是当前列表的一个视图**，不发起检测、不持有结果：数据来自
 *   `PlayerUiState.sources`（后续由 `PlaybackCatalog` 供给），关掉再打开不会丢状态，
 *   也不会因为弹窗重建而重跑一遍检测。
 *
 * 用 `Column` 而不是 `LazyColumn`：单个源的线路是个位数，
 * 一次性组合更简单，也避免和 sheet 自身的 nested scroll 打架。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSourceSheet(
    animeTitle: String,
    episodeLabel: String,
    sources: List<PlayerSource>,
    selectedSourceId: String?,
    onLineSelected: (sourceId: String, lineId: String) -> Unit,
    onRetrySource: (sourceId: String) -> Unit,
    onRetryLine: (sourceId: String, lineId: String) -> Unit,
    onRetryAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 当前浏览的 Tab 是弹窗内的局部交互：关掉就回到「正在播放的源」，不必进 UiState。
    var browsedSourceId by rememberSaveable { mutableStateOf<String?>(null) }

    // 浏览目标失效（源还没到 / 被移除）时回落到正在播放的源，再回落到第一个。
    val activeSourceId = browsedSourceId?.takeIf { id -> sources.any { it.id == id } }
        ?: selectedSourceId?.takeIf { id -> sources.any { it.id == id } }
        ?: sources.firstOrNull()?.id
    val activeSource = sources.firstOrNull { it.id == activeSourceId }
    val activeIndex = sources.indexOfFirst { it.id == activeSourceId }.coerceAtLeast(0)
    val tabListState = rememberLazyListState()

    // 被切到的 Tab 不在可见范围时才滚一下；已经看得见就不动，避免每次点击都「跳到最左」。
    LaunchedEffect(activeIndex, tabListState) {
        val visible = tabListState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }
        if (!visible) tabListState.animateScrollToItem(activeIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 与页面底色同族：夜间 surface 是 #181818，弹窗比它亮一档才能看出层次。
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // 弹窗高度**固定**：换源时高度跟着线路数量跳，手指刚移过去目标就跑了。
        // 线路多到装不下时，让线路区自己滚，而不是把弹窗撑高。
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val contentHeight = min(SheetHeight, maxHeight * SHEET_MAX_HEIGHT_RATIO)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                SheetHeader(
                    animeTitle = animeTitle,
                    episodeLabel = episodeLabel,
                    canRetry = sources.any { it.isRetryable },
                    onRetryClick = onRetryAll,
                )

                if (sources.isEmpty()) {
                    Text(
                        text = stringResource(R.string.player_source_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                    return@Column
                }

                SourceTabRow(
                    sources = sources,
                    activeSourceId = activeSourceId,
                    listState = tabListState,
                    onSelect = { browsedSourceId = it },
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                // Tab 行与标题固定，只有线路区滚动：切 Tab 时不会连 Tab 一起滑走。
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (activeSource != null) {
                        SourceTabContent(
                            source = activeSource,
                            playing = activeSource.id == selectedSourceId,
                            onLineSelected = { lineId -> onLineSelected(activeSource.id, lineId) },
                            onRetrySource = { onRetrySource(activeSource.id) },
                            onRetryLine = { lineId -> onRetryLine(activeSource.id, lineId) },
                        )
                    }
                }
            }
        }
    }
}

/** 标题行：左侧「弹窗在给哪一集换源」，右侧一个全部重试入口。 */
@Composable
private fun SheetHeader(
    animeTitle: String,
    episodeLabel: String,
    canRetry: Boolean,
    onRetryClick: () -> Unit,
) {
    val subtitle = if (episodeLabel.isBlank()) {
        animeTitle
    } else {
        stringResource(R.string.player_source_sheet_subtitle, animeTitle, episodeLabel)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.player_source_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 没有可重试的源时按钮不出现，避免点了没反应的死按钮。
        // 单个源的重试在 Tab 内容顶部，这里只负责「一次全试一遍」。
        if (canRetry) {
            TextButton(onClick = onRetryClick) {
                Text(text = stringResource(R.string.player_source_retry_all))
            }
        }
    }
}

/**
 * 大源 Tab 行。
 *
 * 横向可滚：源数量不可控（插件装得多的时候十几个），等分会把名字全挤成省略号。
 * 顺序**不按可用性排**：Tab 一跳动，用户刚记住的位置就没了；
 * 「哪个源有资源」由 Tab 上的状态点回答。
 */
@Composable
private fun SourceTabRow(
    sources: List<PlayerSource>,
    activeSourceId: String?,
    listState: LazyListState,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = sources, key = { it.id }) { source ->
            SourceTab(
                source = source,
                selected = source.id == activeSourceId,
                onClick = { onSelect(source.id) },
            )
        }
    }
}

/** 单个大源 Tab：状态点 + 源名。选中是主色浅底胶囊，不选中的只留文字。 */
@Composable
private fun SourceTab(
    source: PlayerSource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 150),
        label = "sourceTabContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 150),
        label = "sourceTabContent",
    )

    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            // 触控区就是看到的胶囊，不额外外扩。
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            availability = source.availability,
            dotSize = TabStatusDotSize,
            spinnerSize = TabStatusSpinnerSize,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = source.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = TabLabelMaxWidth),
        )
    }
}

/**
 * 某个大源下的内容：一行状态说明 + 它的子线路列表。
 *
 * 状态说明放在这里而不是 Tab 上：Tab 只能放下一个点，
 * 「为什么现在不能用」必须有一句话，否则用户只看到一个红点不知道点重试。
 */
@Composable
private fun SourceTabContent(
    source: PlayerSource,
    /** 这个源是不是**正在播放**的那个（不是「正在浏览」）。 */
    playing: Boolean,
    onLineSelected: (String) -> Unit,
    onRetrySource: () -> Unit,
    onRetryLine: (String) -> Unit,
) {
    val lines = remember(source.lines) { source.sortedLines() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
    ) {
        SourceStatusRow(
            source = source,
            playing = playing,
            onRetry = onRetrySource,
        )

        lines.forEach { line ->
            LineRow(
                line = line,
                playing = playing && source.selectedLineId == line.id,
                onSelect = { onLineSelected(line.id) },
                onRetry = { onRetryLine(line.id) },
            )
        }
    }
}

/** 状态说明行：左侧一句话解释当前 Tab 的情况，右侧是本源的重新检测入口。 */
@Composable
private fun SourceStatusRow(
    source: PlayerSource,
    playing: Boolean,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = source.statusText(playing = playing),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (source.isRetryable) {
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.player_source_retry_this),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * 线路行。
 *
 * 点一下就切过去（切完弹窗关闭）——线路是这个弹窗的最小可执行单位。
 * 正在播放的那条给主色底 + 勾，其余可用的给灰点，
 * 不可用的置灰且不可点：点一条查不到资源的线路没有任何合理结果。
 */
@Composable
private fun LineRow(
    line: PlayerLine,
    playing: Boolean,
    onSelect: () -> Unit,
    onRetry: () -> Unit,
) {
    val enabled = line.availability.selectable
    val containerColor = if (playing) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LineRowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onSelect)
            .alpha(if (enabled || playing) 1f else 0.55f)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(StatusSlotSize),
            contentAlignment = Alignment.Center,
        ) {
            when {
                playing -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.player_source_playing),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )

                else -> StatusDot(
                    availability = line.availability,
                    dotSize = 8.dp,
                    spinnerSize = 14.dp,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = line.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // fill = false：线路名短的时候清晰度标签要紧跟着，不被推到最右。
                modifier = Modifier.weight(1f, fill = false),
            )
            val quality = line.quality
            if (quality != null) {
                Spacer(modifier = Modifier.width(6.dp))
                QualityTag(quality = quality)
            }
        }
        if (line.isRetryable) {
            RowAction(
                icon = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.player_source_retry_item, line.name),
                onClick = onRetry,
            )
        }
    }
}

/** 行尾小图标按钮：触控盒与可见图标同尺寸，不额外外扩。 */
@Composable
private fun RowAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(RowActionSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(RowActionIconSize),
        )
    }
}

/**
 * 状态说明文案。
 *
 * 「当前线路」只属于正在播放的那个源：每个源各自记着自己的 `selectedLineId`，
 * 不分场合都写「当前线路」，用户会以为好几个源同时在播。
 *
 * 非播放中的源要补一句「点线路切换」——Tab 只是用来翻看的，不告诉用户这一点，
 * 会有人点完 Tab 发现还在放原来的线路，以为坏了。
 */
@Composable
private fun PlayerSource.statusText(playing: Boolean): String = when (availability) {
    SourceAvailability.READY -> if (playing) {
        currentLine?.let { stringResource(R.string.player_source_current_line, it.name) }
            ?: stringResource(R.string.player_source_lines, lines.size)
    } else {
        stringResource(R.string.player_source_lines_pickable, lines.size)
    }

    SourceAvailability.CHECKING -> stringResource(R.string.player_source_checking)
    SourceAvailability.NO_RESULT -> stringResource(R.string.player_source_no_result)
    SourceAvailability.FAILED -> stringResource(R.string.player_source_failed)
}

/** 清晰度小标：只在线路上报了清晰度时出现。 */
@Composable
private fun QualityTag(quality: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = quality,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 状态圆点：就绪=实心、检测中=转圈、无结果=空心、失败=错误色空心。
 *
 * 不用 Tint 传递颜色：这四种状态的颜色本身携带语义（错误必须是 error），
 * 跟着 Tab 的选中色走就会把「失败」画成主色，等于抹掉了这条信息。
 */
@Composable
private fun StatusDot(
    availability: SourceAvailability,
    dotSize: Dp,
    spinnerSize: Dp,
) {
    when (availability) {
        SourceAvailability.READY -> Box(
            modifier = Modifier
                .size(dotSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )

        SourceAvailability.CHECKING -> CircularProgressIndicator(
            modifier = Modifier.size(spinnerSize),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 1.5.dp,
        )

        SourceAvailability.NO_RESULT -> Box(
            modifier = Modifier
                .size(dotSize)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )

        SourceAvailability.FAILED -> Box(
            modifier = Modifier
                .size(dotSize)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    shape = CircleShape,
                ),
        )
    }
}
