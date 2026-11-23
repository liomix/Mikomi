package com.liomix.mikomi.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.AppTheme
import com.liomix.mikomi.core.designsystem.Icons
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.Stroke
import com.liomix.mikomi.core.ui.base.Icon
import com.liomix.mikomi.core.ui.base.mikomiClickable

/**
 * 顶栏高度：与巡礼页搜索条完全一致（`AniMapSearchBar` 的 `SearchBarHeight = 40.dp`）。
 *
 * 头像与搜索框都是这个尺寸，整栏实际高度就是它，不存在「视觉 40、占位 48」的差额留白。
 *
 * **必须是固定高度，不能只给 `heightIn(min)`。** 这一栏现在被放进头图那个
 * `Box(height = 头图高)` 里，而搜索框内部是 `Row(fillMaxSize())` —— `fillMaxSize`
 * 会去填父给的**最大**高度，父一开始是 LazyGrid 的网格项（高度无界，fill 不生效，
 * 看起来正常），进了有界盒子之后就直接填满 180dp，搜索框被拉成一条竖长的胶囊。
 * 三处（整栏、头像、搜索框）因此全部钉死在这个常量上。
 */
private val HomeTopBarHeight = 40.dp

/**
 * 首页顶部栏：左侧头像、右侧搜索框。
 *
 * 自适应：整栏随页面滚动，顶部安全区由 HomeScreen 的 contentPadding 承担；
 * 区域间垂直间距由主页网格的 spacedBy 统一提供，本组件不再自带垂直 padding。
 *
 * ## 材质：压在图上，所以用 media 一档
 *
 * 这一栏现在浮在沉浸式头图之上，不再是「页面上的一行控件」。因此底色不能取
 * `surfaceVariant` —— 那是页面底色的一档，压到封面上会变成一块实心补丁，遮掉画面。
 * 改取 `AppTheme.colors.media`：
 *
 * - 底 `surfaceProminent`（28% 白）：比常态的 `surface`（14%）更实一档。14% 压在
 *   亮封面上几乎读不出「有一个控件」，图标也跟着发虚；
 * - 边 `outline`（14% 白 + [Stroke.Thin]）：比底色淡一圈，玻璃感靠它交代边界；
 * - 内容 `content`（纯白）/ `contentSecondary`：图上专用的文字色，本身不透明。
 *
 * 两处控件都用「无点击版的 [Surface] + [mikomiClickable]」而不是 `Surface(onClick)`：
 * Material3 的可点击 Surface 会强制 `minimumInteractiveComponentSize`（最小 48dp），
 * 把高度顶回 48，改 `.size()` 也没用。这里要的是与巡礼页一致的 40dp 触控区，
 * 所以点击放到修饰符上（`clip` 在前，按压区仍按圆形裁剪），语义由
 * [mikomiClickable] 的 `role = Button` 补回。
 */
@Composable
fun HomeTopBar(
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    searchHint: String,
    profileContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val media = AppTheme.colors.media
    // 一栏两个控件共用同一份材质：同底、同边、同厚度，看起来才是一个东西。
    val glass = BorderStroke(Stroke.Thin, media.outline)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeTopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = media.surfaceProminent,
            border = glass,
            modifier = Modifier
                .size(HomeTopBarHeight)
                .clip(CircleShape)
                .mikomiClickable(onClick = onProfileClick),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon = Icons.Person,
                    contentDescription = profileContentDescription,
                    tint = media.content,
                    // 24 -> 20：圆底由 48 收到 40，图标按同比例缩，否则头像会显得顶满。
                    size = Sizes.IconMd,
                )
            }
        }

        Spacer(modifier = Modifier.width(Spacing.Md))

        Surface(
            shape = CircleShape,
            color = media.surfaceProminent,
            border = glass,
            modifier = Modifier
                .weight(1f)
                .height(HomeTopBarHeight)
                .clip(CircleShape)
                .mikomiClickable(onClick = onSearchClick),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    // 14 / 8 对齐巡礼页搜索条：同样的内缩、同样的图标尺寸，
                    // 两处搜索框并排看是同一个东西。
                    .padding(horizontal = Spacing.Gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon = Icons.Search,
                    contentDescription = null,
                    tint = media.content,
                    size = Sizes.IconMd,
                )
                Spacer(modifier = Modifier.width(Spacing.Sm))
                Text(
                    text = searchHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = media.contentSecondary,
                    // 有界宽度才能省略：否则长提示词会把图标顶出去。
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
