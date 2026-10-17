package com.liomix.mikomi.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** 首页快捷入口动作。 */
enum class HomeQuickAction {
    SCHEDULE,
    RANK,
    CATEGORY,
}

private val HOME_ACTIONS = listOf(
    ActionItem(
        HomeQuickAction.SCHEDULE,
        Icons.Outlined.CalendarMonth,
        R.string.home_action_schedule,
    ),
    ActionItem(
        HomeQuickAction.RANK,
        Icons.Outlined.EmojiEvents,
        R.string.home_action_rank,
    ),
    ActionItem(
        HomeQuickAction.CATEGORY,
        Icons.Outlined.Category,
        R.string.home_action_category,
    ),
)

/**
 * 首页快捷入口：时间表 / 排行榜 / 分类，三列均分。
 *
 * 每个入口触控目标 ≥48dp；目的地由 [onActionClick] 交给上层路由处理（当前为占位）。
 */
@Composable
fun HomeActionTabs(
    onActionClick: (HomeQuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            HOME_ACTIONS.forEach { item ->
                ActionButton(
                    icon = item.icon,
                    label = stringResource(item.labelRes),
                    onClick = { onActionClick(item.action) },
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class ActionItem(
    val action: HomeQuickAction,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
)
