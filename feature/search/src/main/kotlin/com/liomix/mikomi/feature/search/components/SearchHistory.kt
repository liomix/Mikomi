package com.liomix.mikomi.feature.search.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.liomix.mikomi.core.ui.base.mikomiClickable
import com.liomix.mikomi.feature.search.R

/** 历史未展开时最多显示的 chip 数。 */
private const val CollapsedChipCount = 8

/** 历史 chip 高度与单条最大宽度。32dp 是「一行小字 + 上下各 6dp」的结果。 */
private val ChipMinHeight = 32.dp
private val ChipMaxWidth = 180.dp

/**
 * 搜索历史的关键词流：流式排布，超过阈值折成「展开 / 收起」。
 *
 * 点击直接搜索、长按删除单条 —— 两个动作都绑在 chip 自己身上，不额外加删除按钮：
 * 一行里出现十几个小叉号，比误删更难用。
 *
 * 折叠状态由调用方持有：浏览态与结果态的布局不同，组件不该自己记「这次进来是展开还是收起」。
 *
 * @param expanded 是否展开全部关键词。
 * @param onToggleExpand 展开 / 收起。
 * @param onKeywordClick 点击某个关键词（直接搜索）。
 * @param onKeywordLongClick 长按某个关键词（删除）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchHistoryTags(
    history: List<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onKeywordClick: (String) -> Unit,
    onKeywordLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapsible = history.size > CollapsedChipCount
    val visible = if (expanded || !collapsible) history else history.take(CollapsedChipCount)

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
                onClick = { onKeywordClick(keyword) },
                onLongClick = { onKeywordLongClick(keyword) },
            )
        }
        if (collapsible) {
            SearchHistoryExpandToggle(expanded = expanded, onClick = onToggleExpand)
        }
    }
}

/** 单个关键词 chip：细描边 + 透明底，长词截断，保证一屏内可扫读。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchHistoryChip(
    keyword: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = ChipMinHeight)
            .widthIn(max = ChipMaxWidth),
        // 4dp：圆角梯度里最方的一档。历史关键词是「一条可以点的文字」，
        // 圆角只负责削掉尖角，不负责把它做成一枚按钮。
        shape = RoundedCornerShape(Radius.Xs),
        // 容器灰底而不是透明 + 描边：这些词是「可以点的内容」，不是边框里的输入物；
        // 填充底色让它们连成一片、更好扫读，也少一圈杂线。
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(
            modifier = Modifier
                // 组合点击：单击搜索、长按删除。用 `combinedClickable` 而不是自己拼两个手势，
                // 长按的触发时长与系统一致。
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = Spacing.Sm, vertical = Spacing.Tight),
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

/** 展开 / 收起 chip：与关键词 chip 同款描边，只有文字与箭头用主色区分「这是操作」。 */
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
