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
import androidx.compose.ui.graphics.lerp
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
internal val HomeTopBarHeight = 40.dp

/**
 * 首页顶部栏：左侧头像、右侧搜索框。
 *
 * 自适应：整栏随页面滚动，顶部安全区由 HomeScreen 的 contentPadding 承担；
 * 区域间垂直间距由主页网格的 spacedBy 统一提供，本组件不再自带垂直 padding。
 *
 * ## 材质：两套配色，按 [solid] 过渡
 *
 * 这一栏有两种处境，配色不能只有一套：
 *
 * - **浮在头图上**（`solid = 0`）：白字 + 半透明白底，取 `AppTheme.colors.media`。
 *   底用 `surfaceStrong`（62% 白）—— 14% 压在亮封面上读不出「有一个控件」，
 *   28% 在雪景、天空上仍会发虚；边是 `outline`（14% 白 + [Stroke.Thin]），
 *   玻璃感靠这一圈交代。
 * - **压在页面底色上**（`solid = 1`）：上滑之后顶栏底色就是页面底色，白字白底什么都
 *   看不见。此时换页面配色 —— 底 `surfaceContainerHigh`、字与图标 `onSurfaceVariant`、
 *   边 `outlineVariant`，靠「容器比底色深一档」划出边界。
 *
 * 中间过程按 [solid] 线性插值：两套配色是同一条线的两端，不是到某一帧硬切。
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
    solid: Float = 0f,
) {
    val media = AppTheme.colors.media
    val colors = MaterialTheme.colorScheme
    // 三组颜色都按 [solid] 插值：底色从「半透明白」走到「页面容器色」，
    // 文字从「纯白」走到「页面上次级文字」。中间不会出现某一帧看不清。
    val container = lerp(media.surfaceStrong, colors.surfaceContainerHigh, solid)
    val content = lerp(media.content, colors.onSurfaceVariant, solid)
    val hint = lerp(media.contentSecondary, colors.onSurfaceVariant, solid)
    // 一栏两个控件共用同一份材质：同底、同边、同厚度，看起来才是一个东西。
    val glass = BorderStroke(Stroke.Thin, lerp(media.outline, colors.outlineVariant, solid))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeTopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = container,
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
                    tint = content,
                    // 24 -> 20：圆底由 48 收到 40，图标按同比例缩，否则头像会显得顶满。
                    size = Sizes.IconMd,
                )
            }
        }

        Spacer(modifier = Modifier.width(Spacing.Md))

        Surface(
            shape = CircleShape,
            color = container,
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
                    tint = content,
                    size = Sizes.IconMd,
                )
                Spacer(modifier = Modifier.width(Spacing.Sm))
                Text(
                    text = searchHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = hint,
                    // 有界宽度才能省略：否则长提示词会把图标顶出去。
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
