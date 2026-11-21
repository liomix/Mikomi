package com.liomix.mikomi.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Radius
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.mikomiClickable

/** 首页快捷入口动作。 */
enum class HomeQuickAction {
    SCHEDULE,
    RANK,
    CATEGORY,
}

/** 首页快捷入口的图标：28dp 是「三栏并排」这个布局的物理尺寸，不属于图标档位。 */
private val ActionIconSize = 28.dp

private val HOME_ACTIONS = listOf(
    ActionItem(
        HomeQuickAction.SCHEDULE,
        Icons.Calendar,
        R.string.home_action_schedule,
    ),
    ActionItem(
        HomeQuickAction.RANK,
        Icons.Trophy,
        R.string.home_action_rank,
    ),
    ActionItem(
        HomeQuickAction.CATEGORY,
        Icons.Category,
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
        shape = RoundedCornerShape(Radius.Lg),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(vertical = Spacing.Md),
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
            .clip(RoundedCornerShape(Radius.Md))
            .mikomiClickable(onClick = onClick)
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = ActionIconSize,
        )
        Spacer(modifier = Modifier.height(Spacing.Tight))
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
