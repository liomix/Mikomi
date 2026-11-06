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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiPillShape
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiStroke
import com.liomix.mikomi.core.ui.component.base.MikomiIcon
import com.liomix.mikomi.core.ui.component.base.Tag
import com.liomix.mikomi.core.ui.component.base.TagColors
import com.liomix.mikomi.core.ui.component.base.TagStyle
import com.liomix.mikomi.data.video.VdoSourceFamily
import com.liomix.mikomi.feature.player.PlaybackMode
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

/** Tab 上的状态点：比线路行里的点小一档，它只用来扫一眼「这个大源有没有东西」。 */
private val TabStatusDotSize = 7.dp
private val TabStatusSpinnerSize = 12.dp

/** Tab 名称上限：大源的代号不长，留个上限防止以后有人往里塞长名字。 */
private val TabLabelMaxWidth = 132.dp

/**
 * 一条可点的行：**某个源的一条线路**，或**某个源自己**（它还没有线路时）。
 *
 * [line] 为 null 表示这个源线路还没出来（检测中 / 查不到 / 请求失败），
 * 这一行就不提供「切换」，只说明状态并给一个重试入口。
 */
private data class LineEntry(
    val source: PlayerSource,
    val line: PlayerLine? = null,
)

/**
 * 换源弹窗。
 *
 * 布局是 **大源 Tab + 源/线路列表**：
 * 顶部一行 Tab 是**大源**（`VdoPlg`、`VdoCyk`、`VdoRpb`…），
 * 下面列出当前大源里所有能用的东西——一个源一条线路就是一行，多条线路就一行一条。
 *
 * 为什么 Tab 放「大源」而不是「源」：装十几个插件时，Tab 会变成十几个名字挤在一起，
 * 用户第一眼关心的其实是「这套实现有没有片」（VdoPlg 有没有、VdoCyk 有没有），
 * 具体是 DM84 还是 7sefun 是第二层的事。
 *
 * 行的名字由 [PlayerSource.lineLabel] 决定：单线路只写 `DM84`，多线路写 `DM84 - 线路2`。
 * 所以源名不会被重复成两行，也不会出现「DM84 - 线路1」这种对一个单线路源的废话。
 *
 * 两个关键语义：
 * - **点 Tab 只切视图，不切播放源**。切源必须由「点某一行」明确表达 ——
 *   翻看一下别的大源就把当前播放干掉，是这里最不能接受的误操作。
 * - 弹窗**只是当前列表的一个视图**，不发起检测、不持有结果：数据来自
 *   `PlayerUiState.sources`，关掉再打开不会丢状态，
 *   也不会因为弹窗重建而重跑一遍检测。
 *
 * 用 `Column` 而不是 `LazyColumn`：单个大源下的行是个位数，
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

    // 按大源分组。顺序取枚举声明顺序而不是插入顺序：Tab 的位置不该随探测谁先返回而变。
    // 只给「真的有源」的大源建 Tab：CYK / RPB 尚未接入、没有任何 PlayerSource 产出，
    // 不过滤就会先出现两个点不进去的空 Tab。接入后这里一行都不用改。
    val grouped = remember(sources) { sources.groupBy { it.family } }
    val families = remember(grouped) { VdoSourceFamily.entries.filter { grouped.containsKey(it) } }

    // 当前浏览的 Tab 是弹窗内的局部交互：关掉就回到「正在播放的那个大源」，不必进 UiState。
    // 存 name 而不是枚举本体：saveable 只认基本类型，存枚举要靠隐式 Serializable，没必要赌。
    var browsedFamilyName by rememberSaveable { mutableStateOf<String?>(null) }

    val playingFamily = sources.firstOrNull { it.id == selectedSourceId }?.family
    // 浏览目标失效（大源没了 / 名字对不上）时回落到正在播的那个，再回落到第一个。
    val activeFamily = browsedFamilyName
        ?.let { name -> families.firstOrNull { it.name == name } }
        ?: playingFamily?.takeIf { families.contains(it) }
        ?: families.firstOrNull()
    val activeIndex = families.indexOf(activeFamily).coerceAtLeast(0)
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
                    .padding(bottom = MikomiSpacing.Sm),
            ) {
                SheetHeader(
                    animeTitle = animeTitle,
                    episodeLabel = episodeLabel,
                    canRetry = sources.any { it.isRetryable },
                    onRetryClick = onRetryAll,
                )

                if (families.isEmpty()) {
                    Text(
                        text = stringResource(R.string.player_source_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MikomiSpacing.Room, vertical = MikomiSpacing.Xl),
                    )
                    return@Column
                }

                FamilyTabRow(
                    families = families,
                    grouped = grouped,
                    activeFamily = activeFamily,
                    listState = tabListState,
                    onSelect = { browsedFamilyName = it.name },
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
                    FamilyTabContent(
                        sources = activeFamily?.let { grouped[it] }.orEmpty(),
                        playingSourceId = selectedSourceId,
                        onLineSelected = onLineSelected,
                        onRetrySource = onRetrySource,
                        onRetryLine = onRetryLine,
                    )
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
            .padding(start = MikomiSpacing.Room, end = MikomiSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.player_source_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(MikomiSpacing.Xxs))
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
        // 单个源的重试在它自己那一行上，这里只负责「一次全试一遍」。
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
 * 横向可滚：大源个数未来会涨（VdoPlg / VdoCyk / VdoRpb / …），等分会把名字全挤成省略号。
 * 顺序**不按可用性排**：Tab 一跳动，用户刚记住的位置就没了；
 * 「这个大源有没有东西」由 Tab 上的状态点回答。
 */
