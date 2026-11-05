package com.liomix.mikomi.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing

/**
 * 顶栏高度：与巡礼页搜索条完全一致（`AniMapSearchBar` 的 `SearchBarHeight = 40.dp`）。
 *
 * 头像与搜索框都是这个尺寸，整栏实际高度就是它，不存在「视觉 40、占位 48」的差额留白。
 */
private val HomeTopBarHeight = 40.dp

/**
 * 首页顶部栏：左侧头像、右侧搜索框。
 *
 * 自适应：整栏随页面滚动，顶部安全区由 HomeScreen 的 contentPadding 承担；
 * 区域间垂直间距由主页网格的 spacedBy 统一提供，本组件不再自带垂直 padding。
 *
 * 两处控件都用「无点击版的 [Surface] + [Modifier.clickable]」而不是 `Surface(onClick)`：
 * Material3 的可点击 Surface 会强制 `minimumInteractiveComponentSize`（最小 48dp），
 * 把高度顶回 48，改 `.size()` 也没用。这里要的是与巡礼页一致的 40dp 触控区，
 * 所以点击放到修饰符上（`clip` 在前，涟漪仍按圆形裁剪），语义用 `Role.Button` 补回。
 */
@Composable
fun HomeTopBar(
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    searchHint: String,
    profileContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 用 heightIn(min) 而不是 height：正常字号下就是 40dp，
            // 超大字号时让它自然撑高，不把里面的字裁掉。
            .heightIn(min = HomeTopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(HomeTopBarHeight)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onProfileClick),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = profileContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 24 -> 20：圆底由 48 收到 40，图标按同比例缩，否则头像会显得顶满。
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(MikomiSpacing.Md))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = HomeTopBarHeight)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSearchClick),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    // 14 / 18 / 8 全部对齐巡礼页搜索条：同样的内缩、同样的图标尺寸，
                    // 两处搜索框并排看是同一个东西。
                    .padding(horizontal = MikomiSpacing.Gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(MikomiSizes.IconSm),
                )
                Spacer(modifier = Modifier.width(MikomiSpacing.Sm))
                Text(
                    text = searchHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 有界宽度才能省略：否则长提示词会把图标顶出去。
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
