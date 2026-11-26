package com.liomix.mikomi.feature.detail.component

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.common.util.formatRelativeTime
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.PillShape
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.anime.CreatorComment
import com.liomix.mikomi.core.model.anime.CreatorDetail
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.SkeletonBox
import com.liomix.mikomi.core.ui.image.AsyncImage
import com.liomix.mikomi.feature.detail.CreatorCommentsUiState
import com.liomix.mikomi.feature.detail.CreatorDetailUiState
import com.liomix.mikomi.feature.detail.R
import com.liomix.mikomi.core.ui.base.mikomiClickable

/** 头像尺寸（对齐 Dart 版 120x180 竖版）。 */
private val DETAIL_IMAGE_WIDTH = 120.dp
private val DETAIL_IMAGE_HEIGHT = 180.dp

/** 评论头像直径。 */
private val COMMENT_AVATAR_SIZE = 48.dp

/**
 * 回复区缩进 = 一级评论的头像 + 头像与正文的间隙。
 *
 * 写成加法而不是 60dp：这两个值任何一个变了（头像调大、间隙改成 Sm），
 * 回复的首字都会自动跟着正文首字走；写死 60 的话它会在某次调参后错开几像素，
 * 而这种错位没人会专门去发现。
 */
private val ReplyIndent = COMMENT_AVATAR_SIZE + Spacing.Md

/**
 * 主创详情底部弹层：两个 Tab（人物资料 / 吐槽箱）。
 *
 * 对齐 Dart 版 `DetailPopover`：ModalBottomSheet 顶部圆角，
 * 「人物资料」= 左竖版头像 + 名字/本地化名/基本信息/简介；
 * 「吐槽箱」= 评论列表（头像 + 昵称 + 内容 + 相对时间 + 展开回复）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreatorDetailSheet(
    detailState: CreatorDetailUiState,
    commentsState: CreatorCommentsUiState,
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
                    0 -> CreatorDetailInfo(state = detailState)
                    else -> CreatorCommentList(state = commentsState)
                }
            }
        }
    }
}

@Composable
private fun CreatorDetailInfo(
    state: CreatorDetailUiState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CreatorDetailUiState.Hidden -> Unit

        CreatorDetailUiState.Loading -> CreatorDetailSkeleton(modifier = modifier)

        CreatorDetailUiState.Error -> CreatorDetailError(modifier = modifier)

        is CreatorDetailUiState.Loaded -> CreatorDetailContent(
            detail = state.detail,
            modifier = modifier,
        )
    }
}

@Composable
private fun CreatorDetailContent(
    detail: CreatorDetail,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Lg),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧竖版头像。
            AsyncImage(
                url = detail.imageUrl,
                contentDescription = detail.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(DETAIL_IMAGE_WIDTH)
                    .height(DETAIL_IMAGE_HEIGHT)
                    .clip(RoundedCornerShape(Radius.Md)),
            )
            Spacer(modifier = Modifier.width(Spacing.Lg))
            // 右侧信息。
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                detail.localizedName?.takeIf { it.isNotBlank() }?.let { nameCn ->
                    Spacer(modifier = Modifier.height(Spacing.Xs))
                    Text(
                        text = nameCn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 基本信息：始终显示标题，内容为空时显示「暂无」。
                Spacer(modifier = Modifier.height(Spacing.Lg))
                Text(
                    text = stringResource(R.string.detail_credit_basic_info),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(Spacing.Sm))
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
            Spacer(modifier = Modifier.height(Spacing.Lg))
            Text(
                text = stringResource(R.string.detail_credit_summary),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(Spacing.Sm))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f,
                ),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.Xl))
    }
}

@Composable
private fun CreatorCommentList(
    state: CreatorCommentsUiState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CreatorCommentsUiState.Hidden -> Unit

        CreatorCommentsUiState.Loading -> CommentsSkeleton(modifier = modifier)

        CreatorCommentsUiState.Error -> CreatorDetailError(modifier = modifier)

        is CreatorCommentsUiState.Loaded -> {
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
                    contentPadding = PaddingValues(Spacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Xl),
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
    comment: CreatorComment,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasReplies = comment.replies.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            CommentAvatar(url = comment.user.avatarUrl, size = COMMENT_AVATAR_SIZE)
            Spacer(modifier = Modifier.width(Spacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.user.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(Spacing.Tight))
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f,
                    ),
                )
                Spacer(modifier = Modifier.height(Spacing.Sm))
                Text(
                    text = formatRelativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (hasReplies) {
            Spacer(modifier = Modifier.height(Spacing.Md))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .padding(start = ReplyIndent)
                    .mikomiClickable(onClick = { expanded = !expanded }),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(
                        modifier = Modifier.width(40.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(modifier = Modifier.width(Spacing.Sm))
                    Text(
                        text = stringResource(
                            if (expanded) R.string.detail_collapse_replies else R.string.detail_expand_replies,
                            comment.replies.size,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(Spacing.Xs))
                    Icon(
                        icon = if (expanded) Icons.ExpandLess else Icons.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = Sizes.IconXs,
                    )
                }
            }
        }

        if (expanded && hasReplies) {
            Spacer(modifier = Modifier.height(Spacing.Lg))
            comment.replies.forEach { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ReplyIndent, bottom = Spacing.Lg),
                ) {
                    CommentAvatar(url = reply.user.avatarUrl, size = 36.dp)
                    Spacer(modifier = Modifier.width(Spacing.Md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reply.user.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(Spacing.Xs))
                        Text(
                            text = reply.content,
                            style = MaterialTheme.typography.bodySmall.copy(
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f,
                            ),
                        )
                        Spacer(modifier = Modifier.height(Spacing.Tight))
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
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                icon = Icons.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(Spacing.Sm)
                    .fillMaxSize(),
            )
        } else {
            AsyncImage(
                url = url,
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
private fun CreatorDetailSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.Lg),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SkeletonBox(
                modifier = Modifier
                    .width(DETAIL_IMAGE_WIDTH)
                    .height(DETAIL_IMAGE_HEIGHT),
                shape = RoundedCornerShape(Radius.Md),
            )
            Spacer(modifier = Modifier.width(Spacing.Lg))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(18.dp),
                    shape = PillShape,
                )
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp),
                    shape = PillShape,
                )
                Spacer(modifier = Modifier.height(Spacing.Sm))
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp),
                    shape = PillShape,
                )
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(13.dp),
                    shape = PillShape,
                )
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(13.dp),
                    shape = PillShape,
                )
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(13.dp),
                    shape = PillShape,
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.Xl))
    }
}

@Composable
private fun CommentsSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.Lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.Xl),
    ) {
        repeat(5) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SkeletonBox(
                    modifier = Modifier.size(COMMENT_AVATAR_SIZE),
                    shape = CircleShape,
                )
                Spacer(modifier = Modifier.width(Spacing.Md))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(14.dp),
                        shape = PillShape,
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(13.dp),
                        shape = PillShape,
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(13.dp),
                        shape = PillShape,
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(12.dp),
                        shape = RoundedCornerShape(Radius.Sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatorDetailError(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Xxxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