@Composable
private fun FamilyTabRow(
    families: List<VdoSourceFamily>,
    grouped: Map<VdoSourceFamily, List<PlayerSource>>,
    activeFamily: VdoSourceFamily?,
    listState: LazyListState,
    onSelect: (VdoSourceFamily) -> Unit,
) {
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = MikomiSpacing.Lg, vertical = MikomiSpacing.Xxs),
        horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm),
    ) {
        items(items = families, key = { it.name }) { family ->
            FamilyTab(
                label = family.label,
                // 大源的状态取组内最好的那个：只要有一个源能用，这个 Tab 就算有东西。
                availability = grouped[family].orEmpty().bestAvailability(),
                selected = family == activeFamily,
                onClick = { onSelect(family) },
            )
        }
    }
}

/** 组内最优状态：枚举顺序就是「从好到坏」，取最小的那个即可。 */
private fun List<PlayerSource>.bestAvailability(): SourceAvailability =
    minByOrNull { it.availability.ordinal }?.availability ?: SourceAvailability.NO_RESULT

/** 单个大源 Tab：状态点 + 代号。选中是主色浅底胶囊，不选中的只留文字。 */
@Composable
private fun FamilyTab(
    label: String,
    availability: SourceAvailability,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = MikomiMotion.Duration.Fast),
        label = "familyTabContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = MikomiMotion.Duration.Fast),
        label = "familyTabContent",
    )

    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(MikomiPillShape)
            .background(containerColor)
            // 触控区就是看到的胶囊，不额外外扩。
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = MikomiSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            availability = availability,
            dotSize = TabStatusDotSize,
            spinnerSize = TabStatusSpinnerSize,
        )
        Spacer(modifier = Modifier.width(MikomiSpacing.Tight))
        Text(
            text = label,
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
 * 大源下的内容：**所有源的线路铺成一张扁平列表**。
 *
 * 不做「源 → 线路」的两级缩进：一个源只要一条线路（绝大多数情况），
 * 两级结构会多出一行只有源名的分组标题，而它和它下面那行说的是同一件事。
 * 名字由 [PlayerSource.lineLabel] 一次拼好，源与线路的关系在文字里就是清楚的。
 */
@Composable
private fun FamilyTabContent(
    sources: List<PlayerSource>,
    playingSourceId: String?,
    onLineSelected: (sourceId: String, lineId: String) -> Unit,
    onRetrySource: (sourceId: String) -> Unit,
    onRetryLine: (sourceId: String, lineId: String) -> Unit,
) {
    val entries = remember(sources) { sources.toLineEntries() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MikomiSpacing.Md, end = MikomiSpacing.Md, top = MikomiSpacing.Xxs, bottom = MikomiSpacing.Sm),
    ) {
        entries.forEach { entry ->
            val line = entry.line
            EntryRow(
                entry = entry,
                playing = line != null &&
                    entry.source.id == playingSourceId &&
                    entry.source.selectedLineId == line.id,
                onSelect = { line?.let { onLineSelected(entry.source.id, it.id) } },
                onRetry = {
                    if (line == null) {
                        onRetrySource(entry.source.id)
                    } else {
                        onRetryLine(entry.source.id, line.id)
                    }
                },
            )
        }
    }
}

/**
 * 源 → 行的展开规则。
 *
 * - 有线路：一条线路一行；
 * - 没线路且不是「已就绪」：给一行源级状态，否则用户会以为这个源根本不存在（它是查挂了，不是没装）；
 * - 已就绪但线路还没到：**什么都不画**，等它来——这时画一行「0 条线路」纯属添乱。
 */
private fun List<PlayerSource>.toLineEntries(): List<LineEntry> = flatMap { source ->
    val lines = source.sortedLines()
    when {
        lines.isNotEmpty() -> lines.map { LineEntry(source = source, line = it) }
        source.availability != SourceAvailability.READY -> listOf(LineEntry(source = source))
        else -> emptyList()
    }
}

