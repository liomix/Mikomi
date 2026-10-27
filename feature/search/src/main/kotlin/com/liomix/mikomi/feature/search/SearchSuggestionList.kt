package com.liomix.mikomi.feature.search

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.SkeletonBox

/** 联想骨架屏条数。 */
private const val SUGGESTION_SKELETON_COUNT = 5

/**
 * 实时联想列表。
 *
 * 点击某条即以其标题发起搜索；匹配到的关键词用主色高亮，方便确认输入。
 */
@Composable
internal fun SearchSuggestionList(
    keyword: String,
    suggestions: List<Anime>,
    state: LoadState,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        // 输入中（防抖等待 / 请求中）保持骨架屏，避免闪出「无匹配」。
        LoadState.Idle,
        LoadState.Loading,
        -> SuggestionSkeleton(modifier = modifier)

        // 联想是即时反馈：失败后继续输入即会重新请求，无需单独重试入口。
        LoadState.Error -> SearchCenteredHint(
            title = stringResource(R.string.search_load_failed),
            subtitle = stringResource(R.string.search_suggestion_retry_hint),
            modifier = modifier,
        )

        LoadState.Success -> {
            if (suggestions.isEmpty()) {
                SearchCenteredHint(
                    title = stringResource(R.string.search_no_suggestion),
                    modifier = modifier,
                )
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(
                        items = suggestions,
                        key = { anime -> "suggestion_${anime.id}" },
                        contentType = { "suggestion_item" },
                    ) { anime ->
                        SuggestionItem(
                            title = anime.title,
                            keyword = keyword,
                            onClick = { onSuggestionClick(anime.title) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    title: String,
    keyword: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlightStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    val text = remember(keyword, title) {
        highlight(title = title, keyword = keyword, style = highlightStyle)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Outlined.NorthWest,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 把 [keyword] 在 [title] 中的所有命中片段标记为 [style]。 */
private fun highlight(
    title: String,
    keyword: String,
    style: SpanStyle,
): AnnotatedString {
    if (keyword.isBlank()) return AnnotatedString(title)
    return buildAnnotatedString {
        append(title)
        var start = title.indexOf(keyword, ignoreCase = true)
        while (start >= 0) {
            addStyle(style, start, start + keyword.length)
            start = title.indexOf(keyword, start + keyword.length, ignoreCase = true)
        }
    }
}

@Composable
private fun SuggestionSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
    ) {
        repeat(SUGGESTION_SKELETON_COUNT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(
                    modifier = Modifier.size(20.dp),
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp),
                    shape = RoundedCornerShape(7.dp),
                )
            }
        }
    }
}
