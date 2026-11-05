package com.liomix.mikomi.feature.player.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.ui.component.base.Tab
import com.liomix.mikomi.core.ui.component.base.TabBar
import com.liomix.mikomi.core.ui.component.base.TabItemStyle
import com.liomix.mikomi.feature.player.PlayerTab
import com.liomix.mikomi.feature.player.R

/**
 * 导航条高度。
 *
 * 这一条不是纯 Tab 栏——左边是 Tab、右边还挂着三个圆钮，视觉上比系统导航条更「厚」。
 * 56 → 52 → **48dp**（与 Dart-Mikomi 视频页的 `height: 48` 一致）：
 * 里面的 Tab 与圆钮一起缩一档，内外留白才匀得开。
 */
private val PlayerBarHeight = 48.dp

/**
 * 圆形操作按钮直径：**触控区就是这个圆本身**，不再向外扩隐形热区。
 *
 * 视觉与热区一致，点击必须落在圆内，相邻两个按钮之间才不会有互相抢事件的空白带。
 * 36dp 已略高于 48dp 最小触控目标的折算值（配合 8dp 间距），在更薄的导航条里也不难按。
 */
private val ActionSize = 36.dp

/**
 * 操作图标尺寸：来自 Dart-Mikomi 的图标铺满 24 网格且细节很密（气泡里的「弹」字 +
 * 角标），20dp 时角标刚好看得清，与 36dp 的圆也是同一比例。
 */
private val ActionIconSize = 20.dp

/**
 * Tab 栏 + 右侧操作区。
 *
 * 左侧复用 `core:ui` 的 [TabBar]（指示器按实测矩形滑动，与个人页/排行榜同一套动效），
 * 右侧是纯操作区：弹幕开关、弹幕设置、当前播放源。三者都是「与当前集无关的全局动作」，
 * 换集、换排序都不会重建。
 *
 * 弹幕三个图标直接取自 Dart-Mikomi 的 `assets/icons/danmaku_{on,off,setting}.svg`，
 * 已转成 `res/drawable/ic_danmaku_*.xml`，颜色交给 `Icon` 的 tint，日/夜自动跟随主题。
 */
@Composable
internal fun PlayerNavigationBar(
    selectedTab: PlayerTab,
    danmakuEnabled: Boolean,
    sourceLabel: String,
    onTabSelected: (PlayerTab) -> Unit,
    onDanmakuToggle: () -> Unit,
    onDanmakuSettingsClick: () -> Unit,
    onSourceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerBarHeight)
            .padding(horizontal = MikomiSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabBar(
            selectedIndex = selectedTab.ordinal,
            onSelect = { index -> onTabSelected(PlayerTab.entries[index]) },
            containerColor = Color.Transparent,
            contentPadding = PaddingValues(0.dp),
            itemSpacing = 2.dp,
            itemStyle = TabItemStyle(
                height = 32.dp,
                horizontalPadding = 10.dp,
                labelStyle = MaterialTheme.typography.labelLarge,
            ),
        ) {
            Tab(index = PlayerTab.EPISODES.ordinal, text = stringResource(R.string.player_tab_episodes))
            Tab(index = PlayerTab.COMMENTS.ordinal, text = stringResource(R.string.player_tab_comments))
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            // 触控区收紧到圆本身后，间距必须补回来：之前靠 48dp 热区外扩，
            // 视觉间距是「2 + 4+4」；现在热区=视觉，直接给 8dp。
            horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleAction(
                icon = painterResource(
                    if (danmakuEnabled) R.drawable.ic_danmaku_on else R.drawable.ic_danmaku_off,
                ),
                contentDescription = stringResource(R.string.player_danmaku),
                active = danmakuEnabled,
                onClick = onDanmakuToggle,
            )
            CircleAction(
                icon = painterResource(R.drawable.ic_danmaku_setting),
                contentDescription = stringResource(R.string.player_danmaku_settings),
                onClick = onDanmakuSettingsClick,
            )
            if (sourceLabel.isNotBlank()) {
                SourceChip(
                    label = sourceLabel,
                    onClick = onSourceClick,
                )
            }
        }
    }
}

/** 圆形图标的两个状态：关闭态用中性底色，开启态用主色浅底 + 主色图标。 */
@Composable
private fun CircleAction(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    iconSize: Dp = ActionIconSize,
) {
    val background by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = MikomiMotion.Duration.Fast),
        label = "playerActionBackground",
    )
    val tint by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = MikomiMotion.Duration.Fast),
        label = "playerActionTint",
    )

    Box(
        modifier = Modifier
            .size(ActionSize)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * 当前播放源胶囊：首字母头像 + 源名。
 *
 * 点击进入源选择器（P2 的 `PlaybackSourceSheet`），这里只做入口，不承载检测状态。
 */
@Composable
private fun SourceChip(
    label: String,
    onClick: () -> Unit,
) {
    val sourceDescription = stringResource(R.string.player_source)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // 源名可能很长：给一个上限，窄屏时宁可省略也不把 Tab 挤出去。
        modifier = Modifier
            .height(32.dp)
            .widthIn(max = 92.dp)
            .semantics { contentDescription = "$sourceDescription $label" },
    ) {
        Row(
            modifier = Modifier.padding(start = MikomiSpacing.Xs, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