/**
 * 一行：**某条线路**，或**某个源自己的状态**（[LineEntry.line] 为 null）。
 *
 * 点一下就切过去（切完弹窗关闭）——线路是这个弹窗的最小可执行单位。
 * 正在播放的那条给主色底 + 勾，其余可用的给灰点，
 * 不可用的置灰且不可点：点一条查不到资源的线路没有任何合理结果。
 */
@Composable
private fun EntryRow(
    entry: LineEntry,
    playing: Boolean,
    onSelect: () -> Unit,
    onRetry: () -> Unit,
) {
    val line = entry.line
    val availability = line?.availability ?: entry.source.availability
    // 源级行没有「切换」这回事：它连线路都还没拿到。
    val enabled = line != null && availability.selectable
    val containerColor = if (playing) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LineRowHeight)
            .clip(RoundedCornerShape(MikomiRadius.Md))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onSelect)
            .alpha(if (enabled || playing) 1f else 0.55f)
            .padding(start = MikomiSpacing.Md, end = MikomiSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(StatusSlotSize),
            contentAlignment = Alignment.Center,
        ) {
            when {
                playing -> MikomiIcon(
                    icon = MikomiIcons.Check,
                    contentDescription = stringResource(R.string.player_source_playing),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(MikomiSizes.IconXs),
                )

                else -> StatusDot(
                    availability = availability,
                    dotSize = 8.dp,
                    spinnerSize = 14.dp,
                )
            }
        }
        Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = line?.let { entry.source.lineLabel(it) } ?: entry.source.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // fill = false：线路名短的时候标签要紧跟着，不被推到最右。
                modifier = Modifier.weight(1f, fill = false),
            )
            // 源级行没有线路可标，用一句状态说明代替那两种小标。
            if (line == null) {
                val statusText = entry.source.statusText()
                if (statusText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                val quality = line.quality
                if (quality != null) {
                    Spacer(modifier = Modifier.width(MikomiSpacing.Tight))
                    QualityTag(quality = quality)
                }
                val mode = line.playbackMode
                if (mode != null) {
                    Spacer(modifier = Modifier.width(MikomiSpacing.Tight))
                    CapabilityTag(mode = mode)
                }
            }
        }
        if (entry.source.isRetryable) {
            RowAction(
                icon = MikomiIcons.Refresh,
                contentDescription = stringResource(
                    R.string.player_source_retry_item,
                    line?.let { entry.source.lineLabel(it) } ?: entry.source.name,
                ),
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
        MikomiIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = RowActionIconSize,
        )
    }
}

/**
 * 源级状态文案：这一行代表的是「这个源现在什么情况」，不是某条线路。
 *
 * 只在源**拿不到线路**时出现（检测中 / 未找到 / 请求失败），
 * 所以「已就绪」返回空串——那种源的每条线路都有自己的行，不需要再多一句。
 * 空串不是文案，是「这里没有话要说」这个事实，调用方据此决定画不画。
 */
@Composable
private fun PlayerSource.statusText(): String = when (availability) {
    SourceAvailability.CHECKING -> stringResource(R.string.player_source_checking)
    SourceAvailability.NO_RESULT -> stringResource(R.string.player_source_no_result)
    SourceAvailability.FAILED -> stringResource(R.string.player_source_failed)
    SourceAvailability.READY -> ""
}

/** 清晰度小标：只在线路上报了清晰度时出现。 */
@Composable
private fun QualityTag(quality: String) {
    Tag(
        text = quality,
        style = TagStyle.Meta,
        colors = TagColors(
            container = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            content = MaterialTheme.colorScheme.primary,
        ),
        shape = RoundedCornerShape(MikomiRadius.Xs),
        textStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
    )
}

/**
 * 出流形态标签：直链 / 网页。
 *
 * 刻意用中性色而不是主色：它不是「好/坏」，只是**控制权归属**。
 * 直链我们能控制进度与倍速；网页交给站点播放器，那条线没有进度条是正常的，不是坏了。
 * 不标出来的话，用户在两条线路之间来回点，只会觉得我们时好时坏。
 */
@Composable
private fun CapabilityTag(mode: PlaybackMode) {
    Tag(
        text = stringResource(
            when (mode) {
                PlaybackMode.NATIVE -> R.string.player_line_capability_direct
                PlaybackMode.WEB -> R.string.player_line_capability_web
            },
        ),
        style = TagStyle.Meta,
        colors = TagColors(
            container = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        shape = RoundedCornerShape(MikomiRadius.Xs),
        textStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
    )
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
            strokeWidth = MikomiStroke.Medium,
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
