package com.liomix.mikomi.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import coil3.compose.AsyncImage
import com.liomix.mikomi.core.model.TeasingComment
import com.liomix.mikomi.core.ui.component.SkeletonBox

/** 吐槽头像直径（对齐 Dart 版 40）。 */
private val TEASING_AVATAR_SIZE = 40.dp

/** 首屏骨架屏条数（对齐 Dart 版 5）。 */
private const val TEASING_SKELETON_COUNT = 5

/**
 * 吐槽 Tab 内容。
 *
 * 吐槽条目直接平铺到详情页外层 [androidx.compose.foundation.lazy.LazyColumn]，
 * 不嵌套独立滚动容器：一是避免懒列表被无限高度约束测量导致崩溃，
 * 二是让列表滚动、分页预取与吸顶 TabBar 共用同一个滚动状态，滑动手感与 Dart 版一致。
 */
internal fun LazyListScope.teasingTab(
    state: TeasingUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        TeasingUiState.Loading -> {
            items(
                count = TEASING_SKELETON_COUNT,
                key = { "teasing_skeleton_$it" },
                contentType = { "teasing_skeleton" },
            ) {
                TeasingSkeletonItem()
            }
        }

        TeasingUiState.Error -> {
            item(key = "teasing_error") {
                TeasingStatusBlock(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    message = stringResource(R.string.detail_teasing_load_failed),
                    actionLabel = stringResource(R.string.detail_retry),
                    onAction = onRetry,
                )
            }
        }

        is TeasingUiState.Loaded -> {
            if (state.comments.isEmpty()) {
                item(key = "teasing_empty") {
                    TeasingStatusBlock(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        message = stringResource(R.string.detail_no_teasing),
                    )
                }
            } else {
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

/** 单条吐槽：头像 + 昵称/时间 + 正文。 */
@Composable
private fun TeasingCommentItem(
    comment: TeasingComment,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        TeasingAvatar(url = comment.user.avatarUrl, name = comment.user.displayName)
        Spacer(modifier = Modifier.width(12.dp))
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
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
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
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = url,
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
                    .padding(vertical = 16.dp),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
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
            .fillMaxWidth()
            .padding(top = 72.dp, bottom = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

/** 骨架屏条目：头像 + 三行文本占位。 */
@Composable
private fun TeasingSkeletonItem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        SkeletonBox(
            modifier = Modifier.size(TEASING_AVATAR_SIZE),
            shape = CircleShape,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(13.dp),
                shape = RoundedCornerShape(7.dp),
            )
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(13.dp),
                shape = RoundedCornerShape(7.dp),
            )
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(13.dp),
                shape = RoundedCornerShape(7.dp),
            )
        }
    }
}
