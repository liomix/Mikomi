package com.liomix.mikomi.feature.search.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.HapticTone
import com.liomix.mikomi.core.ui.base.rememberHaptic
import com.liomix.mikomi.core.ui.base.mikomiClickable
import com.liomix.mikomi.feature.search.R

/** 历史未展开时最多显示的 chip 数。 */
private const val CollapsedChipCount = 8

/** 历史 chip 的尺寸：32dp 高、最短 72dp 宽（太短的词单独占一行会显得碎），最长 180dp。 */
private val ChipMinHeight = 32.dp
private val ChipMinWidth = 72.dp
private val ChipMaxWidth = 180.dp

/** 删除徽标直径。贴在 chip 内部右上角 —— 溢出到外面会被父级（LazyColumn 的 item 边界）裁掉一半。 */
private val DeleteBadgeSize = 16.dp

/** 徽标底色浓度：与 `Tag` 的 `Neutral` 档同一个值（0.14），淡底 + 主色字。 */
private const val BadgeContainerAlpha = 0.14f

/**
 * 搜索历史的关键词流：流式排布，超过阈值折成「展开 / 收起」。
 *
 * 长按**不再直接删除**（那条老路太容易误删）：长按先震动一下作为确认，
 * 该 chip 换成深一点的灰底、右上角浮出一个删除徽标，点徽标才真的删。
 * 点击 chip 本身（搜索）在编辑态下改为「退出编辑」，避免刚长按完手一滑就跳搜索页。
 *
 * 折叠状态由调用方持有：浏览态与结果态的布局不同，组件不该自己记「这次进来是展开还是收起」。
 * 编辑态则完全在这个组件内部 —— 它只影响这一排 chip 的显示，外面不需要知道。
 *
 * @param expanded 是否展开全部关键词。
 * @param onToggleExpand 展开 / 收起。
 * @param onKeywordClick 点击某个关键词（直接搜索）。
 * @param onKeywordDelete 确认删除某个关键词（点删除徽标触发）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchHistoryTags(
    history: List<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onKeywordClick: (String) -> Unit,
    onKeywordDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapsible = history.size > CollapsedChipCount
    val visible = if (expanded || !collapsible) history else history.take(CollapsedChipCount)
    // 正在编辑（长按唤出删除徽标）的那一条；同一时刻只可能有一条。
    var editingKeyword by remember { mutableStateOf<String?>(null) }

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Lg)
            // 展开 / 收起时高度平滑过渡，一屏内容不会突然跳掉半页。
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        visible.forEach { keyword ->
            SearchHistoryChip(
                keyword = keyword,
                editing = editingKeyword == keyword,
                onClick = {
                    // 编辑态下点击只是「收起」，不触发搜索：刚长按完手一滑就跳页很难受。
                    if (editingKeyword == null) onKeywordClick(keyword) else editingKeyword = null
                },
                onLongClick = { editingKeyword = keyword },
                onDelete = {
                    onKeywordDelete(keyword)
                    editingKeyword = null
                },
            )
        }
        if (collapsible) {
            SearchHistoryExpandToggle(expanded = expanded, onClick = onToggleExpand)
        }
    }
}

/**
 * 单个关键词 chip。
 *
 * 常态是浅灰底；编辑态换成更深一档的灰（`surfaceContainerHighest`），
 * 并在右上角浮出一个删除徽标 —— 底色变化是「这条被选中了」的反馈，
 * 徽标是唯一能删的入口。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchHistoryChip(
    keyword: String,
    editing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .heightIn(min = ChipMinHeight)
                .widthIn(min = ChipMinWidth, max = ChipMaxWidth),
            // 4dp：圆角梯度里最方的一档。历史关键词是「一条可以点的文字」，
            // 圆角只负责削掉尖角，不负责把它做成一枚按钮。
            shape = RoundedCornerShape(Radius.Xs),
            // 容器灰底而不是透明 + 描边：这些词是「可以点的内容」，不是边框里的输入物；
            // 填充底色让它们连成一片、更好扫读，也少一圈杂线。
            color = if (editing) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Box(
                modifier = Modifier
                    // 组合点击：单击搜索、长按唤出删除。
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            // 先震一下：长按本来就没有视觉反馈，没有这一下用户不知道
                            // 自己是「按住了」还是「按到了别的」。
                            haptic(HapticTone.LongPress)
                            onLongClick()
                        },
                    )
                    .padding(
                        start = Spacing.Sm,
                        end = if (editing) DeleteBadgeSize + Spacing.Xxs else Spacing.Sm,
                        top = Spacing.Tight,
                        bottom = Spacing.Tight,
                    ),
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

        if (editing) {
            // 与搜索页其它动画图标同一套口径：主色淡底 + 主色叉，
            // 切主题时整页一起换色（红旗标会和蓝色主题打架）。
            val accent = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.Xxs)
                    .size(DeleteBadgeSize)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = BadgeContainerAlpha))
                    .mikomiClickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon = Icons.Close,
                    contentDescription = stringResource(R.string.search_history_delete),
                    tint = accent,
                    size = Sizes.IconXs,
                )
            }
        }
    }
}

/** 展开 / 收起 chip：与关键词 chip 同款底色，只有文字与箭头用主色区分「这是操作」。 */
@Composable
private fun SearchHistoryExpandToggle(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = ChipMinHeight)
            .clip(RoundedCornerShape(Radius.Xs))
            .mikomiClickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.Xs),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.Sm,
                end = Spacing.Tight,
                top = Spacing.Tight,
                bottom = Spacing.Tight,
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
