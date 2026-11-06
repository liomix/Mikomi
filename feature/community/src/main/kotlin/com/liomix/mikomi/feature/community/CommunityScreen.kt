package com.liomix.mikomi.feature.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiTextStyles
import com.liomix.mikomi.core.ui.component.base.MikomiIcon
import com.liomix.mikomi.core.ui.component.layout.LocalBottomBarSpace

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
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
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
                start = MikomiSpacing.Room,
                end = MikomiSpacing.Room,
                top = MikomiSpacing.Gutter,
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

@Composable
private fun CommunityEmptyState(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(horizontal = MikomiSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MikomiIcon(
            icon = MikomiIcons.Forum,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            size = MikomiSizes.AvatarMd,
        )
        Spacer(modifier = Modifier.height(MikomiSpacing.Md))
        Text(
            text = stringResource(R.string.community_empty_title),
            color = colorScheme.onSurface,
            style = MikomiTextStyles.BodySecondary.copy(fontWeight = FontWeight.Medium),
        )
        Spacer(modifier = Modifier.height(MikomiSpacing.Tight))
        Text(
            text = stringResource(R.string.community_empty_body),
            color = colorScheme.onSurfaceVariant,
            style = MikomiTextStyles.Caption,
            textAlign = TextAlign.Center,
        )
    }
}
