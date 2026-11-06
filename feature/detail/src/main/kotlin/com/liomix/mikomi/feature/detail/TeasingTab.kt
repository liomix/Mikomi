package com.liomix.mikomi.feature.detail

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.common.util.formatRelativeTime
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiPillShape
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.model.TeasingComment
import com.liomix.mikomi.core.ui.component.base.MikomiIcon
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.component.base.SkeletonShimmer
import com.liomix.mikomi.core.ui.image.MikomiAsyncImage

/** 吐槽头像直径（对齐 Dart 版 40）。 */
private val TEASING_AVATAR_SIZE = 40.dp

/** 空状态插图尺寸：整页只有一个，大到成为视觉主语，不属于图标档位。 */
private val EmptyStateIconSize = 56.dp

/** 首屏骨架屏条数（对齐 Dart 版 5）。 */
private const val TEASING_SKELETON_COUNT = 5

/** 底部翻页骨架条数：两条就够撑住一行可见区域，再多只是白白拉长列表。 */
private const val TEASING_FOOTER_SKELETON_COUNT = 2

/**
 * 吐槽 Tab：自带独立滚动的吐槽列表（对齐 Dart 版 `AnimeTeasingContent`）。
 *
 * 列表状态由外层传入并在 Tab 切换间保留，滚动到尾部自动翻页；
 * 滑动只作用于本列表，不会带动详情页头部。
 */
@Composable
internal fun TeasingTab(
    state: TeasingUiState,
    listState: LazyListState,
    onLoadTeasing: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 首次组合即触发拉取；重复进入由 ViewModel 内部去重。
    LaunchedEffect(Unit) {
        onLoadTeasing()
    }

    when (state) {
        TeasingUiState.Loading -> TeasingSkeletonList(modifier = modifier)

        TeasingUiState.Error -> TeasingStatusBlock(
            icon = MikomiIcons.Comment,
            message = stringResource(R.string.detail_teasing_load_failed),
            actionLabel = stringResource(R.string.detail_retry),
            onAction = onRetry,
            modifier = modifier,
        )

        is TeasingUiState.Loaded -> {
            if (state.comments.isEmpty()) {
                TeasingStatusBlock(
                    icon = MikomiIcons.Comment,
                    message = stringResource(R.string.detail_no_teasing),
                    modifier = modifier,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = CONTENT_BOTTOM_SPACER),
                ) {
                    items(
                        items = state.comments,
                        key = { comment -> "teasing_${comment.id}" },
                        contentType = { "teasing_item" },
                    ) { comment ->
                        TeasingCommentItem(comment = comment)
                    }
                    item(key = "teasing_footer") {
                        TeasingFooter(
                            state = state,
                            onLoadMore = onLoadMore,
                            onRetry = onRetry,
                        )
                    }
                }
            }
        }
    }
}

/** 单条吐槽：头像 + 昵称/时间 + 正文。 */
@Composable
private fun TeasingCommentItem(
    comment: TeasingComment,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MikomiSpacing.Lg, vertical = MikomiSpacing.Gutter),
    ) {
        TeasingAvatar(url = comment.user.avatarUrl, name = comment.user.displayName)
        Spacer(modifier = Modifier.width(MikomiSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
                Text(
                    text = formatRelativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(MikomiSpacing.Tight))
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f,
                ),
            )
        }
    }
}

@Composable
private fun TeasingAvatar(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(TEASING_AVATAR_SIZE),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (url.isNullOrBlank()) {
            MikomiIcon(
                icon = MikomiIcons.Person,
                contentDescription = name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(MikomiSpacing.Sm)
                    .fillMaxSize(),
            )
        } else {
            MikomiAsyncImage(
                url = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    }
}

/** 列表底部：加载更多 / 重试 / 到底提示。 */
@Composable
private fun TeasingFooter(
    state: TeasingUiState.Loaded,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.appendFailed -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MikomiSpacing.Lg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.detail_teasing_load_more_failed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.detail_retry))
                }
            }
        }

        state.hasMore -> {
            // 底部占位进入可视区即预取下一页（列表长度变化才会再次触发）。
            LaunchedEffect(state.comments.size) {
                onLoadMore()
            }
            // 翻页等待期间补两条**同构吐槽骨架**，而不是一个孤立的进度环：
            // 头像 + 三行文本的位置、行高、左右边距都和上面已显示的内容一致，
            // 数据到位后原地被真实条目替换，底部不会出现「转圈 → 突然多两条」的跳变。
            // 整块共用一个 SkeletonShimmer：两条骨架 8 个块，各自建时间线就是 8 条。
            SkeletonShimmer {
                Column {
                    repeat(TEASING_FOOTER_SKELETON_COUNT) {
                        TeasingSkeletonItem()
                    }
                }
            }
        }

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MikomiSpacing.Lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.detail_teasing_no_more),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 空态 / 错误态：居中的图标 + 文案（可选操作按钮）。 */
@Composable
private fun TeasingStatusBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MikomiSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MikomiIcon(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            size = EmptyStateIconSize,
        )
        Spacer(modifier = Modifier.height(MikomiSpacing.Lg))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(MikomiSpacing.Sm))
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

/** 骨架屏列表：头像 + 三行文本占位。 */
@Composable
private fun TeasingSkeletonList(modifier: Modifier = Modifier) {
    // 5 条 × 4 块：合成一条扫光时间线，见 SkeletonShimmer 注释。
    SkeletonShimmer {
        Column(modifier = modifier.fillMaxSize()) {
            repeat(TEASING_SKELETON_COUNT) {
                TeasingSkeletonItem()
            }
        }
    }
}

@Composable
private fun TeasingSkeletonItem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MikomiSpacing.Lg, vertical = MikomiSpacing.Gutter),
    ) {
        SkeletonBox(
            modifier = Modifier.size(TEASING_AVATAR_SIZE),
            shape = CircleShape,
        )
        Spacer(modifier = Modifier.width(MikomiSpacing.Md))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm),
        ) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(13.dp),
                shape = MikomiPillShape,
            )
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(13.dp),
                shape = MikomiPillShape,
            )
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(13.dp),
                shape = MikomiPillShape,
            )
        }
    }
}
