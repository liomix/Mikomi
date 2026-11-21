package com.liomix.mikomi.feature.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.WindowSpec
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.TextStyles
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.layout.LocalBottomBarSpace
import com.liomix.mikomi.core.ui.window.LocalWindowSize
import com.liomix.mikomi.core.ui.window.rememberBottomInset
import com.liomix.mikomi.core.ui.window.rememberTopInset

/**
 * 社区页（顶级目的地）。
 *
 * 目前只有骨架：标题 + 空态。数据源（Bangumi 私有 API 的条目讨论 / 影评）接入后，
 * 列表替换掉 [CommunityEmptyState]，页面外层结构与底部让位不需要再改。
 *
 * 底部留白 = 系统导航栏 inset + 壳下发的悬浮底栏高度，页面不关心浮着什么。
 */
@Composable
fun CommunityScreen(modifier: Modifier = Modifier) {
    val statusBarInset = rememberTopInset()
    val navigationBarInset = rememberBottomInset()
    val expanded = LocalWindowSize.current.isExpandedWidth

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                // 宽屏收成可读列：标题是左对齐的，在 1200dp 的平板上贴着最左边、
                // 内容却只有一行，读起来像「页面没加载完」。收列之后标题与内容同宽。
                .then(
                    if (expanded) {
                        Modifier.widthIn(max = WindowSpec.ContentMaxWidth)
                    } else {
                        Modifier
                    },
                )
                .fillMaxSize()
                .padding(
                    top = statusBarInset,
                    bottom = navigationBarInset + LocalBottomBarSpace.current,
                ),
        ) {
            Text(
                text = stringResource(R.string.community_title),
                color = MaterialTheme.colorScheme.onSurface,
                // 页面内大标题：字阶取 headlineSmall（24sp），只把字重提到 SemiBold。
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(
                    start = Spacing.Room,
                    end = Spacing.Room,
                    top = Spacing.Gutter,
                ),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CommunityEmptyState()
            }
        }
    }
}

@Composable
private fun CommunityEmptyState(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(horizontal = Spacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon = Icons.Forum,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            size = Sizes.AvatarMd,
        )
        Spacer(modifier = Modifier.height(Spacing.Md))
        Text(
            text = stringResource(R.string.community_empty_title),
            color = colorScheme.onSurface,
            style = TextStyles.BodySecondary.copy(fontWeight = FontWeight.Medium),
        )
        Spacer(modifier = Modifier.height(Spacing.Tight))
        Text(
            text = stringResource(R.string.community_empty_body),
            color = colorScheme.onSurfaceVariant,
            style = TextStyles.Caption,
            textAlign = TextAlign.Center,
        )
    }
}
