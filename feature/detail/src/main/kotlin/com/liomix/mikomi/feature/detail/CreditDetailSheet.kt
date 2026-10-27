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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.liomix.mikomi.core.model.CreditComment
import com.liomix.mikomi.core.model.CreditDetail
import com.liomix.mikomi.core.ui.component.SkeletonBox

/** 头像尺寸（对齐 Dart 版 120x180 竖版）。 */
private val DETAIL_IMAGE_WIDTH = 120.dp
private val DETAIL_IMAGE_HEIGHT = 180.dp

/** 评论头像直径。 */
private val COMMENT_AVATAR_SIZE = 48.dp

/**
 * 主创详情底部弹层：两个 Tab（人物资料 / 吐槽箱）。
 *
 * 对齐 Dart 版 `DetailPopover`：ModalBottomSheet 顶部圆角，
 * 「人物资料」= 左竖版头像 + 名字/本地化名/基本信息/简介；
 * 「吐槽箱」= 评论列表（头像 + 昵称 + 内容 + 相对时间 + 展开回复）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreditDetailSheet(
    detailState: CreditDetailUiState,
    commentsState: CreditCommentsUiState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        var selectedTab by remember { mutableStateOf(0) }
        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.detail_credit_tab_info)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.detail_credit_tab_comments)) },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            ) {
                when (selectedTab) {
                    0 -> CreditDetailInfo(state = detailState)
                    else -> CreditCommentList(state = commentsState)
                }
            }
        }
    }
}

@Composable
private fun CreditDetailInfo(
    state: CreditDetailUiState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CreditDetailUiState.Hidden -> Unit

        CreditDetailUiState.Loading -> CreditDetailSkeleton(modifier = modifier)

        CreditDetailUiState.Error -> CreditDetailError(modifier = modifier)

        is CreditDetailUiState.Loaded -> CreditDetailContent(
            detail = state.detail,
            modifier = modifier,
        )
    }
}

@Composable
private fun CreditDetailContent(
    detail: CreditDetail,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧竖版头像。
            AsyncImage(
                model = detail.imageUrl,
                contentDescription = detail.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(DETAIL_IMAGE_WIDTH)
                    .height(DETAIL_IMAGE_HEIGHT)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(16.dp))
            // 右侧信息。
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                detail.localizedName?.takeIf { it.isNotBlank() }?.let { nameCn ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = nameCn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 基本信息：始终显示标题，内容为空时显示「暂无」。
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.detail_credit_basic_info),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detail.info?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.detail_no_basic_info),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f,
                    ),
                )
            }
        }

        detail.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.detail_credit_summary),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f,
                ),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CreditCommentList(
    state: CreditCommentsUiState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CreditCommentsUiState.Hidden -> Unit

        CreditCommentsUiState.Loading -> CommentsSkeleton(modifier = modifier)

        CreditCommentsUiState.Error -> CreditDetailError(modifier = modifier)

        is CreditCommentsUiState.Loaded -> {
            if (state.comments.isEmpty()) {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.detail_no_comments),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(
                        items = state.comments,
                        key = { "comment_${it.id}" },
                    ) { comment ->
                        CommentItem(comment = comment)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: CreditComment,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasReplies = comment.replies.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            CommentAvatar(url = comment.user.avatarUrl, size = COMMENT_AVATAR_SIZE)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.user.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f,
                    ),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatRelativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (hasReplies) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                onClick = { expanded = !expanded },
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(start = 60.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(
                        modifier = Modifier.width(40.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (expanded) R.string.detail_collapse_replies else R.string.detail_expand_replies,
                            comment.replies.size,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        if (expanded && hasReplies) {
            Spacer(modifier = Modifier.height(16.dp))
            comment.replies.forEach { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 60.dp, bottom = 16.dp),
                ) {
                    CommentAvatar(url = reply.user.avatarUrl, size = 36.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reply.user.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reply.content,
                            style = MaterialTheme.typography.bodySmall.copy(
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f,
                            ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatRelativeTime(reply.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(
    url: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun CreditDetailSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SkeletonBox(
                modifier = Modifier
                    .width(DETAIL_IMAGE_WIDTH)
                    .height(DETAIL_IMAGE_HEIGHT),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(18.dp),
                    shape = RoundedCornerShape(9.dp),
                )
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp),
                    shape = RoundedCornerShape(7.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp),
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
                        .fillMaxWidth()
                        .height(13.dp),
                    shape = RoundedCornerShape(7.dp),
                )
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(13.dp),
                    shape = RoundedCornerShape(7.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CommentsSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        repeat(5) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SkeletonBox(
                    modifier = Modifier.size(COMMENT_AVATAR_SIZE),
                    shape = CircleShape,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(14.dp),
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
                            .fillMaxWidth(0.7f)
                            .height(13.dp),
                        shape = RoundedCornerShape(7.dp),
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(12.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditDetailError(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}