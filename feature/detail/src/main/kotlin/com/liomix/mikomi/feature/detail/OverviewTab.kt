package com.liomix.mikomi.feature.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.Anime

/** 简介最多展示行数。 */
private const val MAX_SYNOPSIS_LINES = 4

/** 标签最多展示数量（超出折叠为「更多」）。 */
private const val MAX_VISIBLE_TAGS = 9

/**
 * 概述 Tab：简介（超长可展开）+ 标签（超量可展开）。
 *
 * 对齐 Dart 版 `OverviewTabContent`：简介默认 4 行，超出显示「展开」；
 * 标签默认展示前 9 个，超出显示「更多」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewTab(
    anime: Anime,
    modifier: Modifier = Modifier,
) {
    var synopsisExpanded by remember { mutableStateOf(false) }
    var tagsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 简介。
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.detail_synopsis),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = anime.synopsis?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.detail_no_synopsis),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.6f,
                ),
                maxLines = if (synopsisExpanded) Int.MAX_VALUE else MAX_SYNOPSIS_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (anime.synopsis.orEmpty().length > MAX_SYNOPSIS_LINES) {
                ExpandToggle(
                    expanded = synopsisExpanded,
                    onClick = { synopsisExpanded = !synopsisExpanded },
                )
            }
        }

        // 标签。
        if (anime.genres.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.detail_tags),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val visibleTags = if (tagsExpanded) {
                    anime.genres
                } else {
                    anime.genres.take(MAX_VISIBLE_TAGS)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visibleTags.forEach { tag ->
                        TagChip(tag = tag)
                    }
                    if (anime.genres.size > MAX_VISIBLE_TAGS) {
                        ExpandChip(
                            expanded = tagsExpanded,
                            onClick = { tagsExpanded = !tagsExpanded },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandToggle(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = stringResource(
            if (expanded) R.string.detail_collapse else R.string.detail_expand_more,
        ),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun TagChip(
    tag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ExpandChip(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.detail_collapse else R.string.detail_expand_more,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Outlined.Remove else Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
